// vars/buildModulesWithJDKReleaseTag.groovy
//
// EXACT REPLICA OF: CustomerXP buildModulesWithJDKReleaseTag.groovy
//
// PURPOSE:
// Release-tag build variant of buildModulesWithJDK.
// Used by release-cipipeline.jenkinsfile when RELEASE_TAG is specified.
// Difference from snapshot build:
//   - Creates a Git tag on the branch before building
//   - Runs with release-specific Maven goals (no -SNAPSHOT suffix)
//   - Security tools optional (disabled by default for release)
//
// SIGNATURE matches CustomerXP exactly:
//   call(moduleName, jdkVersion, workspacePath, cxSmgBase,
//        isCoreType, customBranchOverride, releaseTag,
//        runSecurityTools, pipelineCfg)

def call(String moduleName,
         String jdkVersion,
         String workspacePath,
         String cxSmgBase,
         boolean isCoreType,
         String customBranchOverride,
         String releaseTag,
         Boolean runSecurityTools = false,
         Map pipelineCfg = null) {

    if (!moduleName) error "❌ buildModulesWithJDKReleaseTag: moduleName is required."
    if (!releaseTag?.trim()) error "❌ buildModulesWithJDKReleaseTag: releaseTag is required."

    def runSecurityToolsParams = loadSharedLibVarScript('runSecurityToolsParams')
    boolean jobRunSecurityTools = runSecurityToolsParams ?
        runSecurityToolsParams.resolveJobRunSecurityTools(runSecurityTools) : false

    // Resolve config
    def serde = loadSharedLibVarScript('pipelineConfigSerde')
    def cfg
    if (pipelineCfg != null) {
        cfg = new LinkedHashMap(pipelineCfg as Map)
    } else if (env.CX_PIPELINE_CFG_JSON?.toString()?.trim() && serde != null) {
        cfg = serde.pipelineConfigFromJson(env.CX_PIPELINE_CFG_JSON.toString())
    }
    if (cfg == null) cfg = cxPipelineConfig(false)

    def moduleSpecialCasesScript = loadSharedLibVarScript('moduleSpecialCases')
    String repoUrl = moduleSpecialCasesScript.getRepoUrl(moduleName, cfg)
    String branch  = customBranchOverride?.trim() ?: moduleSpecialCasesScript.getDefaultBranch(moduleName)
    String normalizedTag = normalizeNexusTagVersion(releaseTag)

    echo "⚙️ [RELEASE TAG] Building ${moduleName} (${jdkVersion})"
    echo "   Branch: ${branch}, Tag: ${releaseTag} → normalized: ${normalizedTag}"
    echo "   Repo: ${repoUrl}"

    // Clone
    echo "📥 Cloning ${moduleName} @ ${branch}..."
    dir(workspacePath) {
        deleteDir()
        git branch: branch, url: repoUrl, changelog: false, poll: false
        echo "✅ Cloned ${moduleName} @ ${branch}"
    }

    // Tag build script (CustomerXP's tagRebuildBuildScript equivalent)
    def tagScript = loadSharedLibVarScript('tagRebuildBuildScript')
    if (tagScript) {
        dir(workspacePath) {
            def script = tagScript.render([
                moduleName:     moduleName,
                jdkVersion:     jdkVersion,
                branch:         branch,
                releaseTag:     releaseTag,
                normalizedTag:  normalizedTag,
                buildTypeSuffix: '',   // release — no -SNAPSHOT
                nexusUrl:       cfg.nexusBaseUrl ?: '',
                isCoreType:     isCoreType,
                pipelineDebug:  cfg.m2DebugExplicit == 'true'
            ])
            writeFile file: 'run_build_from_tag.sh', text: script
            sh 'chmod +x run_build_from_tag.sh && bash run_build_from_tag.sh'
        }
    } else {
        // Fallback: use main build script with release settings
        def mainBuildSonarRenderer = loadSharedLibVarScript('mainBuildAndSonarScript')
        dir(workspacePath) {
            def script = mainBuildSonarRenderer.render([
                moduleName:     moduleName,
                jdkVersion:     jdkVersion,
                branch:         branch,
                buildTypeSuffix: '',
                nexusUrl:       cfg.nexusBaseUrl ?: '',
                pipelineDebug:  false,
                isCoreType:     isCoreType,
                runSonar:       false
            ])
            writeFile file: 'run_build.sh', text: script
            sh 'chmod +x run_build.sh && bash run_build.sh'
        }
    }

    // Security tools (disabled by default for release builds in CustomerXP)
    if (jobRunSecurityTools) {
        def securityOrchestrator = loadSharedLibVarScript('securityToolsOrchestrator')
        if (securityOrchestrator) {
            dir(workspacePath) {
                securityOrchestrator.runTagRebuildSecurityTooling([
                    moduleName:       moduleName,
                    moduleBranch:     branch,
                    releaseTag:       releaseTag,
                    jdkVersion:       jdkVersion,
                    workspacePath:    workspacePath,
                    cfg:              cfg,
                    runSecurityTools: jobRunSecurityTools
                ])
            }
        }
    }

    echo "✅ buildModulesWithJDKReleaseTag complete: ${moduleName} tag=${releaseTag}"
}

/**
 * Normalize a release tag for Nexus versioning.
 * Strips leading v/V, replaces non-alphanumeric with dash.
 * e.g. "v1.2.3" → "1.2.3", "release/1.0" → "release-1.0"
 * EXACT REPLICA of CustomerXP's normalizeNexusTagVersion()
 */
static String normalizeNexusTagVersion(String tagName) {
    if (!tagName) return tagName
    def normalized = tagName.replaceAll(/^[vV]/, '')
    normalized = normalized.replaceAll(/[^a-zA-Z0-9.\-_]/, '-')
    return normalized
}
