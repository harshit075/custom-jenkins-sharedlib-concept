// vars/securityToolsOrchestrator.groovy
// Linear security-tool pipeline: one phase after another; tools do not call each other.

void runBranchSecurityTooling(Map args) {
    def cat = loadSharedLibVarScript('securityToolsCatalog')
    if (!cat) {
        error '❌ securityToolsCatalog could not be loaded from the shared library.'
    }
    boolean master = args.effectiveRunSecurityTools as boolean

    def sonarScript = loadSharedLibVarScript('toolSonarqube')
    def sbomScript = loadSharedLibVarScript('toolSbomJacoco')
    def dtScript = loadSharedLibVarScript('toolDependencyTrack')
    def dojoScript = loadSharedLibVarScript('toolDefectdojo')
    def gitleaksScript = loadSharedLibVarScript('toolGitleaks')

    boolean sonarOn = cat == null ? master : cat.isToolEffective('sonarqube', master)
    boolean cyclonedxSbomOn = cat == null ? master : cat.isToolEffective('cyclonedx_sbom', master)
    boolean jacocoOn = cat == null ? master : cat.isToolEffective('jacoco', master)
    boolean dtOn = cat == null ? master : cat.isToolEffective('dependency_track', master)
    boolean dojoOn = cat == null ? master : cat.isToolEffective('defectdojo', master)
    boolean gitleaksOn = cat == null ? master : cat.isToolEffective('gitleaks', master)

    def sonarArgs = new LinkedHashMap(args)
    sonarArgs.effectiveRunSecurityTools = sonarOn

    def dtResult = [dtPublishOk: false, fullSbomPath: null]

    if (master) {
        sonarScript.runMainBuildWithOptionalSonar(sonarArgs)
    } else {
        echo '⏭️ Skipping security tooling phases (security tools disabled).'
        // Still run compile/package: toolSonarqube.runMainBuildWithOptionalSonar executes run_build.sh;
        // with effectiveRunSecurityTools=false it runs bash without Sonar env / QG (see vars/toolSonarqube.groovy).
        def buildOnlyArgs = new LinkedHashMap(sonarArgs)
        buildOnlyArgs.effectiveRunSecurityTools = false
        sonarScript.runMainBuildWithOptionalSonar(buildOnlyArgs)
        return
    }

    if (master && (jacocoOn || cyclonedxSbomOn)) {
        sbomScript.publishBranchTestReportsAndJacoco(
            moduleName            : args.moduleName?.toString(),
            masterSecurityToolsOn : master,
            jacocoCatalogEnabled  : master && jacocoOn
        )
    }

    for (String toolId in cat.getOrderedModuleToolIds()) {
        if (!cat.isToolEffective(toolId, master)) {
            continue
        }
        switch (toolId) {
            case 'gitleaks':
                if (gitleaksOn && gitleaksScript != null) {
                    gitleaksScript.runBranch(args)
                } else if (gitleaksOn && gitleaksScript == null) {
                    echo '⚠️ toolGitleaks could not be loaded; skipping Gitleaks.'
                }
                break
            case 'dependency_track':
                if (dtOn) {
                    dtResult = dtScript.publishToDependencyTrackBranch(args)
                }
                break
            case 'defectdojo':
                if (dojoOn && dtResult.dtPublishOk) {
                    dojoScript.uploadBranch(
                        moduleName        : args.moduleName?.toString(),
                        moduleBranch      : args.moduleBranch?.toString(),
                        fullSbomPath      : dtResult.fullSbomPath,
                        defectdojoEnabled : true
                    )
                } else if (dojoOn && !dtResult.dtPublishOk) {
                    echo '⏭️ Skipping DefectDojo (Dependency-Track did not publish or no SBOM path).'
                }
                break
            default:
                echo "⚠️ Unknown tool id in catalog order: ${toolId}"
        }
    }
}

void runTagRebuildSecurityTooling(Map args) {
    def sonarScript = loadSharedLibVarScript('toolSonarqube')
    def sbomScript = loadSharedLibVarScript('toolSbomJacoco')
    def dtScript = loadSharedLibVarScript('toolDependencyTrack')

    boolean tagTools = args.tagBuildSecurityTools as boolean

    sonarScript.runTagRebuildWithOptionalSonar(args)
    sbomScript.publishTagRebuildTestReportsAndJacoco(
        moduleName             : args.moduleName?.toString(),
        releaseBranchName      : args.releaseBranchName?.toString(),
        tagName                : args.tagName?.toString(),
        tagBuildSecurityTools  : tagTools
    )
    if (tagTools) {
        dtScript.publishToDependencyTrackTagRebuild(
            cfg               : args.cfg,
            moduleName        : args.moduleName?.toString(),
            releaseBranchName : args.releaseBranchName?.toString(),
            jdkVersion        : args.jdkVersion?.toString(),
            namespace         : args.namespace?.toString()
        )
    } else {
        echo '⏭️ Skipping Dependency-Track for tag rebuild (security tools disabled).'
    }
}

return this
