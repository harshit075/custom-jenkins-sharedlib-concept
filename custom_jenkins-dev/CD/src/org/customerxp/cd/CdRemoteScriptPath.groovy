package org.customerxp.cd

/**
 * Resolves the remote directory on an app server for CD scripts ({@code cc_setup.sh}, {@code module_deploy.sh},
 * {@code module_json.tar}, {@code .config}). No fallback chain — missing configuration fails immediately.
 * <p>Precedence:
 * <ol>
 *   <li>{@code servers[].remoteScriptPath} — explicit per server</li>
 *   <li>{@code JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE} — required when (1) is unset; e.g. {@code /home/${user}/scripts}</li>
 * </ol>
 */
@SuppressWarnings(['unused'])
final class CdRemoteScriptPath {

    private static final String PATH_PATTERN = /^[a-zA-Z0-9_.\\/+-]+$/

    /**
     * @param serverEntry map from {@code servers.json} ({@code user} required for template substitution)
     * @param envLike Jenkins workflow {@code env} ({@code EnvActionImpl}), a plain {@code Map}, or {@code null}
     * @return {@code [path: String, source: String]} for logging and deploy
     */
    static Map<String, String> resolve(Map serverEntry, def envLike) {
        def entry = serverEntry ?: [:]
        def user = trim(entry.user)
        def serverName = trim(entry.name)

        def explicit = trim(entry.remoteScriptPath)
        if (explicit) {
            return result(explicit, 'servers.json remoteScriptPath')
        }

        def template = readEnv(envLike, 'JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE')
        if (!template) {
            throw new IllegalArgumentException(
                'JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE is not set. ' +
                    'Set it in Manage Jenkins global env, e.g. /home/${user}/scripts'
            )
        }
        if (!user) {
            throw new IllegalArgumentException(
                "CD remote script path: JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE is set but server '${serverName ?: 'unknown'}' has no user in servers.json."
            )
        }

        def applied = applyTemplate(template, user)
        if (!applied) {
            throw new IllegalArgumentException(
                "CD remote script path: could not apply JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE for server '${serverName ?: user}'."
            )
        }
        return result(applied, 'JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE')
    }

    /** Replaces {@code ${user}} in {@code JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE}. */
    static String applyTemplate(String pattern, String user) {
        def p = pattern?.toString() ?: ''
        if (!p.trim() || !user?.trim()) {
            return ''
        }
        return p.trim().replace('${user}', user.trim())
    }

    static String normalizePath(String path) {
        def p = path?.toString()?.trim() ?: ''
        if (!p) {
            throw new IllegalArgumentException('CD remote script path is empty.')
        }
        if (!(p ==~ PATH_PATTERN)) {
            throw new IllegalArgumentException(
                "CD remote script path contains unsupported characters: '${p}'. " +
                    'Use letters, digits, underscore, dot, slash, hyphen only.'
            )
        }
        return p.replaceAll(/\/+$/, '')
    }

    private static Map<String, String> result(String path, String source) {
        [path: normalizePath(path), source: source?.toString() ?: 'unknown']
    }

    /**
     * Read one env key from a {@code Map} or Jenkins workflow {@code env} without requiring {@code Map} at the call site
     * (CPS: {@code EnvActionImpl} is not a {@code java.util.Map}).
     */
    private static String readEnv(def envLike, String key) {
        if (envLike == null || !key?.trim()) {
            return ''
        }
        if (envLike instanceof Map) {
            def m = envLike as Map
            def v = m[key] ?: m.get(key)
            if (v != null) {
                return trim(v)
            }
        }
        try {
            return trim(envLike."${key}")
        } catch (Throwable ignored) {
            return ''
        }
    }

    private static String trim(def v) {
        v?.toString()?.trim() ?: ''
    }
}
