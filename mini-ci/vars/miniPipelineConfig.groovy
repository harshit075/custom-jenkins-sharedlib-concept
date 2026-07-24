/**
 * miniPipelineConfig.groovy
 * 
 * MAPS TO: CI/vars/cxPipelineConfig.groovy in CustomerXP
 * 
 * PURPOSE:
 * Validates that all required Jenkins global environment variables exist.
 * Returns a config Map used by all downstream stages.
 * Fails fast with a clear error if anything is missing.
 *
 * CustomerXP requires 30+ env vars (GitLab, Nexus, SonarQube, etc.)
 * Our mini version uses just a few to demonstrate the same pattern.
 */
def call() {
    echo "═══════════════════════════════════════════════════"
    echo "  miniPipelineConfig: Validating Global Configuration"
    echo "═══════════════════════════════════════════════════"

    def errors = []

    // --- Required globals (set in Manage Jenkins → System → Environment variables) ---
    def chainOrder = env.MINI_CHAIN_ORDER?.trim()
    if (!chainOrder) errors << "MINI_CHAIN_ORDER (comma-separated module build order)"

    def coreModuleIds = env.MINI_CORE_MODULE_IDS?.trim()
    if (!coreModuleIds) errors << "MINI_CORE_MODULE_IDS (comma-separated core module IDs)"

    def installerModuleIds = env.MINI_INSTALLER_MODULE_IDS?.trim()
    if (!installerModuleIds) errors << "MINI_INSTALLER_MODULE_IDS (comma-separated installer module IDs)"

    def gitBaseUrl = env.MINI_GIT_BASE_URL?.trim()
    if (!gitBaseUrl) errors << "MINI_GIT_BASE_URL (e.g., https://github.com)"

    def gitOrg = env.MINI_GIT_ORG?.trim()
    if (!gitOrg) errors << "MINI_GIT_ORG (e.g., your GitHub username or org)"

    // --- Fail if any required vars are missing ---
    if (errors) {
        def msg = """
╔══════════════════════════════════════════════════════════════╗
║  ❌ MISSING REQUIRED JENKINS GLOBAL ENVIRONMENT VARIABLES   ║
╠══════════════════════════════════════════════════════════════╣
${errors.collect { "║  • ${it}" }.join('\n')}
╠══════════════════════════════════════════════════════════════╣
║  Fix: Manage Jenkins → System → Environment variables       ║
╚══════════════════════════════════════════════════════════════╝
"""
        error msg
    }

    // --- Build config map (like CustomerXP's cfg return object) ---
    def config = [
        chainOrder:       chainOrder.split(',').collect { it.trim() }.findAll { it },
        coreModuleIds:    coreModuleIds.split(',').collect { it.trim() }.findAll { it },
        installerModuleIds: installerModuleIds.split(',').collect { it.trim() }.findAll { it },
        gitBaseUrl:       gitBaseUrl,
        gitOrg:           gitOrg,
        buildType:        env.MINI_BUILD_TYPE ?: 'snapshot',
        verboseLogging:   env.MINI_VERBOSE == 'true'
    ]

    echo "✅ Configuration validated successfully!"
    echo "   Chain Order: ${config.chainOrder.join(' → ')}"
    echo "   Core Modules: ${config.coreModuleIds.join(', ')}"
    echo "   Installer Modules: ${config.installerModuleIds.join(', ')}"
    echo "   Git Base: ${config.gitBaseUrl}/${config.gitOrg}"
    echo "   Build Type: ${config.buildType}"

    return config
}
