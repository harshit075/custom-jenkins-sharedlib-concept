// vars/registerNightlyCiJobParameters.groovy
//
// EXACT REPLICA OF: CustomerXP registerNightlyCiJobParameters.groovy

def call() {
    def getDefaultsScript = loadSharedLibVarScript('getDefaults')
    def cronSchedule = ''
    try { cronSchedule = getDefaultsScript ? getDefaultsScript.getCronSchedule() : env.JENKINS_CRON_SCHEDULE ?: '0 2 * * *' } catch (Throwable t) { cronSchedule = '0 2 * * *' }

    def jdkOpts = (env.CX_JDK_VERSION_OPTIONS ?: 'jdk-17').split(',').collect { it.trim() }.findAll { it }.join(',')
    def jdkDefault = (env.CX_JDK_VERSION_DEFAULT ?: 'jdk-17').trim()

    properties([
        pipelineTriggers([cron(cronSchedule)]),
        parameters([
            booleanParam(name: 'RUN_SECURITY_TOOLS', defaultValue: false, description: 'Run security tools (Gitleaks, SBOM, Sonar, DT, DefectDojo)'),
            [$class: 'ExtendedChoiceParameterDefinition', name: 'jdk_version', type: 'PT_CHECKBOX', value: jdkOpts, defaultValue: jdkDefault, description: 'JDK version(s) to use']
        ])
    ])
    echo "✅ registerNightlyCiJobParameters: cron=${cronSchedule}, jdkOptions=${jdkOpts}"
}

return this
