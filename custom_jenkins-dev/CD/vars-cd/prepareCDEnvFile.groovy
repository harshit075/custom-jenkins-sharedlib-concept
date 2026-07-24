// vars/prepareCDEnvFile.groovy

/**
 * Prepares the automation_variables.env file for CD deployment.
 * Writes env vars to a file in workspace so they can be SCP'd to the app server.
 * Avoids echoing secrets in shell by using Groovy/writeFile. P4-5: logs only the relative file name and count, not secret values.
 * Aligns with CI; sibling-var resolution for callers uses {@link org.customerxp.cd.CdSharedLibVarLookup}.
 *
 * @param args Map with keys: workspacePath (optional), envVars (Map of VAR_NAME -> value)
 * @return Absolute path to the created env file
 */
def call(Map args) {
    Map<String, String> envVars = args.envVars ?: [:]
    if (envVars.isEmpty()) {
        error '❌ prepareCDEnvFile: envVars map is required.'
    }
    def lines = []
    envVars.each { k, v ->
        if (v != null && v != '') {
            def escaped = v.toString().replace("'", "'\"'\"'")
            lines << "export ${k}='${escaped}'"
        }
    }
    def content = lines.join('\n') + '\n'
    def fileName = args.fileName?.toString()?.trim()
    if (!fileName) {
        fileName = env.JENKINS_CD_AUTOMATION_ENV_FILENAME?.toString()?.trim()
    }
    if (!fileName) {
        error 'prepareCDEnvFile: pass fileName or set JENKINS_CD_AUTOMATION_ENV_FILENAME.'
    }
    writeFile file: fileName, text: content
    def filePath = "${env.WORKSPACE}/${fileName}"
    echo "✅ CD env file prepared: ${fileName} (${envVars.size()} variables)"
    return filePath
}
