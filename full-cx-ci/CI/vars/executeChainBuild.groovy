// vars/executeChainBuild.groovy
//
// EXACT REPLICA OF: CustomerXP executeChainBuild.groovy
//
// PURPOSE:
// Orchestrates sequential + parallel-JDK build for a set of modules.
// This is the main execution engine for the CI pipeline.
//
// BEHAVIOR (mirrors CustomerXP exactly):
// 1. Resolves canonical module order via resolveChainBuildModules()
// 2. For each module: builds against ALL selected JDK versions IN PARALLEL
//    (e.g., JDK-17 and JDK-11 build simultaneously for the same module)
// 3. Each JDK build runs in a dedicated workspace: {workspaceBase}/{module}-{branch}-{jdk}
// 4. Uses catchError so one JDK failure doesn't abort other JDK builds
// 5. Aggregates per-module results and fails pipeline if any module failed
//
// ADAPTED FOR LOCAL JENKINS:
// - Uses CX_WORKSPACE_BASE instead of CX_SMG_DEV for workspace paths
// - Skips Maven-specific setup (no JAVA_HOME override needed for Docker Jenkins)
// - Still calls buildModulesWithJDK for the actual clone + build + scan

def call(Map args) {
    List<String> selectedModulesList = args.selectedModulesList ?: []
    String jdkVersions               = args.jdkVersions ?: env.CX_JDK_VERSION_DEFAULT ?: 'jdk-17'
    String cxSmgBase                 = args.cxSmgBase ?: env.CX_WORKSPACE_BASE
    boolean isCoreType               = args.containsKey('isCoreType') ? (args.isCoreType as boolean) : false
    String customBranchOverride      = args.customBranchOverride
    String releaseTag                = args.releaseTag
    Map pipelineCfg                  = args.containsKey('pipelineCfg') ? (args.pipelineCfg as Map) : null

    def runSecurityToolsParams = loadSharedLibVarScript('runSecurityToolsParams')
    if (!runSecurityToolsParams) error '❌ runSecurityToolsParams script not found.'
    boolean runSecurityTools = runSecurityToolsParams.resolveJobRunSecurityTools(args.runSecurityTools)

    if (!selectedModulesList) error "❌ executeChainBuild: no modules provided."
    if (!customBranchOverride?.trim()) error "❌ executeChainBuild: customBranchOverride (branch) is required."

    // ── 1. Resolve canonical order ────────────────────────────────────────────
    def resolveScript = loadSharedLibVarScript('resolveChainBuildModules')
    def canonicalOrder = resolveScript.call(selectedModulesList.join(','))

    def jdks = jdkVersions.split(',').collect { it.trim() }.findAll { it }
    def moduleBuildResults = [:]

    echo "═══════════════════════════════════════════════════════════"
    echo "  executeChainBuild"
    echo "  Modules (canonical order): ${canonicalOrder.join(' → ')}"
    echo "  JDK versions: ${jdks.join(', ')}"
    echo "  Branch: ${customBranchOverride}"
    echo "  CoreType: ${isCoreType}"
    echo "  SecurityTools: ${runSecurityTools}"
    echo "═══════════════════════════════════════════════════════════"

    // ── 2. Build each module sequentially ────────────────────────────────────
    for (String module : canonicalOrder) {
        def currentModuleName = module.trim()
        if (!selectedModulesList.contains(currentModuleName)) continue

        def currentModuleJdkResults = [:]
        def parallelJdkBuilds = [:]

        // ── 3. For each module, build all JDKs in PARALLEL ───────────────────
        jdks.each { jdk ->
            def currentJdkVersion = jdk.trim()
            def stageName = "${currentModuleName} (JDK ${currentJdkVersion})"

            parallelJdkBuilds[stageName] = {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    stage(stageName) {
                        def workspacePath = "${cxSmgBase}/${currentModuleName}-${customBranchOverride}-${currentJdkVersion}"
                        workspacePath = workspacePath.replaceAll('[/\\\\]+', '/')

                        ws(workspacePath) {
                            try {
                                def buildScriptName = (releaseTag?.trim()) ? 'buildModulesWithJDKReleaseTag' : 'buildModulesWithJDK'
                                def buildScript = loadSharedLibVarScript(buildScriptName)
                                if (!buildScript) error "❌ ${buildScriptName} not found in shared library."

                                def reactCiResolver = loadSharedLibVarScript('resolveReactCiEnv')
                                String reactCiExport = reactCiResolver ? reactCiResolver.effective(pipelineCfg) : 'false'

                                withEnv([
                                    "CI=${reactCiExport}",
                                    "CX_CURRENT_MODULE=${currentModuleName}",
                                    "CX_CURRENT_JDK=${currentJdkVersion}",
                                    "CX_CURRENT_BRANCH=${customBranchOverride}",
                                    "PIPELINE_DEBUG=${pipelineCfg?.m2DebugExplicit ?: env.CX_PIPELINE_DEBUG ?: 'false'}"
                                ]) {
                                    if (releaseTag?.trim()) {
                                        buildScript.call(currentModuleName, currentJdkVersion, workspacePath, cxSmgBase, isCoreType, customBranchOverride, releaseTag.trim(), runSecurityTools, pipelineCfg)
                                    } else {
                                        buildScript.call(currentModuleName, currentJdkVersion, workspacePath, cxSmgBase, isCoreType, customBranchOverride, runSecurityTools, pipelineCfg)
                                    }
                                }

                                echo "✅ [${stageName}] Build SUCCESS"
                                currentModuleJdkResults[currentJdkVersion] = 'SUCCESS'
                            } finally {
                                cleanWs(deleteDirs: true, disableDeferredWipeout: true, notFailBuild: true)
                            }
                        }
                    }
                }
            }
        } // end jdks.each

        // ── 4. Run all JDK builds for this module in parallel ─────────────────
        if (parallelJdkBuilds.size() == 1) {
            // Single JDK — run directly (no parallel overhead)
            parallelJdkBuilds.each { name, task -> task() }
        } else {
            parallel parallelJdkBuilds
        }

        // ── 5. Check results for this module ──────────────────────────────────
        def failedJdks = jdks.findAll { currentModuleJdkResults[it] == 'FAILURE' }
        moduleBuildResults[currentModuleName] = failedJdks.isEmpty() ? 'SUCCESS' : 'FAILURE'

        if (!failedJdks.isEmpty()) {
            echo "❌ Module '${currentModuleName}' failed on JDKs: ${failedJdks.join(', ')}"
        } else {
            echo "✅ Module '${currentModuleName}' succeeded on all JDKs"
        }
    } // end for each module

    // ── 6. Final result check ─────────────────────────────────────────────────
    def failedModules = moduleBuildResults.findAll { k, v -> v == 'FAILURE' }.keySet()
    if (failedModules) {
        error "❌ executeChainBuild: the following modules FAILED: ${failedModules.join(', ')}"
    }

    echo "✅ executeChainBuild: ALL modules completed successfully"
}
