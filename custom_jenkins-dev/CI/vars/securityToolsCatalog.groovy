// vars/securityToolsCatalog.groovy
//
// Top-level `def` maps are not instance fields in Jenkins vars scripts — methods must use @Field
// or they throw MissingPropertyException (e.g. TOOL_ENABLED in isToolEnabled).

import groovy.transform.Field

//
// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  CLIENT CONFIGURATION — Security tools registry                              ║
// ║                                                                              ║
// ║  • Set a tool to false to disable it temporarily (no other files to edit). ║
// ║  • To add a new tool: add an entry to TOOL_ENABLED, document env vars in     ║
// ║    TOOL_ENV_DOCUMENTATION, then implement behaviour in vars/tool*.groovy (see     ║
// ║    securityToolsOrchestrator.groovy for linear order).                         ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Linear Groovy order for post-{@code run_build.sh} tools only.
 * Phase 0 (always when security master on): {@code toolSonarqube.runMainBuildWithOptionalSonar} — see TOOL_ENABLED.sonarqube for Sonar env + QG only.
 * Post-Sonar order below (test/JaCoCo HTML publish runs before this loop when cyclonedx_sbom or jacoco is on — see securityToolsOrchestrator).
 */
@Field
List<String> ORDERED_MODULE_TOOL_IDS = [
    'gitleaks',
    'dependency_track',
    'defectdojo',
].asImmutable()

List<String> getOrderedModuleToolIds() {
    ORDERED_MODULE_TOOL_IDS
}

/** Master switches per integrated security capability. Comment a line and merge with comma rules — or set false. */
@Field
Map TOOL_ENABLED = [
    sonarqube                  : true,   // SonarScanner, QG API, withSonarQubeEnv
    cyclonedx_sbom             : true,   // CycloneDX SBOM in compilation / run_build.sh (Gradle/Maven)
    jacoco                     : true,   // JaCoCo tasks + Jenkins coverage HTML (vars/toolSbomJacoco)
    dependency_track           : true,   // SBOM publish to Dependency-Track
    defectdojo                 : true,   // DT → DefectDojo (after DT publish)
    gitleaks                   : true,   // Gitleaks JSON → DefectDojo (Gitleaks Scan); runs before DT, no SBOM
    jenkins_sonar_html_report  : true,   // Jenkins HTML “Sonar Report” (publishSonarReport)
]

/** Documentation only: expected env when a tool is enabled (see collectMissingEnvForEnabledTools for enforced keys). */
@Field
Map TOOL_ENV_DOCUMENTATION = [
    sonarqube                 : [
        'JENKINS_SONAR_URL',
        'JENKINS_SONARQUBE_ENV_NAME',
        'JENKINS_SONAR_TOKEN_CREDENTIAL_ID',
    ],
    cyclonedx_sbom            : [
        '(build-time; no extra Jenkins globals beyond standard compile)',
    ],
    jacoco                    : [
        '(build-time; JaCoCo reports — no extra Jenkins globals beyond standard compile)',
    ],
    dependency_track          : [
        'JENKINS_DEPENDENCY_TRACK_INSTANCE',
    ],
    defectdojo                : [
        'DOJO_URL; JENKINS_DOJO_PRODUCT_RULES (product name)',
    ],
    gitleaks                  : [
        'DOJO_URL; JENKINS_DOJO_PRODUCT_RULES; gitleaks on agent PATH (or Tool); DefectDojo API (same credential as uploadToDefectDojo)',
    ],
    jenkins_sonar_html_report : [
        'JENKINS_SONAR_URL or SONAR_HOST_URL',
        'JENKINS_SONAR_TOKEN_CREDENTIAL_ID',
    ],
]

boolean isToolEnabled(String toolId) {
    if (toolId == null || !toolId.toString().trim()) {
        return true
    }
    String id = toolId.toString().trim()
    if (!TOOL_ENABLED.containsKey(id)) {
        return true
    }
    return TOOL_ENABLED[id] == true
}

boolean isToolEffective(String toolId, boolean masterSecurityToolsOn) {
    return masterSecurityToolsOn && isToolEnabled(toolId)
}

List<String> collectMissingEnvForEnabledTools(boolean masterSecurityToolsOn) {
    List<String> missing = []
    if (!masterSecurityToolsOn) {
        return missing
    }

    def reqRaw = { String name ->
        def v = env["${name}"]
        if (v == null || v.toString().trim().isEmpty()) {
            missing << name
        }
    }

    if (isToolEnabled('sonarqube')) {
        def sonarUrl = env.JENKINS_SONAR_URL?.toString()?.trim() ?: env.SONAR_HOST_URL?.toString()?.trim()
        if (!sonarUrl) {
            missing << 'JENKINS_SONAR_URL or SONAR_HOST_URL'
        }
        reqRaw('JENKINS_SONARQUBE_ENV_NAME')
        reqRaw('JENKINS_SONAR_TOKEN_CREDENTIAL_ID')
    }

    if (isToolEnabled('dependency_track')) {
        reqRaw('JENKINS_DEPENDENCY_TRACK_INSTANCE')
    }

    return missing.unique()
}

return this
