/**
 * Orchestrates a parallel build process for installers with dynamic branch support.
 * Supports per-module branch selection via moduleBranchMap parameter.
 */
def call(Map args) {
    List<String> selectedModulesListFromParams = args.selectedModulesList
    String jdkVersions = args.jdkVersions
    String cxSmgBase = args.cxSmgBase
    boolean isCoreType = args.isCoreType
    // NEW: Accept either a map of module-to-branch mappings OR a single customBranchOverride (for backward compatibility)
    Map<String, String> moduleBranchMap = args.moduleBranchMap ?: [:]
    String customBranchOverride = args.customBranchOverride // Kept for backward compatibility
    // Optional parameters for release pipeline (releaseTag → tag first, then build from tag only)
    String buildChannel = args.buildChannel
    String releaseTag = args.releaseTag
    // Optional: pre-resolved tag per module (release pipeline + allowlist); overrides releaseTag per key when set
    Map releaseTagByModule = args.containsKey('releaseTagByModule') ? (args.releaseTagByModule as Map) : null
    // Release pipeline only: parallel installer JDK branches do not fail the job; finish all branches then UNSTABLE if any failed.
    boolean markUnstableOnFailure = args.markUnstableOnFailure == true
    Map pipelineCfg = args.containsKey('pipelineCfg') ? (args.pipelineCfg as Map) : null
    // Job-level gate: vars/runSecurityToolsParams.groovy — null ⇒ true (same as before).
    def runSecurityToolsParams = loadSharedLibVarScript('runSecurityToolsParams')
    if (!runSecurityToolsParams) {
        error '❌ runSecurityToolsParams could not be loaded from the shared library.'
    }
    boolean runSecurityTools = runSecurityToolsParams.resolveJobRunSecurityTools(args.runSecurityTools)

    if (selectedModulesListFromParams == null || selectedModulesListFromParams.isEmpty()) {
        return
    }

    def modulesToBuild = selectedModulesListFromParams
    def jdksToUse = jdkVersions.split(',').collect { it.trim() }.findAll { it }

    def parallelExecutionMap = [:]
    def allModuleJdkResults = [:]

    modulesToBuild.each { moduleName ->
        jdksToUse.each { jdk ->
            def currentJdkVersion = jdk.trim()
            def nestedStageName = "${moduleName} (JDK ${currentJdkVersion})"

            parallelExecutionMap[nestedStageName] = {
                stage(nestedStageName) {
                    node {
                        def displayBranch = (moduleBranchMap[moduleName] ?: customBranchOverride)?.toString()?.trim()
                        if (!displayBranch || displayBranch == 'null') {
                            error "❌ Branch or tag required for installer module '${moduleName}'. Set INST_BRANCH_CHOICES (module:branch) or customBranchOverride."
                        }
                        def cxSmgDevBase = env.CX_SMG_DEV?.toString()?.trim()
                        if (!cxSmgDevBase) {
                            error "❌ CX_SMG_DEV must be set in Jenkins global environment variables (Manage Jenkins → Configure System)."
                        }
                        def wsPathForModuleJdk = "${cxSmgDevBase}/${moduleName}-${displayBranch}-${currentJdkVersion}"

                        ws(wsPathForModuleJdk) {
                            try {
                                def jdkToolName = loadSharedLibVarScript('getJdkTool').call(currentJdkVersion)
                                def jdkHome = tool name: jdkToolName, type: 'jdk'

                                def effectiveReleaseTag = (releaseTagByModule != null && releaseTagByModule.containsKey(moduleName))
                                    ? releaseTagByModule[moduleName]?.toString()?.trim()
                                    : releaseTag?.toString()?.trim()
                                boolean useReleaseTag = (effectiveReleaseTag ?: '') != ''
                                def buildScript = loadSharedLibVarScript(useReleaseTag ? 'buildModulesWithJDKReleaseTag' : 'buildModulesWithJDK')
                                if (!buildScript) {
                                    error "❌ Failed to load build script '${useReleaseTag ? 'buildModulesWithJDKReleaseTag' : 'buildModulesWithJDK'}'. Pipeline must fail when the module build cannot run (e.g. script/compilation error)."
                                }
                                def reactCiResolver = loadSharedLibVarScript('resolveReactCiEnv')
                                String reactCiExport = reactCiResolver ? reactCiResolver.effective(pipelineCfg) : 'false'
                                def jdkEnv = [
                                    "JAVA_HOME=${jdkHome}",
                                    "PATH+JDK=${jdkHome}/bin",
                                    "CI=${reactCiExport}",
                                    "CX_SMG_NPM_CI=true",
                                    "CX_SMG_DEV=${wsPathForModuleJdk}"
                                ]
                                withEnv(jdkEnv) {
                                    if (useReleaseTag) {
                                        buildScript.call(moduleName, currentJdkVersion, wsPathForModuleJdk, cxSmgBase, isCoreType, displayBranch, effectiveReleaseTag, runSecurityTools, pipelineCfg)
                                    } else {
                                        buildScript.call(moduleName, currentJdkVersion, wsPathForModuleJdk, cxSmgBase, isCoreType, displayBranch, runSecurityTools, pipelineCfg)
                                    }
                                }

                                allModuleJdkResults["${moduleName}-${currentJdkVersion}"] = 'SUCCESS'
                            } catch (Throwable err) {
                                def rk = "${moduleName}-${currentJdkVersion}"
                                allModuleJdkResults[rk] = 'FAILURE'
                                echo "❌ Parallel installer build failed for ${rk}: ${err.message}"
                                if (markUnstableOnFailure) {
                                    // Let sibling parallel branches continue; aggregate UNSTABLE after parallel().
                                } else {
                                    if (currentBuild.result != 'ABORTED') {
                                        currentBuild.result = 'FAILURE'
                                    }
                                    throw err
                                }
                            } finally {
                                cleanWs(deleteDirs: true, disableDeferredWipeout: true, notFailBuild: true)
                            }
                        }
                    }
                }
            }
        }
    }

    if (!parallelExecutionMap.isEmpty()) {
        def parallelOpts = new LinkedHashMap()
        parallelOpts.failFast = false
        parallelOpts.putAll(parallelExecutionMap)
        parallel(parallelOpts)

        if (markUnstableOnFailure) {
            boolean anyFail = allModuleJdkResults.any { _k, v -> v == 'FAILURE' }
            if (anyFail) {
                if (currentBuild.result != 'ABORTED' && currentBuild.result != 'FAILURE') {
                    currentBuild.result = 'UNSTABLE'
                }
                echo "⚠️ Release parallel installers: one or more module/JDK cells failed — job result UNSTABLE (remaining installer branches were allowed to finish)."
            }
        }
    }
}