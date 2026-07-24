    // vars/buildModulesWithJDKReleaseTag.groovy
    //
    // Release-pipeline only: tag release-branch HEAD in GitLab, then SMG build from {@code run_build_from_tag.sh}.
    // Snapshot / branch builds use {@code buildModulesWithJDK.groovy} — this script is never loaded for final-ci.

    /**
     * @param releaseTag Non-empty Git tag to create on the release-branch HEAD (e.g. v4.1.56).
     * @param pipelineCfg Same Option A semantics as {@code buildModulesWithJDK}.
     */
    def call(String moduleName, String jdkVersion, String workspacePath, String cxSmgBase, boolean isCoreType, String customBranchOverride, String releaseTag, Boolean runSecurityTools = true, Map pipelineCfg = null) {
        def runSecurityToolsParams = loadSharedLibVarScript('runSecurityToolsParams')
        if (!runSecurityToolsParams) {
            error '❌ runSecurityToolsParams could not be loaded from the shared library.'
        }
        boolean jobRunSecurityTools = runSecurityToolsParams.resolveJobRunSecurityTools(runSecurityTools)
        if (!moduleName) { error "❌ No module name provided." }
        if (!releaseTag?.toString()?.trim()) {
            error '❌ buildModulesWithJDKReleaseTag: releaseTag is required. Use buildModulesWithJDK for snapshot/branch-only builds.'
        }
        def getDefaultsScript = loadSharedLibVarScript('getDefaults')
        boolean globalSecurityTools = getDefaultsScript != null && getDefaultsScript.getRunSecurityTools()
        boolean effectiveRunSecurityTools = globalSecurityTools && jobRunSecurityTools
        def serde = loadSharedLibVarScript('pipelineConfigSerde')
        def cfg
        if (pipelineCfg != null) {
            cfg = new LinkedHashMap(pipelineCfg as Map)
            echo 'ℹ️ Using injected pipelineCfg (cxPipelineConfig skipped for this module/JDK cell).'
        } else if (env.CX_PIPELINE_CFG_JSON?.toString()?.trim() && serde != null) {
            cfg = serde.pipelineConfigFromJson(env.CX_PIPELINE_CFG_JSON.toString())
            if (cfg != null) {
                echo 'ℹ️ Using CX_PIPELINE_CFG_JSON from Validate stage (cxPipelineConfig skipped for this module/JDK cell).'
            }
        }
        if (cfg == null) {
            cfg = cxPipelineConfig(effectiveRunSecurityTools)
        }
        def reactCiResolver = loadSharedLibVarScript('resolveReactCiEnv')
        String reactCiExport = cfg.reactCiExport?.toString()?.trim()
        if (!(reactCiExport in ['true', 'false']) && reactCiResolver) {
            reactCiExport = reactCiResolver.effective(cfg)
        }
        if (!(reactCiExport in ['true', 'false'])) {
            reactCiExport = 'false'
        }
        boolean pipelineVerbose = cfg.m2DebugExplicit == 'true'
        echo "⚙️ RELEASE-TAG build: ${moduleName} (${jdkVersion}) tag=${releaseTag.trim()}${isCoreType ? ' core' : ''}"

        def secCat = loadSharedLibVarScript('securityToolsCatalog')
        boolean useCycloneDxSbom = secCat == null || secCat.isToolEffective('cyclonedx_sbom', effectiveRunSecurityTools)
        boolean useJacocoCatalog = secCat == null || secCat.isToolEffective('jacoco', effectiveRunSecurityTools)

        def toolSbomJacoco = loadSharedLibVarScript('toolSbomJacoco')
        def toolSonarqube = loadSharedLibVarScript('toolSonarqube')
        def securityOrchestrator = loadSharedLibVarScript('securityToolsOrchestrator')
        if (!toolSbomJacoco || !toolSonarqube || !securityOrchestrator) {
            error '❌ toolSbomJacoco, toolSonarqube, or securityToolsOrchestrator could not be loaded from the shared library.'
        }
        def tagRebuildRenderer = loadSharedLibVarScript('tagRebuildBuildScript')
        if (!tagRebuildRenderer) { error "❌ tagRebuildBuildScript not found in shared library." }
        def nexusUrls = loadSharedLibVarScript('nexusUploadUrls')
        if (!nexusUrls) { error "❌ nexusUploadUrls not found in shared library." }
        def moduleSpecialCases = loadSharedLibVarScript('moduleSpecialCases')
        if (!moduleSpecialCases) { error "❌ moduleSpecialCases not found in shared library." }
        def sbomDiscovery = loadSharedLibVarScript('sbomDiscovery')
        if (!sbomDiscovery) { error "❌ sbomDiscovery not found in shared library." }

        def gitHost = cfg.gitHost
        def resolveCxSmgDevPathScript = loadSharedLibVarScript('resolveCxSmgDevPath')
        def dbg = loadSharedLibVarScript('debug')
        if (!dbg) { error "❌ debug helper not found in shared library." }
        def cxSmgDevResolved = resolveCxSmgDevPathScript.call(cxSmgBase)
        dbg.pipelineLog(pipelineVerbose, "📌 SMG CX_SMG_DEV resolved to: ${cxSmgDevResolved} (raw CX_SMG_DEV='${env.CX_SMG_DEV}')")

        withCredentials([
            usernamePassword(credentialsId: cfg.gitHttpCredentialId, usernameVariable: 'G_USR', passwordVariable: 'G_PWD'),
            usernamePassword(credentialsId: cfg.nexusCredentialId, usernameVariable: 'N_USR', passwordVariable: 'N_PWD')
        ]) {
            def netrcScript = """echo "machine ${gitHost} login ${G_USR} password ${G_PWD}" > ~/.netrc && chmod 600 ~/.netrc"""
            sh netrcScript

            try {
                dir(workspacePath) {
                    deleteDir()

                    withEnv(["PIPELINE_DEBUG=${cfg.m2DebugExplicit}"]) {

                    if (!customBranchOverride?.toString()?.trim() || customBranchOverride.toString().trim() == 'null') {
                        error "❌ customBranchOverride (release branch) is required."
                    }
                    def moduleBranch = customBranchOverride.toString().trim()
                    dbg.pipelineLog(pipelineVerbose, "🔗 Release branch: ${moduleBranch}, tag to push: ${releaseTag.trim()}")

                    def gitOrgForDiscover = cfg.gitOrg?.toString()?.trim() ?: env.CX_GIT_ORG?.toString()?.trim()
                    if (!gitOrgForDiscover) {
                        error '❌ Git org is not configured. Set JENKINS_GIT_ORG or CX_GIT_ORG (Jenkins global or job environment).'
                    }
                    def namespaceScript = moduleSpecialCases.buildDiscoverNamespaceScript(gitHost, gitOrgForDiscover, moduleName)
                    writeFile file: "discover.sh", text: namespaceScript
                    def namespace = sh(script: "bash discover.sh", returnStdout: true).trim()

                    def cloneUrl = "https://${gitHost}/${gitOrgForDiscover}/${namespace}/${moduleName}.git"
                    sh """
                        git init
                        git remote add origin ${cloneUrl}
                        if git fetch origin refs/heads/${moduleBranch}:refs/remotes/origin/${moduleBranch} 2>/dev/null; then
                            if [ "\${PIPELINE_DEBUG}" = "true" ]; then echo "✅ Fetched as branch: ${moduleBranch}"; fi
                            git checkout -f refs/remotes/origin/${moduleBranch}
                        elif git fetch origin refs/tags/${moduleBranch}:refs/tags/${moduleBranch} 2>/dev/null; then
                            if [ "\${PIPELINE_DEBUG}" = "true" ]; then echo "✅ Fetched as tag: ${moduleBranch}"; fi
                            git checkout -f refs/tags/${moduleBranch}
                        else
                            git fetch origin ${moduleBranch}
                            git checkout -f FETCH_HEAD
                        fi
                    """

                    def cleanJdk = jdkVersion.replace('jdk-', '')
                    def buildTypeSuffix = cfg.buildTypeSuffix

                    dbg.echoM2PipelineContext(moduleName, jdkVersion, buildTypeSuffix, releaseTag, cfg.m2DebugExplicit == 'true')

                    def relativeSbomPath = sbomDiscovery.getRelativeSbomPath(moduleName)

                    def cxGitOrgSeg = env.CX_GIT_ORG?.toString()?.trim() ?: cfg.gitOrg?.toString()?.trim()
                    if (!cxGitOrgSeg) {
                        error '❌ Git org is not configured. Set JENKINS_GIT_ORG or CX_GIT_ORG (Jenkins global or job environment).'
                    }
                    def cxGitOrgSmg = env.CX_GIT_ORG?.toString()?.trim() ?: cxGitOrgSeg
                    def ccInstallerBranchSuffix = env.BUILD_NUMBER?.toString()?.trim() ?: '0'
                    def bashCommand = (env.JENKINS_CD_BASH_X?.toString() == 'true') ? 'bash -x' : 'bash'
                    def javaSetupBlock = """
                            if [ -n "\${JAVA_HOME:-}" ] && [ -x "\${JAVA_HOME}/bin/java" ]; then
                                export PATH="\${JAVA_HOME}/bin:\${PATH}"
                            elif [ -n "\$JDK_${cleanJdk}_HOME" ] && [ -x "\$JDK_${cleanJdk}_HOME/bin/java" ]; then
                                export JAVA_HOME="\$JDK_${cleanJdk}_HOME"
                                export PATH="\${JAVA_HOME}/bin:\${PATH}"
                            elif command -v java >/dev/null 2>&1; then
                                _J="\$(command -v java)"
                                _R="\$(readlink -f "\$_J" 2>/dev/null || readlink "\$_J" 2>/dev/null || echo "\$_J")"
                                export JAVA_HOME="\$(dirname "\$(dirname "\$_R")")"
                                export PATH="\${JAVA_HOME}/bin:\${PATH}"
                            fi
                            export JDK_${cleanJdk}_HOME="\${JAVA_HOME}"
                            if [ "\${PIPELINE_DEBUG:-false}" = "true" ]; then echo "Build environment: JAVA_HOME=\${JAVA_HOME:-}"; fi
"""
                    def preserveJdkBeforeBashrc = """
                            _CX_PIPELINE_JAVA_HOME="\${JAVA_HOME:-}"
                            . "\${HOME}/.bashrc" || true
                            if [ -n "\${_CX_PIPELINE_JAVA_HOME}" ] && [ -x "\${_CX_PIPELINE_JAVA_HOME}/bin/java" ]; then
                                export JAVA_HOME="\${_CX_PIPELINE_JAVA_HOME}"
                            fi
"""
                    def packInstallerCmd = '''bash "${CX_SMG_BASE}/bin/pack-module.sh"'''
                    def nexusBaseUrl = cfg.nexusBaseUrl

                    dbg.pipelineLog(pipelineVerbose, "⏭️ RELEASE (${moduleName}): skipping normal branch build; pushing tag then building release branch.")

                    def releaseBranchName = moduleBranch.trim()
                    def tagBuildSecurityTools = false
                    def tagName = releaseTag.trim()

                    dbg.pipelineLog(pipelineVerbose, "--- PHASE: Creating and Pushing Release Tag ---")
                    sh """
                        git config user.name "Jenkins"
                        git config user.email "jenkins@customerxps.com"
                        git tag -a "${tagName}" -m "Release tag ${tagName} created by Jenkins build ${env.BUILD_NUMBER}"
                        git push origin "${tagName}" || echo "⚠️ Tag ${tagName} may already exist, continuing..."
                    """

                    dbg.pipelineLog(pipelineVerbose, "✅ Tag ${tagName} created and pushed to GitLab")
                    dbg.pipelineLog(pipelineVerbose, "--- PHASE: Building release branch ${releaseBranchName} (same commit as ${tagName}) ---")

                    sh """
                        git fetch origin refs/heads/${releaseBranchName}:refs/remotes/origin/${releaseBranchName} 2>/dev/null || true
                        git checkout -f refs/remotes/origin/${releaseBranchName}
                    """

                    def sonarCmdForTag = tagBuildSecurityTools ? getSonarCommand(moduleName, releaseBranchName) : ""

                    def normalizedTagForNexus = normalizeNexusTagVersion(tagName)
                    def nexusPathDirForTag = "jdk${cleanJdk}-${normalizedTagForNexus}"

                    dbg.pipelineLog(pipelineVerbose, "✅ On release branch ${releaseBranchName}, regenerating build script (tag ${tagName} in GitLab)...")

                    def compilationBlockTag = toolSbomJacoco.getCompilationBlockTag(tagBuildSecurityTools, useCycloneDxSbom, useJacocoCatalog, releaseBranchName, tagName, moduleName, true)
                    def sonarBlockTag = toolSonarqube.getSonarBlockTag(tagBuildSecurityTools, sonarCmdForTag, releaseBranchName)

                    withEnv([
                        "SMG_MODULE=${moduleName}",
                        "SMG_BRANCH_NAME=${releaseBranchName}",
                        "TARGET_NAMESPACE=${namespace}",
                        "SBOM_FILE=${relativeSbomPath}",
                        "BUILD_TYPE_SUFFIX=${buildTypeSuffix}",
                        "M2_DEBUG=${cfg.m2DebugExplicit}",
                        "PIPELINE_DEBUG=${cfg.m2DebugExplicit}",
                        "NEXUS_PATH_DIR=${nexusPathDirForTag}",
                        "CLEAN_JDK=${cleanJdk}"
                    ]) {
                        def rebuildScriptForTag = tagRebuildRenderer.render([
                            preserveJdkBeforeBashrc      : preserveJdkBeforeBashrc,
                            cxSmgBase                    : cxSmgBase,
                            cxSmgDevResolved             : cxSmgDevResolved,
                            javaSetupBlock               : javaSetupBlock,
                            cxGitOrg                     : cxGitOrgSmg,
                            cxGitOrgSeg                  : cxGitOrgSeg,
                            dbg                          : dbg,
                            compilationBlockTag          : compilationBlockTag,
                            sonarBlockTag                : sonarBlockTag,
                            isCoreType                   : isCoreType,
                            moduleName                   : moduleName,
                            namespace                    : namespace,
                            nexusBaseUrl                 : nexusBaseUrl,
                            releaseBranchName            : releaseBranchName,
                            tagName                      : tagName,
                            nexusPathDirForTag           : nexusPathDirForTag,
                            nexusUploadPrefix            : nexusUrls.nexusPackageImagesUploadUrlPrefix(nexusBaseUrl, moduleName, nexusPathDirForTag),
                            ccInstallerBranchSuffix      : ccInstallerBranchSuffix,
                            packInstallerCmd             : packInstallerCmd,
                            nexusUser                    : N_USR,
                            nexusPassword                : N_PWD,
                            reactCiExport                : reactCiExport
                        ])
                        writeFile file: "run_build_from_tag.sh", text: rebuildScriptForTag

                        securityOrchestrator.runTagRebuildSecurityTooling(
                            bashCommand            : bashCommand,
                            tagBuildSecurityTools  : tagBuildSecurityTools,
                            cfg                    : cfg,
                            moduleName             : moduleName,
                            releaseBranchName      : releaseBranchName,
                            tagName                : tagName,
                            jdkVersion             : jdkVersion,
                            namespace              : namespace,
                            qgTimeoutMinutes       : 10
                        )

                        def nexusLogUrlRelease
                        if (isCoreType) {
                            nexusLogUrlRelease = "${(nexusBaseUrl ?: '').toString().trim().replaceAll(/\/+$/, '')}/"
                        } else {
                            boolean ccCre = nexusUrls.nexusCxCreinstallerModule(moduleName, env.CX_CREINSTALLER_MODULES?.toString())
                            def instBase = nexusUrls.nexusPackageImagesInstallerBasename(moduleName, cleanJdk, nexusPathDirForTag, ccCre)
                            nexusLogUrlRelease = nexusUrls.nexusPackageImagesInstallerFileUrl(nexusBaseUrl, moduleName, nexusPathDirForTag, instBase)
                        }
                        echo "${moduleName}: ${nexusLogUrlRelease}"

                        dbg.pipelineLog(pipelineVerbose, "✅ Module '${moduleName}' release build finished (branch ${releaseBranchName}, tag ${tagName} in GitLab)")
                    }
                    } // withEnv PIPELINE_DEBUG
                }
            } finally {
                sh "rm -f ~/.netrc"
            }
        }
    }

    def normalizeNexusTagVersion(String tagName) {
        def t = tagName?.toString()?.trim()
        if (!t) {
            error('❌ normalizeNexusTagVersion: tag name must be set.')
        }
        if (t.startsWith('v') || t.startsWith('V')) {
            t = t.substring(1)
        }
        t = t.replaceAll('\\s+', '')
        t = t.replaceAll('[^a-zA-Z0-9.\\-]', '-')
        return t
    }
