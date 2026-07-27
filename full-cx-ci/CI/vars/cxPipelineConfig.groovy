// vars/cxPipelineConfig.groovy
//
// EXACT REPLICA OF: CustomerXP cxPipelineConfig.groovy
//
// PURPOSE:
// Single source of truth for all Jenkins global environment variable validation.
// Called once at pipeline start — validates all required vars exist, then returns
// a config Map used by every downstream function.
//
// ADAPTED FOR LOCAL JENKINS:
// - Replaces GitLab/Nexus/SonarQube requirements with GitHub-based equivalents
// - Same validation pattern: fail fast with clear error listing ALL missing vars
// - Same return Map structure so all downstream scripts work identically
//
// REQUIRED JENKINS GLOBAL ENVIRONMENT VARIABLES (Manage Jenkins → System → Env vars):
//   CX_BUILD_TYPE              — snapshot | release
//   CX_GIT_ORG                 — GitHub org/user (e.g. gabrielecirulli)
//   CX_GIT_BASE_URL            — https://github.com
//   CX_GIT_HTTP_CREDENTIALS_ID — Jenkins credential ID for git (or 'none' for public repos)
//   CX_CHAIN_ORDER             — comma-separated module build order
//   CX_CORE_MODULE_IDS         — comma-separated core module IDs
//   CX_INSTALLER_MODULE_IDS    — comma-separated installer module IDs
//   CX_JDK_VERSION_OPTIONS     — comma-separated JDK labels (e.g. jdk-17,jdk-11)
//   CX_JDK_VERSION_DEFAULT     — default JDK label
//   CX_WORKSPACE_BASE          — base path for module workspaces (e.g. /var/jenkins_home/cx-builds)
//
// OPTIONAL:
//   CX_PIPELINE_DEBUG          — true|false verbose logging
//   CX_REACT_CI_STRICT         — true|false export CI=true for React builds
//   CX_RUN_SECURITY_TOOLS      — true|false (default true)
//   CX_SONAR_BASE_URL          — SonarQube server URL (stubbed if absent)
//   CX_SONAR_ENV_NAME          — Jenkins SonarQube environment name
//   CX_NEXUS_URL               — Nexus server URL (stubbed if absent)
//   CX_NEXUS_CREDENTIALS_ID    — Nexus credentials ID

