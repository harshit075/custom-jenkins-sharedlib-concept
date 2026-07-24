/**
 * Module lists and JDK/security selection for nightly SNAPSHOT CI (globals only).
 */
def call(Map args = [:]) {
    def pipelineEnv = args.env ?: env
    def getDefaultsScript = loadSharedLibVarScript('getDefaults')
    if (!getDefaultsScript) {
        error '❌ resolveNightlyCiSelection: getDefaults could not be loaded from the shared library.'
    }

    def coreIds = (pipelineEnv.CX_CORE_MODULE_IDS ?: '')
        .split(',')
        .collect { it.trim() }
        .findAll { it }
    def instIds = (pipelineEnv.CX_INSTALLER_MODULE_IDS ?: '')
        .split(',')
        .collect { it.trim() }
        .findAll { it }

    if (coreIds.isEmpty() && instIds.isEmpty()) {
        error '❌ Nightly CI: set CX_CORE_MODULE_IDS and/or CX_INSTALLER_MODULE_IDS in Jenkins global properties.'
    }

    def jdkFromParam = (args.jdkVersionParam ?: '').trim()
    def jdkFromEnv = (pipelineEnv.CX_JDK_VERSION_DEFAULT ?: pipelineEnv.JENKINS_DEFAULT_JDKS ?: '').trim()
    def jdkVersions = jdkFromParam ?: jdkFromEnv
    if (!jdkVersions) {
        jdkVersions = getDefaultsScript.getDefaultJDKs()
    }
    if (!jdkVersions?.trim()) {
        error '❌ Nightly CI: set jdk_version, CX_JDK_VERSION_DEFAULT, or JENKINS_DEFAULT_JDKS.'
    }

    boolean runSecurityTools = getDefaultsScript.getRunSecurityTools()
    if (args.containsKey('runSecurityToolsParam') && args.runSecurityToolsParam != null) {
        def p = args.runSecurityToolsParam?.toString()?.trim()?.toLowerCase()
        runSecurityTools = !(p in ['false', '0', 'no', 'off'])
    }

    return [
        coreModuleList      : coreIds,
        instModuleList      : instIds,
        jdkVersions         : jdkVersions,
        runSecurityTools    : runSecurityTools,
    ]
}

return this
