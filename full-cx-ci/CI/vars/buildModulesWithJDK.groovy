// vars/buildModulesWithJDK.groovy
//
// EXACT REPLICA OF: CustomerXP buildModulesWithJDK.groovy
//
// PURPOSE:
// THE CORE BUILD FUNCTION. Called once per module per JDK version.
// Executes the full build pipeline for a single module:
//   1. Resolve pipeline config (from injected Map or CX_PIPELINE_CFG_JSON)
//   2. Resolve workspace path via resolveCxSmgDevPath
//   3. Discover module repo URL via moduleSpecialCases
//   4. Clone the repo from Git
//   5. Generate run_build.sh via mainBuildAndSonarScript
//   6. Execute run_build.sh
//   7. Simulate Nexus upload via nexusUploadUrls
//   8. Run security toolchain via securityToolsOrchestrator
//
// SIGNATURE matches CustomerXP exactly:
//   call(moduleName, jdkVersion, workspacePath, cxSmgBase,
//        isCoreType, customBranchOverride, runSecurityTools, pipelineCfg)

def call(String moduleName,
         String jdkVersion,
         String workspacePath,
         String cxSmgBase,
         boolean isCoreType,
         String customBranchOverride = null,
         Boolean runSecurityTools = true,
         Map pipelineCfg = null) {

    // ── 1. Load runSecurityToolsParams ───────────────────────────────────────
    def runSecurityToolsParams = loadSharedLibVarScript('runSecurityToolsParams')
    if (!runSecurityToolsParams) error '❌ runSecurityToolsParams could not be loaded.'
    boolean jobRunSecurityTools = runSecurityToolsParams.resolveJobRunSecurityTools(runSecurityTools)

    if (!moduleName) error "❌ buildModulesWithJDK: moduleName is required."

    // ── 2. Resolve pipeline config ───────────────────────────────────────────
    def getDefaultsScript = loadSharedLibVarScript('getDefaults')
    boolean globalSecurityTools = getDefaultsScript != null && getDefaultsScript.getRunSecurityTools()
    boolean effectiveRunSecurityTools = globalSecurityTools && jobRunSecurityTools

    def serde = loadSharedLibVarScript('pipelineConfigSerde')
    def cfg
    if (pipelineCfg != null) {
        cfg = new LinkedHashMap(pipelineCfg as Map)
        echo "ℹ️ Using injected pipelineCfg (skipping cxPipelineConfig re-validation)"
    } else if (env.CX_PIPELINE_CFG_JSON?.toString()?.trim() && serde != null) {
        cfg = serde.pipelineConfigFromJson(env.CX_PIPELINE_CFG_JSON.toString())
        if (cfg != null) {
            echo "ℹ️ Using CX_PIPELINE_CFG_JSON (Option A — skipping re-validation)"
        }
    }
    if (cfg == null) {
        cfg = cxPipelineConfig(effectiveRunSecurityTools)
    }

    boolean pipelineVerbose = cfg.m2DebugExplicit == 'true'
    def dbg = loadSharedLibVarScript('debug')

    // ── 3. Resolve React CI env ──────────────────────────────────────────────
    def reactCiResolver = loadSharedLibVarScript('resolveReactCiEnv')
    String reactCiExport = reactCiResolver ? reactCiResolver.effective(cfg) : 'false'

    // ── 4. Load required scripts ─────────────────────────────────────────────
    def moduleSpecialCasesScript = loadSharedLibVarScript('moduleSpecialCases')
    if (!moduleSpecialCasesScript) error '❌ moduleSpecialCases not found.'
    def mainBuildSonarRenderer = loadSharedLibVarScript('mainBuildAndSonarScript')
    if (!mainBuildSonarRenderer) error '❌ mainBuildAndSonarScript not found.'
    def nexusUrls = loadSharedLibVarScript('nexusUploadUrls')
    if (!nexusUrls) error '❌ nexusUploadUrls not found.'
    def securityOrchestrator = loadSharedLibVarScript('securityToolsOrchestrator')
    if (!securityOrchestrator) error '❌ securityToolsOrchestrator not found.'

    // ── 5. Resolve repo URL and namespace ────────────────────────────────────
    String repoUrl   = moduleSpecialCasesScript.getRepoUrl(moduleName, cfg)
    String namespace = moduleSpecialCasesScript.getNamespace(moduleName, cfg.gitOrg)
    String branch    = customBranchOverride?.trim() ?: moduleSpecialCasesScript.getDefaultBranch(moduleName)

    if (pipelineVerbose && dbg) {
        dbg.pipelineLog(true, "buildModulesWithJDK: module=${moduleName}, jdk=${jdkVersion}, branch=${branch}, core=${isCoreType}, security=${effectiveRunSecurityTools}")
        dbg.echoM2PipelineContext(moduleName, jdkVersion, cfg.buildTypeSuffix ?: '-SNAPSHOT')
    }

    echo "⚙️ Building ${moduleName} (${jdkVersion})${isCoreType ? ' [core]' : ' [installer]'}"
    echo "   Repo: ${repoUrl} @ ${branch}"
    echo "   Workspace: ${workspacePath}"
    echo "   SecurityTools: ${effectiveRunSecurityTools}"

    // ── 6. Clone repository ───────────────────────────────────────────────────
    echo "📥 Cloning ${moduleName}..."
    dir(workspacePath) {
        deleteDir()
        git branch: branch, url: repoUrl, changelog: false, poll: false
        echo "✅ Cloned ${moduleName} @ ${branch}"
    }

    // ── 7. Generate and execute run_build.sh ──────────────────────────────────
    dir(workspacePath) {
        echo "🔨 Generating run_build.sh for ${moduleName}..."

        def buildScript = mainBuildSonarRenderer.render([
            moduleName:     moduleName,
            jdkVersion:     jdkVersion,
            branch:         branch,
            buildTypeSuffix: cfg.buildTypeSuffix ?: '-SNAPSHOT',
            nexusUrl:       cfg.nexusBaseUrl ?: '',
            sonarUrl:       cfg.sonarBaseUrl ?: '',
            sonarProjectKey: "${moduleName}-${branch}".replaceAll('[^a-zA-Z0-9_\\-:]', '-'),
            pipelineDebug:  pipelineVerbose,
            isCoreType:     isCoreType,
            runSonar:       effectiveRunSecurityTools && cfg.sonarBaseUrl
        ])

        writeFile file: 'run_build.sh', text: buildScript
        sh 'chmod +x run_build.sh && bash run_build.sh'
    }

    // ── 8. Nexus upload (installer modules only) ──────────────────────────────
    if (!isCoreType) {
        dir(workspacePath) {
            nexusUrls.simulateNexusUpload(moduleName, '1.0', cfg.buildTypeSuffix ?: '-SNAPSHOT', cfg)
        }
    }

    // ── 9. Security toolchain ─────────────────────────────────────────────────
    if (effectiveRunSecurityTools) {
        dir(workspacePath) {
            securityOrchestrator.runBranchSecurityTooling([
                moduleName:       moduleName,
                moduleBranch:     branch,
                jdkVersion:       jdkVersion,
                workspacePath:    workspacePath,
                gitOrg:           cfg.gitOrg,
                namespace:        namespace,
                isCoreType:       isCoreType,
                cfg:              cfg,
                runSecurityTools: effectiveRunSecurityTools
            ])
        }
    } else {
        echo "⏭️ Security tools skipped (effectiveRunSecurityTools=false)"
    }

    echo "✅ buildModulesWithJDK complete: ${moduleName} (${jdkVersion})"
}
