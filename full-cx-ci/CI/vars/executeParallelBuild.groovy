// vars/executeParallelBuild.groovy
//
// EXACT REPLICA OF: CustomerXP executeParallelBuild.groovy
//
// PURPOSE:
// Builds multiple installer modules SIMULTANEOUSLY.
// Each module gets its own parallel branch — they all execute at the same time.
// Used for installer modules that have no dependencies on each other.
//
// DIFFERENCE from executeChainBuild:
// - executeChainBuild: Module A finishes → Module B starts (sequential)
// - executeParallelBuild: Module A, B, C, D all start at the same time
//
// CustomerXP uses this for installer modules that are independent —
// each installer installs into a different application area and doesn't
// need artifacts from other installers.

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

    if (!selectedModulesList) {
        echo "⏭️ executeParallelBuild: no modules to build — skipping"
        return
    }
    if (!customBranchOverride?.trim()) error "❌ executeParallelBuild: customBranchOverride (branch) is required."

    def jdks = jdkVersions.split(',').collect { it.trim() }.findAll { it }

    echo "═══════════════════════════════════════════════════════════"
    echo "  executeParallelBuild"
    echo "  Modules (all simultaneous): ${selectedModulesList.join(', ')}"
    echo "  JDK versions: ${jdks.join(', ')}"
    echo "  Branch: ${customBranchOverride}"
    echo "  SecurityTools: ${runSecurityTools}"
    echo "═══════════════════════════════════════════════════════════"

    // ── Build task map: one entry per module × JDK combination ───────────────
    def parallelTasks = [:]

    selectedModulesList.each { moduleName ->
        jdks.each { jdk ->
            def currentModuleName = moduleName.trim()
            def currentJdkVersion = jdk.trim()
            def taskKey = "build-${currentModuleName}-${currentJdkVersion}"

            parallelTasks[taskKey] = {
                stage("Build ${currentModuleName} (JDK ${currentJdkVersion})") {
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

                            echo "✅ [PARALLEL] ${currentModuleName} (${currentJdkVersion}) SUCCESS"
                        } finally {
                            cleanWs(deleteDirs: true, disableDeferredWipeout: true, notFailBuild: true)
                        }
                    }
                }
            }
        }
    }

    // ── Execute all tasks simultaneously ──────────────────────────────────────
    parallel parallelTasks

    echo "✅ executeParallelBuild: ALL ${selectedModulesList.size()} module(s) completed"
}
