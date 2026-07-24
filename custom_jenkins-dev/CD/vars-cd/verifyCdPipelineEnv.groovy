import org.customerxp.cd.CdPipelineEnvCheck

/**
 * Verifies CD-required Jenkins environment (globals + job env) before deploy-related stages.
 * Merges {@code env} with {@link #resolveCdEnv} so Manage Jenkins properties are visible like other CD steps.
 *
 * @param checkServersFile required — pass {@code true} to require {@code JENKINS_CD_SERVERS_CONFIG_FILE}
 * @param checkDbConfigFile required — pass {@code true} when validating DB config path
 * @param warnOnly if {@code true}, echo failures but do not call {@code error}
 */
def call(Map args = [:]) {
    if (!args.containsKey('checkServersFile')) {
        error 'verifyCdPipelineEnv: pass checkServersFile: true|false (no default).'
    }
    if (!args.containsKey('checkDbConfigFile')) {
        error 'verifyCdPipelineEnv: pass checkDbConfigFile: true|false (no default).'
    }
    boolean checkServersFile = args.checkServersFile as boolean
    boolean checkDbConfigFile = args.checkDbConfigFile as boolean
    boolean warnOnly = args.warnOnly as boolean

    Map<String, String> effective = CdPipelineEnvCheck.buildEffectiveEnv(env, { String name ->
        resolveCdEnv(name, env)
    })
    // Use def — Jenkins CPS sometimes cannot compile references to inner class CdPipelineEnvCheck.Result here.
    def result = CdPipelineEnvCheck.verify(effective, checkServersFile, checkDbConfigFile)

    echo result.fullReport()

    if (!result.ok && !warnOnly) {
        error result.summaryLine() + ' Fix the variables above (Manage Jenkins → Global properties → Environment variables, or folder/job env).'
    }
    if (!result.ok && warnOnly) {
        echo '⚠️ verifyCdPipelineEnv(warnOnly: true): continuing despite failures.'
    }

    return result
}
