// vars/getSonarCommand.groovy
//
// EXACT REPLICA OF: CustomerXP getSonarCommand.groovy

def call(String moduleName, String branchName) {
    def sonarBase = env.CX_SONAR_BASE_URL?.trim() ?: env.SONAR_HOST_URL?.trim() ?: ''
    if (!sonarBase) {
        return "echo '📊 [SIMULATED] sonar-scanner -Dsonar.projectKey=${moduleName} (CX_SONAR_BASE_URL not set)'"
    }
    def sonarKeyScript = loadSharedLibVarScript('sonarProjectKey')
    def projectKey = sonarKeyScript ? sonarKeyScript.getSonarProjectKey(moduleName, branchName) : moduleName
    try {
        def scannerHome = tool 'sonar-scanner'
        return """
echo "--- SonarQube Analysis: ${moduleName} on ${branchName} ---"
${scannerHome}/bin/sonar-scanner \\
    -Dsonar.projectKey=${projectKey} \\
    -Dsonar.projectName=${projectKey} \\
    -Dsonar.sources=. \\
    -Dsonar.java.binaries=target/classes \\
    -Dsonar.host.url=${sonarBase} \\
    -Dsonar.branch.name=${branchName} \\
    || echo "⚠️ SonarQube analysis failed (non-blocking)"
"""
    } catch (Throwable t) {
        return "echo '⚠️ sonar-scanner tool not configured in Jenkins Tools — skipping'"
    }
}

return this
