// vars/toolDefectdojo.groovy
// DefectDojo upload after Dependency-Track (uses SBOM path for upload contract with existing uploadToDefectDojo).

void uploadBranch(Map args) {
    String moduleName = args.moduleName?.toString()
    String moduleBranch = args.moduleBranch?.toString()
    String fullSbomPath = args.fullSbomPath?.toString()
    boolean defectdojoEnabled = args.containsKey('defectdojoEnabled') ? (args.defectdojoEnabled as boolean) : true

    if (!defectdojoEnabled) {
        echo "⏭️ Skipping DefectDojo upload for ${moduleName} (disabled in securityToolsCatalog.groovy)."
        return
    }
    if (!env.DOJO_URL?.toString()?.trim()) {
        return
    }
    if (!fullSbomPath?.trim()) {
        echo "⚠️ DefectDojo: no SBOM path; skipping upload for ${moduleName}."
        return
    }

    echo "--- PHASE: Uploading Dependency-Track findings to DefectDojo for ${moduleName} ---"
    try {
        def uploadDefectDojoScript = loadSharedLibVarScript('uploadToDefectDojo')
        def getDojoProductScript = loadSharedLibVarScript('getDefectDojoProductName')
        if (uploadDefectDojoScript && getDojoProductScript) {
            def scanType = 'Dependency Track Finding Packaging Format (FPF) Export'
            def productName = getDojoProductScript.call(moduleBranch)

            uploadDefectDojoScript.call(
                moduleName : moduleName,
                scanType   : scanType,
                reportFile : fullSbomPath,
                branch     : moduleBranch,
                buildNumber: env.BUILD_NUMBER,
                productName: productName,
                failOnError: false
            )
        } else {
            echo '⚠️ uploadToDefectDojo or getDefectDojoProductName utility not found. Skipping DefectDojo upload.'
        }
    } catch (Exception e) {
        echo "⚠️ WARNING: Failed to upload to DefectDojo for ${moduleName}: ${e.message}"
        echo '⚠️ Build will continue, but DefectDojo upload was not performed.'
    }
}

return this
