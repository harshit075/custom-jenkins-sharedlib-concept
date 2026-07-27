// vars/toolDefectdojo.groovy
//
// EXACT REPLICA OF: CustomerXP toolDefectdojo.groovy

void uploadBranch(Map args) {
    String moduleName    = args.moduleName
    String moduleBranch  = args.moduleBranch
    String fullSbomPath  = args.fullSbomPath
    boolean enabled      = args.containsKey('defectdojoEnabled') ? (args.defectdojoEnabled as boolean) : true
    String dojoUrl       = env.DOJO_URL?.trim() ?: ''

    if (!enabled) { echo "⏭️ toolDefectdojo: disabled — skipping ${moduleName}"; return }
    if (!dojoUrl) { echo "   [SIMULATED] DOJO_URL not set — skipping DefectDojo upload for ${moduleName}"; return }
    if (!fullSbomPath?.trim()) { echo "⚠️ toolDefectdojo: no SBOM path — skipping ${moduleName}"; return }

    echo "🛡️ toolDefectdojo: uploading findings for ${moduleName}..."
    def uploadScript = loadSharedLibVarScript('uploadToDefectDojo')
    def productNameScript = loadSharedLibVarScript('getDefectDojoProductName')
    if (uploadScript && productNameScript) {
        def productName = productNameScript.call(moduleBranch)
        uploadScript.call(
            moduleName: moduleName, scanType: 'Dependency Track Finding Packaging Format (FPF) Export',
            reportFile: fullSbomPath, branch: moduleBranch, buildNumber: env.BUILD_NUMBER,
            productName: productName, failOnError: false
        )
    } else {
        echo "⚠️ uploadToDefectDojo or getDefectDojoProductName not found — skipping"
    }
}

return this
