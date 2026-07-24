/**
 * Registers nightly SNAPSHOT CI job parameters and cron trigger.
 *
 * Uses existing globals: JENKINS_CRON_SCHEDULE, CX_JDK_VERSION_OPTIONS, CX_JDK_VERSION_DEFAULT,
 * JENKINS_DEFAULT_JDKS, JENKINS_RUN_SECURITY_TOOLS (via getDefaults).
 */
def call(Object pipelineEnv = null) {
    def getDefaultsScript = loadSharedLibVarScript('getDefaults')
    if (!getDefaultsScript) {
        error '❌ registerNightlyCiJobParameters: getDefaults could not be loaded from the shared library.'
    }

    def resolve = { String name ->
        if (pipelineEnv != null) {
            try {
                def map = pipelineEnv.getEnvironment()
                def v = map?.get(name)
                if (v?.toString()?.trim()) {
                    return v.toString().trim()
                }
            } catch (Throwable ignored) { }
            try {
                def v2 = pipelineEnv[name]
                if (v2?.toString()?.trim()) {
                    return v2.toString().trim()
                }
            } catch (Throwable ignored2) { }
        }
        return System.getenv(name)?.toString()?.trim() ?: ''
    }

    def cronExpr = resolve('JENKINS_CRON_SCHEDULE')
    if (!cronExpr) {
        cronExpr = getDefaultsScript.getCronSchedule()
    }

    def jdkOptsStr = resolve('CX_JDK_VERSION_OPTIONS')
        .split(',')
        .collect { it.trim() }
        .findAll { it }
        .join(',')
    if (!jdkOptsStr) {
        error '❌ registerNightlyCiJobParameters: set CX_JDK_VERSION_OPTIONS in Jenkins global properties.'
    }

    def jdkDefStr = (resolve('CX_JDK_VERSION_DEFAULT') ?: resolve('JENKINS_DEFAULT_JDKS')).trim()
    if (!jdkDefStr) {
        jdkDefStr = getDefaultsScript.getDefaultJDKs()
    }
    def jdkChoiceDefault = jdkDefStr.tokenize(',').first().trim()
    if (!jdkChoiceDefault) {
        error '❌ registerNightlyCiJobParameters: could not resolve default JDK (CX_JDK_VERSION_DEFAULT or JENKINS_DEFAULT_JDKS).'
    }

    boolean securityDefault = getDefaultsScript.getRunSecurityTools()

    properties([
        pipelineTriggers([cron(cronExpr)]),
        parameters([
            [$class: 'BooleanParameterDefinition',
                name: 'RUN_SECURITY_TOOLS',
                defaultValue: securityDefault,
                description: 'Run security tools (also gated by JENKINS_RUN_SECURITY_TOOLS global).'],
            [$class: 'ExtendedChoiceParameterDefinition',
                name: 'jdk_version',
                type: 'PT_CHECKBOX',
                value: jdkOptsStr,
                defaultValue: jdkChoiceDefault,
                description: 'JDK tool label(s); defaults from CX_JDK_VERSION_DEFAULT / JENKINS_DEFAULT_JDKS.'],
        ]),
    ])
}

return this
