// vars/securityToolsCommon.groovy
//
// EXACT REPLICA OF: CustomerXP securityToolsCommon.groovy
//
// PURPOSE:
// Shared utilities used across all security tool scripts.

boolean isGradleOnlyModule(String moduleName) {
    def gradleModules = (env.CX_GRADLE_ONLY_MODULES ?: '')
        .split(',').collect { it.trim() }.findAll { it }
    return gradleModules.contains(moduleName)
}

return this