def call(boolean securityToolsEnabled = true) {
    echo "═══════════════════════════════════════════════════════════════"
    echo "  cxPipelineConfig: Validating Pipeline Configuration"
    echo "═══════════════════════════════════════════════════════════════"

    def errors = []

    // ── STRICT prerequisites — pipeline cannot run without these ──────────────
    def buildType          = env.CX_BUILD_TYPE?.trim()
    def gitOrg             = env.CX_GIT_ORG?.trim()
    def gitBaseUrl         = env.CX_GIT_BASE_URL?.trim()
    def gitHttpCredId      = env.CX_GIT_HTTP_CREDENTIALS_ID?.trim()
    def chainOrder         = env.CX_CHAIN_ORDER?.trim()
    def coreModuleIds      = env.CX_CORE_MODULE_IDS?.trim()
    def installerModuleIds = env.CX_INSTALLER_MODULE_IDS?.trim()
    def jdkOptions         = env.CX_JDK_VERSION_OPTIONS?.trim()
    def jdkDefault         = env.CX_JDK_VERSION_DEFAULT?.trim()
    def workspaceBase      = env.CX_WORKSPACE_BASE?.trim()

    if (!buildType)          errors << 'CX_BUILD_TYPE (snapshot | release)'
    if (!gitOrg)             errors << 'CX_GIT_ORG (e.g. gabrielecirulli)'
    if (!gitBaseUrl)         errors << 'CX_GIT_BASE_URL (e.g. https://github.com)'
    if (!gitHttpCredId)      errors << 'CX_GIT_HTTP_CREDENTIALS_ID (credential ID or "none" for public repos)'
    if (!chainOrder)         errors << 'CX_CHAIN_ORDER (comma-separated module build order)'
    if (!coreModuleIds)      errors << 'CX_CORE_MODULE_IDS (comma-separated core module IDs)'
    if (!installerModuleIds) errors << 'CX_INSTALLER_MODULE_IDS (comma-separated installer module IDs)'
    if (!jdkOptions)         errors << 'CX_JDK_VERSION_OPTIONS (comma-separated JDK labels)'
    if (!jdkDefault)         errors << 'CX_JDK_VERSION_DEFAULT (default JDK label)'
    if (!workspaceBase)      errors << 'CX_WORKSPACE_BASE (workspace root path, e.g. /var/jenkins_home/cx-builds)'

    if (errors) {
        def msg = [
            '',
            '╔══════════════════════════════════════════════════════════════════╗',
            '║  ❌ MISSING REQUIRED JENKINS GLOBAL ENVIRONMENT VARIABLES       ║',
            '╠══════════════════════════════════════════════════════════════════╣',
        ]
        errors.each { msg << "║  • ${it}" }
        msg += [
            '╠══════════════════════════════════════════════════════════════════╣',
            '║  Fix: Manage Jenkins → System → Environment variables           ║',
            '╚══════════════════════════════════════════════════════════════════╝',
            ''
        ]
        error msg.join('\n')
    }

    // ── Optional values with defaults ────────────────────────────────────────
    def pipelineDebug  = (env.CX_PIPELINE_DEBUG?.trim() ?: 'false') == 'true'
    def reactCiExport  = (env.CX_REACT_CI_STRICT?.trim() ?: 'false')
    def sonarBaseUrl   = env.CX_SONAR_BASE_URL?.trim() ?: ''
    def sonarEnvName   = env.CX_SONAR_ENV_NAME?.trim() ?: 'SonarQube'
    def nexusBaseUrl   = env.CX_NEXUS_URL?.trim() ?: ''
    def nexusCredId    = env.CX_NEXUS_CREDENTIALS_ID?.trim() ?: ''

    // ── Set effective mirror paths (same as CustomerXP's EFFECTIVE_* env vars)
    env.EFFECTIVE_GIT_LOCAL_MIRROR_BASE = env.CX_GIT_LOCAL_MIRROR_BASE?.trim() ?: '/var/jenkins_home/cx/git'
    env.EFFECTIVE_GIT_ORG_DIR          = env.CX_GIT_ORG_DIR?.trim() ?: gitOrg

    // ── Build and return config Map ───────────────────────────────────────────
    def cfg = [
        buildType:             buildType,
        buildTypeSuffix:       buildType == 'release' ? '' : '-SNAPSHOT',
        gitOrg:                gitOrg,
        gitHost:               gitBaseUrl.replaceAll(/https?:\/\//, '').split('/')[0],
        gitBaseUrl:            gitBaseUrl,
        gitHttpCredentialId:   gitHttpCredId,
        chainOrder:            chainOrder.split(',').collect { it.trim() }.findAll { it },
        coreModuleIds:         coreModuleIds.split(',').collect { it.trim() }.findAll { it },
        installerModuleIds:    installerModuleIds.split(',').collect { it.trim() }.findAll { it },
        jdkOptions:            jdkOptions.split(',').collect { it.trim() }.findAll { it },
        jdkDefault:            jdkDefault,
        workspaceBase:         workspaceBase,
        m2DebugExplicit:       pipelineDebug ? 'true' : 'false',
        reactCiExport:         reactCiExport,
        sonarBaseUrl:          sonarBaseUrl,
        sonarQubeEnvName:      sonarEnvName,
        nexusBaseUrl:          nexusBaseUrl,
        nexusCredentialId:     nexusCredId,
        effectiveGitLocalMirrorBase: env.EFFECTIVE_GIT_LOCAL_MIRROR_BASE,
        effectiveGitOrgDir:    env.EFFECTIVE_GIT_ORG_DIR,
        securityToolsEnabled:  securityToolsEnabled
    ]

    echo "✅ cxPipelineConfig validated successfully"
    echo "   gitOrg=${cfg.gitOrg}, gitHost=${cfg.gitHost}, buildType=${cfg.buildType}"
    echo "   chainOrder=${cfg.chainOrder.join(' → ')}"
    echo "   coreModules=${cfg.coreModuleIds.join(', ')}"
    echo "   installerModules=${cfg.installerModuleIds.join(', ')}"
    echo "   jdkOptions=${cfg.jdkOptions.join(', ')}, default=${cfg.jdkDefault}"
    echo "   workspaceBase=${cfg.workspaceBase}"
    echo "   pipelineDebug=${pipelineDebug}, reactCiStrict=${reactCiExport}"
    if (cfg.sonarBaseUrl) echo "   sonarBaseUrl=${cfg.sonarBaseUrl}"
    if (cfg.nexusBaseUrl) echo "   nexusBaseUrl=${cfg.nexusBaseUrl}"

    return cfg
}
