// vars/toolGitleaks.groovy
// Gitleaks secret scan in module workspace, then DefectDojo import (scan type: Gitleaks Scan).
// Independent of Dependency-Track / SBOM. Invoked from securityToolsOrchestrator when catalog enables gitleaks.

/**
 * Runs {@code gitleaks detect -s . -f json -r <module>_gitleaks.json} in {@code pwd()}, then uploads via {@code uploadToDefectDojo}.
 * Product name comes from {@code getDefectDojoProductName(moduleBranch)} (same rules as other DefectDojo uploads).
 *
 * @param args.moduleName  Module id (CX_SMG_MODULE equivalent)
 * @param args.moduleBranch Branch / tag used for product mapping
 */
void runBranch(Map args) {
    String moduleName = args.moduleName?.toString()?.trim()
    String moduleBranch = args.moduleBranch?.toString()?.trim()
    if (!moduleName || !moduleBranch) {
        echo '⚠️ toolGitleaks.runBranch: moduleName and moduleBranch are required.'
        return
    }
    if (!env.DOJO_URL?.toString()?.trim()) {
        echo '⏭️ Skipping Gitleaks → DefectDojo (DOJO_URL unset).'
        return
    }

    def reportFile = "${moduleName}_gitleaks.json"
    echo "--- PHASE: Gitleaks scan for ${moduleName} ---"

    int onPath = sh(script: 'command -v gitleaks >/dev/null 2>&1', returnStatus: true)
    if (onPath != 0) {
        echo '⚠️ gitleaks not found on PATH — install Gitleaks on the agent or configure a Jenkins Tool. Skipping.'
        return
    }

    int rc = sh(script: "gitleaks detect -s . -f json -r '${reportFile}'", returnStatus: true)
    if (rc == 0) {
        echo '   Gitleaks exited 0 (no leaks detected, or tool success).'
    } else {
        echo "⚠️ Gitleaks exited ${rc} — findings or warnings may be present; uploading report if file exists."
    }

    if (!fileExists(reportFile)) {
        echo "⚠️ Gitleaks report not found at ${reportFile}; skipping DefectDojo upload."
        return
    }

    try {
        def uploadDefectDojoScript = loadSharedLibVarScript('uploadToDefectDojo')
        def getDojoProductScript = loadSharedLibVarScript('getDefectDojoProductName')
        if (!uploadDefectDojoScript || !getDojoProductScript) {
            echo '⚠️ uploadToDefectDojo or getDefectDojoProductName not found. Skipping DefectDojo upload.'
            return
        }
        def productName = getDojoProductScript.call(moduleBranch)
        uploadDefectDojoScript.call(
            moduleName    : moduleName,
            scanType      : 'Gitleaks Scan',
            reportFile    : reportFile,
            branch        : moduleBranch,
            buildNumber   : env.BUILD_NUMBER,
            productName   : productName,
            engagementName: moduleName,
            failOnError   : false
        )
    } catch (Exception e) {
        echo "⚠️ Gitleaks DefectDojo upload failed: ${e.message}"
    }
}

return this
