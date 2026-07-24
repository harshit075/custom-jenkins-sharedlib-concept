// vars/toolSonarqube.groovy
// SonarQube: bash fragment + withSonarQubeEnv + Quality Gate API polling.

import java.net.URLEncoder

String getSonarBlock(boolean enabled, String sonarCmd) {
    enabled ? """
                            echo "--- PHASE: SonarQube Analysis ---"
                            ${sonarCmd}
                        """ : ''
}

String getSonarBlockTag(boolean tagBuildSecurityTools, String sonarCmdForTag, String releaseBranchName) {
    tagBuildSecurityTools ? """
                                    echo "--- PHASE: SonarQube Analysis (release branch ${releaseBranchName}) ---"
                                    ${sonarCmdForTag}
                            """ : ''
}

void runMainBuildWithOptionalSonar(Map args) {
    String bashCommand = args.bashCommand?.toString()
    boolean effective = args.effectiveRunSecurityTools as boolean
    def cfg = args.cfg
    String moduleName = args.moduleName?.toString()
    String moduleBranch = args.moduleBranch?.toString()
    int qgTimeout = (args.qgTimeoutMinutes != null) ? args.qgTimeoutMinutes as int : 30

    if (effective) {
        withSonarQubeEnv(cfg.sonarQubeEnvName) {
            sh "${bashCommand} run_build.sh"
        }
        try {
            def qgStatus = waitForQualityGateViaApi(moduleName, moduleBranch, qgTimeout, cfg.sonarBaseUrl, cfg.sonarTokenCredentialId)
            if (qgStatus != 'OK') {
                echo "⚠️ Quality Gate failed for ${moduleName}: ${qgStatus}"
                currentBuild.result = 'UNSTABLE'
            } else {
                echo "✅ Quality Gate passed for ${moduleName}"
            }
        } catch (Exception e) {
            echo "⚠️ Quality Gate check timed out or failed for ${moduleName}: ${e.message}"
            echo "⚠️ Continuing build as quality gate enforcement is not strict."
            currentBuild.result = 'UNSTABLE'
        }
    } else {
        sh "${bashCommand} run_build.sh"
    }
}

void runTagRebuildWithOptionalSonar(Map args) {
    String bashCommand = args.bashCommand?.toString()
    boolean tagBuildSecurityTools = args.tagBuildSecurityTools as boolean
    def cfg = args.cfg
    String moduleName = args.moduleName?.toString()
    String releaseBranchName = args.releaseBranchName?.toString()
    int qgTimeout = (args.qgTimeoutMinutes != null) ? args.qgTimeoutMinutes as int : 10

    if (tagBuildSecurityTools) {
        withSonarQubeEnv(cfg.sonarQubeEnvName) {
            sh "${bashCommand} run_build_from_tag.sh"
        }
        try {
            def qgStatus = waitForQualityGateViaApi(moduleName, releaseBranchName, qgTimeout, cfg.sonarBaseUrl, cfg.sonarTokenCredentialId)
            if (qgStatus != 'OK') {
                echo "⚠️ Quality Gate failed for ${moduleName} (branch ${releaseBranchName}): ${qgStatus}"
                currentBuild.result = 'UNSTABLE'
            } else {
                echo "✅ Quality Gate passed for ${moduleName} (${releaseBranchName})"
            }
        } catch (Exception e) {
            echo "⚠️ Quality Gate check timed out or failed for ${moduleName} (${releaseBranchName}): ${e.message}"
            echo "⚠️ Continuing release build as quality gate enforcement is not strict."
            currentBuild.result = 'UNSTABLE'
        }
    } else {
        sh "${bashCommand} run_build_from_tag.sh"
    }
}

String waitForQualityGateViaApi(String moduleName, String branchName, int timeoutMinutes, String sonarHostUrl, String sonarTokenCredentialId) {
    if (!sonarHostUrl?.toString()?.trim()) {
        error('❌ waitForQualityGateViaApi: sonarHostUrl is required.')
    }
    if (!sonarTokenCredentialId?.toString()?.trim()) {
        error('❌ waitForQualityGateViaApi: sonarTokenCredentialId is required.')
    }
    def baseUrl = sonarHostUrl.toString().trim()
    while (baseUrl.endsWith('/')) {
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1)
    }
    def sonarProjectKeyScript = loadSharedLibVarScript('sonarProjectKey')
    if (!sonarProjectKeyScript) {
        error('❌ sonarProjectKey could not be loaded from the shared library.')
    }
    def projectKey = sonarProjectKeyScript.getSonarProjectKey(moduleName, branchName)
    def encodedProjectKey = URLEncoder.encode(projectKey, 'UTF-8')
    def maxSeconds = timeoutMinutes * 60
    def sleepSeconds = 15
    def elapsed = 0
    def lastNormalizedStatus = null

    echo "🔎 Polling Sonar Quality Gate via API for projectKey='${projectKey}' (timeout=${timeoutMinutes}m)"

    withCredentials([string(credentialsId: sonarTokenCredentialId, variable: 'SONAR_TOKEN')]) {
        while (elapsed < maxSeconds) {
            def rawStatus = sh(
                script: """#!/bin/bash
                        set +x
                        curl -s -u "\${SONAR_TOKEN}:" "${baseUrl}/api/qualitygates/project_status?projectKey=${encodedProjectKey}" | jq -r '.projectStatus.status // "IN_PROGRESS"'
                    """,
                returnStdout: true
            ).trim()
            def status = rawStatus?.toUpperCase()?.replaceAll('[^A-Z_]', '') ?: 'IN_PROGRESS'
            lastNormalizedStatus = status

            echo "📈 Sonar Quality Gate status for ${projectKey}: ${status} (raw='${rawStatus}')"
            if (status == 'OK' || status == 'ERROR' || status == 'WARN' || status == 'NONE') {
                return status
            }

            sleep(time: sleepSeconds, unit: 'SECONDS')
            elapsed += sleepSeconds
        }
    }

    if (lastNormalizedStatus in ['OK', 'ERROR', 'WARN', 'NONE']) {
        return lastNormalizedStatus
    }
    return 'TIMEOUT'
}

return this
