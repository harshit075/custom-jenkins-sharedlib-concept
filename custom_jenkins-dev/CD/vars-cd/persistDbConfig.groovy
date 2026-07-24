// vars/persistDbConfig.groovy
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.customerxp.cd.CdJenkinsSecretTextCredentials

/**
 * P4-3: When the user chooses NEW DB credentials, store the password in Jenkins **Secret text**
 * credentials only, then update {@code JENKINS_CD_DB_CONFIG_FILE} with {@code credential_id} + user —
 * **no** plaintext passwords on disk.
 *
 * <p>P4-2: Still gated by {@code JENKINS_CD_PERSIST_DB_CONFIG} (opt-out) and
 * {@code JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX}. Credential id pattern:
 * {@code JENKINS_CD_DB_CREDENTIAL_ID_PREFIX} + sanitized setup name (required).
 *
 * @return credential id string written to JSON, or {@code null} if nothing done
 */
def call(Map args) {
    String path = args.path?.toString()?.trim()
    String setupName = args.setupName?.toString()?.trim()
    String user = args.user?.toString()?.trim()
    String password = args.password?.toString() ?: ''

    if (!path || !setupName || !user || !password?.trim()) {
        return null
    }

    if (isPersistDbConfigDisabledByEnv()) {
        return null
    }

    if (!isDbConfigPathAllowed(path)) {
        echo "persistDbConfig (P4-2): rejected write — path not under JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX (check controller layout)."
        return null
    }

    def prefix = env.JENKINS_CD_DB_CREDENTIAL_ID_PREFIX?.toString()?.trim()
    if (!prefix) {
        error 'persistDbConfig: set JENKINS_CD_DB_CREDENTIAL_ID_PREFIX.'
    }
    def credentialId = prefix + CdJenkinsSecretTextCredentials.sanitizeCredentialIdFragment(setupName)

    try {
        CdJenkinsSecretTextCredentials.upsertSecretText(
            credentialId,
            password,
            "CD DB setup '${setupName}' (managed by persistDbConfig P4-3)")
    } catch (Exception e) {
        echo "persistDbConfig (P4-3): failed to store Jenkins Secret text credential '${credentialId}': ${e.message}"
        echo 'Ensure the Jenkins user may manage credentials, Plain Credentials plugin is installed, and script approvals allow Credentials APIs.'
        return null
    }

    def f = new java.io.File(path)
    if (!f.exists()) {
        echo 'persistDbConfig: configured DB config file not found on controller, skip JSON update.'
        return null
    }

    def raw
    try {
        raw = new JsonSlurper().parseText(f.text)
    } catch (Exception e) {
        echo "persistDbConfig: invalid JSON in DB config file: ${e.message}"
        return null
    }

    def changed = false
    if (raw instanceof Map && raw.databases instanceof List) {
        raw.databases.each { item ->
            if (item?.name?.toString()?.trim() == setupName) {
                item.user = user
                item.credential_id = credentialId
                item.credentialId = credentialId
                scrubDbSecretsFromEntry(item)
                changed = true
            }
        }
    } else if (raw instanceof Map) {
        raw.each { key, value ->
            if (key.toString().startsWith('_')) return
            if (key.toString().trim() == setupName && value instanceof Map) {
                value.DB_USER = user
                value.user = user
                value.credential_id = credentialId
                value.credentialId = credentialId
                scrubDbSecretsFromEntry(value)
                changed = true
            }
        }
    }

    if (changed) {
        f.text = JsonOutput.prettyPrint(JsonOutput.toJson(raw))
        echo "persistDbConfig (P4-3): linked setup '${setupName}' to Jenkins credential id '${credentialId}' (no DB password in JSON)."
        return credentialId
    }
    return null
}

private static void scrubDbSecretsFromEntry(def entry) {
    if (!(entry instanceof Map)) return
    entry.remove('password')
    entry.remove('DB_PASSWORD')
    entry.remove('pwd')
}

/** Opt-out: set JENKINS_CD_PERSIST_DB_CONFIG to false / 0 / no / off to disable all writes. Unset = enabled (legacy). */
private boolean isPersistDbConfigDisabledByEnv() {
    def v = env.JENKINS_CD_PERSIST_DB_CONFIG?.toString()?.trim()?.toLowerCase()
    return v in ['false', '0', 'no', 'off']
}

/**
 * If {@code JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX} is set (e.g. {@code /var/lib/jenkins}), only paths under that
 * prefix may be modified. Unset = no prefix check (legacy).
 */
private boolean isDbConfigPathAllowed(String filePath) {
    def prefixRaw = env.JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX?.toString()?.trim()
    if (!prefixRaw) {
        return true
    }
    def norm = { String s ->
        if (!s) return ''
        s.replace('\\', '/').replaceAll(/\/+$/, '')
    }
    def p = norm(filePath)
    def px = norm(prefixRaw)
    if (!px) return true
    return p == px || p.startsWith(px + '/')
}
