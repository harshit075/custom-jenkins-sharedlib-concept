/**
 * Orchestrates a chain build process with dynamic branch support.
 */
def call(Map args) {
    List<String> selectedModulesListFromParams = args.selectedModulesList
    String jdkVersions = args.jdkVersions
    String cxSmgBase = args.cxSmgBase
    boolean isCoreType = args.isCoreType
    // NEW: Capture the dynamic branch override passed from Main Jenkinsfile
    String customBranchOverride = args.customBranchOverride
    // Optional: Release tag — routes to buildModulesWithJDKReleaseTag (release pipeline only)
    String releaseTag = args.releaseTag
    Map pipelineCfg = args.containsKey('pipelineCfg') ? (args.pipelineCfg as Map) : null
    // Job-level gate: vars/runSecurityToolsParams.groovy — null ⇒ true (same as before).
    def runSecurityToolsParams = loadSharedLibVarScript('runSecurityToolsParams')
    if (!runSecurityToolsParams) {
        error '❌ runSecurityToolsParams could not be loaded from the shared library.'
    }
    boolean runSecurityTools = runSecurityToolsParams.resolveJobRunSecurityTools(args.runSecurityTools)

    if (selectedModulesListFromParams == null || selectedModulesListFromParams.isEmpty()) {
        error "❌ Chain Build received no modules to process."
    }

    // --- 1. Resolve Canonical order ---
    def canonicalCoreModulesOrder = []
    def resolveChainBuildModulesScript = loadSharedLibVarScript('resolveChainBuildModules')
    canonicalCoreModulesOrder = resolveChainBuildModulesScript.call(selectedModulesListFromParams.join(','))

    def jdks = jdkVersions.split(',').collect { it.trim() }.findAll { it }
    def moduleBuildResults = [:]

    for (String module : canonicalCoreModulesOrder) {
        def currentModuleName = module.trim()
        
        if (!selectedModulesListFromParams.contains(currentModuleName)) {
            continue
        }

        def parallelJdkBuilds = [:]
        def currentModuleJdkResults = [:]

        jdks.each { jdk ->
            def currentJdkVersion = jdk.trim()
            def nestedStageName = "${currentModuleName} (JDK ${currentJdkVersion})"

            parallelJdkBuilds[nestedStageName] = {
                // No catch{} inside ws — Groovy 2 on Jenkins can fail parse with } catch (Throwable) after nested ws/try. Use try/finally only; failures skip SUCCESS so jdks.each marks FAILURE.
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    stage(nestedStageName) {
                        node {
                            def displayBranch = customBranchOverride?.toString()?.trim()
                            if (!displayBranch || displayBranch == 'null') {
                                currentModuleJdkResults[currentJdkVersion] = 'FAILURE'
                                error "❌ customBranchOverride (branch or tag) is required for chain build module '${currentModuleName}'."
                            }
                            def cxSmgDevBase = env.CX_SMG_DEV?.toString()?.trim()
                            if (!cxSmgDevBase) {
                                currentModuleJdkResults[currentJdkVersion] = 'FAILURE'
                                error "❌ CX_SMG_DEV must be set in Jenkins global environment variables (Manage Jenkins → Configure System)."
                            }
                            def wsPathForModuleJdk = "${cxSmgDevBase}/${currentModuleName}-${displayBranch}-${currentJdkVersion}"

                            ws(wsPathForModuleJdk) {
                                try {
                                    def jdkToolName = loadSharedLibVarScript('getJdkTool').call(currentJdkVersion)
                                    def jdkHome = tool name: jdkToolName, type: 'jdk'

                                    boolean useReleaseTag = (releaseTag?.toString()?.trim() ?: '') != ''
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
                                            buildScript.call(currentModuleName, currentJdkVersion, wsPathForModuleJdk, cxSmgBase, isCoreType, customBranchOverride, releaseTag?.toString()?.trim(), runSecurityTools, pipelineCfg)
                                        } else {
                                            buildScript.call(currentModuleName, currentJdkVersion, wsPathForModuleJdk, cxSmgBase, isCoreType, customBranchOverride, runSecurityTools, pipelineCfg)
                                        }
                                    }

                                    echo "✅ Module '${currentModuleName}' built successfully on ${displayBranch} (${currentJdkVersion})."
                                    currentModuleJdkResults[currentJdkVersion] = 'SUCCESS'
                                } finally {
                                    cleanWs(deleteDirs: true, disableDeferredWipeout: true, notFailBuild: true)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!parallelJdkBuilds.isEmpty()) {
            def parallelOpts = new LinkedHashMap()
            parallelOpts.failFast = false
            parallelOpts.putAll(parallelJdkBuilds)
            parallel(parallelOpts)
        }

        jdks.each { jdkLabel ->
            def v = jdkLabel.trim()
            if (!currentModuleJdkResults.containsKey(v)) {
                currentModuleJdkResults[v] = 'FAILURE'
                echo "⚠️ No result recorded for JDK ${v} (treating as FAILURE)."
            }
        }

        def successCount = currentModuleJdkResults.count { k, v -> v == 'SUCCESS' }
        def failureCount = currentModuleJdkResults.count { k, v -> v == 'FAILURE' }
        moduleBuildResults[currentModuleName] = [success: successCount, total: jdks.size(), failures: failureCount]

        if (failureCount > 0 || successCount < jdks.size()) {
            if (currentBuild.result != 'ABORTED') {
                currentBuild.result = 'FAILURE'
            }
            error("❌ Chain build: one or more JDK builds failed for module '${currentModuleName}' (${failureCount}/${jdks.size()} failed). Results: ${currentModuleJdkResults}")
        }
    }
}