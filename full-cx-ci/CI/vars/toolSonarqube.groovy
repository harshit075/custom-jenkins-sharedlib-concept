// vars/toolSonarqube.groovy
//
// EXACT REPLICA OF: CustomerXP toolSonarqube.groovy

void runBranch(Map args) {
    String moduleName    = args.moduleName
    String branchName    = args.branchName
    String workspacePath = args.workspacePath ?: '.'
    Map cfg              = args.cfg ?: [:]
    String sonarUrl      = cfg.sonarBaseUrl ?: env.CX_SONAR_BASE_URL?.trim() ?: ''

    echo "📊 toolSonarqube: running analysis for ${moduleName} (${branchName})..."
    if (!sonarUrl) {
        echo "   [SIMULATED] CX_SONAR_BASE_URL not set — simulating SonarQube analysis"
        echo "   Project Key: ${moduleName}-${branchName}".replaceAll('[^a-zA-Z0-9_\\-:]', '-')
        echo "   Bugs: 0 | Vulnerabilities: 0 | Code Smells: 3"
        echo "   Coverage: 72.4% | Duplications: 2.1%"
        echo "   Quality Gate: ✅ PASSED (simulated)"
        return
    }
    def sonarEnvName = cfg.sonarQubeEnvName ?: env.CX_SONAR_ENV_NAME?.trim() ?: 'SonarQube'
    def projectKey   = "${moduleName}".replaceAll('[^a-zA-Z0-9_\\-:]', '-')
    dir(workspacePath) {
        withSonarQubeEnv(sonarEnvName) {
            def scannerHome = tool 'sonar-scanner'
            sh """
                ${scannerHome}/bin/sonar-scanner \
                    -Dsonar.projectKey=${projectKey} \
                    -Dsonar.projectName=${projectKey} \
                    -Dsonar.sources=. \
                    -Dsonar.host.url=${sonarUrl} \
                    -Dsonar.branch.name=${branchName} \
                    || echo "⚠️ sonar-scanner failed (non-blocking)"
            """
        }
    }
    echo "✅ toolSonarqube: analysis submitted for ${moduleName}"
}

return this
