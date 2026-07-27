// vars/securityToolsCatalog.groovy
//
// EXACT REPLICA OF: CustomerXP securityToolsCatalog.groovy
//
// PURPOSE:
// Defines the catalog of all available security tools.
// Each tool has: name, enabled flag, and required env vars.
// securityToolsOrchestrator reads this catalog to decide what to run.
//
// ADAPTED FOR LOCAL JENKINS:
// All tools are present in catalog but "effective" based on whether
// their required config (server URLs) is available.

/**
 * Returns the full tool catalog.
 * Each entry: [name, envKey, defaultEnabled, requiredEnvVars]
 */
List<Map> getCatalog() {
    return [
        [
            name: 'gitleaks',
            envKey: 'gitleaks',
            defaultEnabled: true,
            description: 'Scan source code for hardcoded secrets (API keys, tokens, passwords)',
            requiredEnvVars: []   // No server needed — runs locally
        ],
        [
            name: 'cyclonedx_sbom',
            envKey: 'cyclonedx_sbom',
            defaultEnabled: true,
            description: 'Generate Software Bill of Materials (SBOM) in CycloneDX format',
            requiredEnvVars: []   // No server needed — generates local file
        ],
        [
            name: 'jacoco',
            envKey: 'jacoco',
            defaultEnabled: true,
            description: 'JaCoCo code coverage analysis',
            requiredEnvVars: []   // No server needed — Maven/Gradle plugin
        ],
        [
            name: 'sonarqube',
            envKey: 'sonarqube',
            defaultEnabled: false,  // Requires CX_SONAR_BASE_URL
            description: 'SonarQube static analysis and quality gate',
            requiredEnvVars: ['CX_SONAR_BASE_URL']
        ],
        [
            name: 'dependency_track',
            envKey: 'dependency_track',
            defaultEnabled: false,  // Requires Dependency-Track server
            description: 'Upload SBOM to Dependency-Track for CVE analysis',
            requiredEnvVars: ['CX_DEPENDENCY_TRACK_URL']
        ],
        [
            name: 'defectdojo',
            envKey: 'defectdojo',
            defaultEnabled: false,  // Requires DefectDojo server
            description: 'Upload findings to DefectDojo vulnerability management',
            requiredEnvVars: ['DOJO_URL']
        ],
    ]
}

/**
 * Returns whether a tool is effectively enabled.
 * Tool must be: globally enabled AND all required env vars present.
 * MAPS TO: CustomerXP's securityToolsCatalog.isToolEffective()
 */
boolean isToolEffective(String toolName, boolean globalSecurityEnabled) {
    if (!globalSecurityEnabled) return false

    // Check per-tool override env var: CX_TOOL_{TOOLNAME}_ENABLED
    def toolEnvKey = "CX_TOOL_${toolName.toUpperCase().replaceAll('[^A-Z0-9]', '_')}_ENABLED"
    def explicitOverride = env[toolEnvKey]?.trim()?.toLowerCase()
    if (explicitOverride == 'false') return false
    if (explicitOverride == 'true') return true

    // Check required env vars are present
    def entry = getCatalog().find { it.name == toolName || it.envKey == toolName }
    if (!entry) return false

    boolean requiredPresent = entry.requiredEnvVars.every { envVar ->
        env[envVar]?.trim()
    }

    return entry.defaultEnabled && requiredPresent
}

/**
 * Returns list of missing env vars for all enabled tools.
 * Used by cxPipelineConfig for early validation.
 */
List<String> collectMissingEnvForEnabledTools(boolean globalSecurityEnabled) {
    if (!globalSecurityEnabled) return []
    def missing = []
    getCatalog().each { tool ->
        if (tool.defaultEnabled) {
            tool.requiredEnvVars.each { envVar ->
                if (!env[envVar]?.trim()) {
                    missing << "${envVar} (required for ${tool.name})"
                }
            }
        }
    }
    return missing
}

return this
