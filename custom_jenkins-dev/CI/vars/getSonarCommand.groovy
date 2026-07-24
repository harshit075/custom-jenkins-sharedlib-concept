// vars/getSonarCommand.groovy
/**
 * Generates SonarQube scanner command with module-branch naming convention.
 * Project key format: {module-name}-{branch-name} (e.g., "aiml-core-4.10.x", "platform-develop")
 * 
 * @param moduleName The module name (e.g., "aiml-core", "platform")
 * @param branchName The branch name (e.g., "develop", "develop-4.10.x", "neo.x")
 * @return Shell command string for SonarQube scanner
 */
def call(String moduleName, String branchName) {
    def sonarBase = env.JENKINS_SONAR_URL?.toString()?.trim() ?: env.SONAR_HOST_URL?.toString()?.trim()
    if (!sonarBase) {
        error("❌ getSonarCommand: set JENKINS_SONAR_URL or SONAR_HOST_URL (see vars/cxPipelineConfig.groovy).")
    }
    def scannerHome = tool 'sonar-scanner'

    def sonarProjectKeyScript = loadSharedLibVarScript('sonarProjectKey')
    if (!sonarProjectKeyScript) {
        error('❌ sonarProjectKey could not be loaded from the shared library.')
    }
    def projectKey = sonarProjectKeyScript.getSonarProjectKey(moduleName, branchName, '❌ getSonarCommand: branchName must be set.')
    def projectName = projectKey

    return """
        echo "--- Starting SonarQube Analysis for ${moduleName} on branch ${branchName} ---"
        echo "--- Sonar Project Key: ${projectKey} ---"
        ${scannerHome}/bin/sonar-scanner \
        -Dsonar.projectKey=${projectKey} \
        -Dsonar.projectName=${projectName} \
        -Dsonar.sources=. \
        -Dsonar.java.binaries=. \
        -Dsonar.host.url=${sonarBase}
    """
    // Note: Changed sonar.java.binaries to '.' 
    // This tells Sonar to search the current directory recursively for all compiled .class files
}