// vars/resolveNightlyCiSelection.groovy
//
// EXACT REPLICA OF: CustomerXP resolveNightlyCiSelection.groovy
//
// PURPOSE: Resolves which modules, JDKs, and security settings to use for nightly builds.

def call() {
    def coreModuleIds    = (env.CX_CORE_MODULE_IDS ?: '').split(',').collect { it.trim() }.findAll { it }
    def instModuleIds    = (env.CX_INSTALLER_MODULE_IDS ?: '').split(',').collect { it.trim() }.findAll { it }
    def jdkVersions      = params.jdk_version ?: env.CX_JDK_VERSION_DEFAULT ?: 'jdk-17'
    def runSecTools      = params.RUN_SECURITY_TOOLS != null ? (params.RUN_SECURITY_TOOLS as boolean) : false

    if (!coreModuleIds) error "❌ resolveNightlyCiSelection: CX_CORE_MODULE_IDS not set"
    if (!instModuleIds) error "❌ resolveNightlyCiSelection: CX_INSTALLER_MODULE_IDS not set"

    echo "📋 Nightly CI selection:"
    echo "   Core modules: ${coreModuleIds.join(', ')}"
    echo "   Installer modules: ${instModuleIds.join(', ')}"
    echo "   JDK versions: ${jdkVersions}"
    echo "   Security tools: ${runSecTools}"

    return [
        coreModuleList: coreModuleIds,
        instModuleList: instModuleIds,
        jdkVersions:    jdkVersions,
        runSecurityTools: runSecTools
    ]
}

return this
