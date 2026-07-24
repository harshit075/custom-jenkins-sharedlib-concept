// vars/cxPipelineConfig.groovy
//
// ═══════════════════════════════════════════════════════════════════════════════════
// INVENTORY: environment variables used across this shared library and CI Jenkinsfiles.
// Prefer Manage Jenkins → Configure System → Global properties → Environment variables.
// Do not duplicate secrets or org URLs in Jenkinsfile `environment {}` except BUILD_TYPE
// when snapshot vs release jobs cannot share one global value.
// ═══════════════════════════════════════════════════════════════════════════════════
//
// ── Validated by call() — “strict” prerequisites (fail job if missing when applicable)
//
//   BUILD_TYPE                    — snapshot | release (see also buildTypeSuffix in return map)
//   JENKINS_GIT_ORG              — GitLab/Git org path segment for clone URLs & SMG (required)
//   CX_GIT_HOST                   — Explicit host; OR parseable JENKINS_GIT_URL to derive host
//   JENKINS_GIT_URL               — Used to derive host when CX_GIT_HOST unset
//   JENKINS_GIT_CREDENTIALS or JENKINS_GIT_HTTP_CREDENTIALS_ID — Username/password credential id for HTTPS/git
//   JENKINS_NEXUS_CREDENTIALS     — Nexus credential id
//   JENKINS_NEXUS_URL or CX_NEXUS_URL — Nexus base (normalized in return map)
//   JENKINS_M2_DEBUG              — true | false — required when JENKINS_PIPELINE_DEBUG unset
//   JENKINS_PIPELINE_DEBUG        — Optional master: true|false|1|0|yes|no (overrides M2 debug semantics)
//
// When securityToolsEnabled is true (e.g. final-ci with default runSecurityTools):
//   Per-tool prerequisites are centralized in vars/securityToolsCatalog.groovy (TOOL_ENABLED + collectMissingEnvForEnabledTools).
//   Legacy fallbacks match former Sonar + Dependency-Track globals if the catalog script is absent.
//
// ── Set by call() after successful validation (derived / effective paths — not Manage Jenkins secrets)
//
//   env.EFFECTIVE_GIT_LOCAL_MIRROR_BASE — GIT_LOCAL_MIRROR_BASE or default /jenkins_data/cx/git
//   env.EFFECTIVE_GIT_ORG_DIR      — GIT_ORG_DIR override or JENKINS_GIT_ORG (mirror cleanup in final-ci)
//
// Optional — set by Jenkinsfiles after Validate (Option A): serialized cxPipelineConfig map for reuse in parallel cells
//   env.CX_PIPELINE_CFG_JSON     — JSON from vars/pipelineConfigSerde.groovy; buildModulesWithJDK reads this before calling cxPipelineConfig() again
//
// ── Optional globals (pipeline / agent behaviour; defaults often in getDefaults.groovy)
//
// Git org alias (buildModulesWithJDK, tool*.groovy — clone/SMG discovery):
//   CX_GIT_ORG                    — Optional; when set, used with JENKINS_GIT_ORG for module paths (see those scripts)
//
// Git (extra — getCredentials.groovy, getDiscoveryRepoURL, getModuleChoices.groovy):
//   JENKINS_GIT_SSH_CREDENTIALS   — SSH credential id (fallback patterns)
//   JENKINS_BUILD_CREDENTIALS    — Generic build credential; falls back to JENKINS_GIT_CREDENTIALS
//   JENKINS_GIT_PROJECT          — Repo group segment for getDiscoveryRepoURL()
//
// Workspace layout (executeChainBuild / executeParallelBuild):
//   JENKINS_WORKSPACE_BASE       — Agent workspace root for module workspaces
//
// Local mirror cleanup (final-cipipeline “Initialize & Clean”; defaults applied in call()):
//   GIT_LOCAL_MIRROR_BASE        — Override root for org mirror wipe (see EFFECTIVE_* above)
//   GIT_ORG_DIR                  — Override org folder name under mirror (else JENKINS_GIT_ORG)
//
// SMG / tooling (resolveCxSmgDevPath, buildModulesWithJDK):
//   CX_SMG_BASE_GLOBAL           — Tools root passed as cxSmgBase from jobs
//   CX_SMG_DEV, CX_SMG_DEV_DEFAULT — SMG dev tree resolution order
//
// Chain / installer ordering & module lists (validateInstallerCorePrerequisites, resolveChainBuildModules,
// getModuleChoices — union of core + installer ids):
//   CX_CHAIN_ORDER               — Canonical module chain string
//   CX_CORE_MODULE_IDS           — Comma-separated core module ids (prerequisite checks; final-ci CORE_SELECTED source)
//   CX_INSTALLER_MODULE_IDS      — Comma-separated installer module ids (final-ci INST_SELECTED; merged into module probes)
//
// final-ci / release-ci Jenkinsfile UI (Active Choices + ExtendedChoice; read on controller via System.getenv at job definition time):
//   CX_GITLAB_API_URL             — GitLab HTTP API base URL (e.g. …/api/v4)
//   CX_GITLAB_ORG                 — Path segment for GitLab project path (top-level group/org)
//   CX_GITLAB_NAMESPACES          — Comma-separated namespaces tried in order for branch dropdown resolution
//   CX_GITLAB_TOKEN_CREDENTIAL_ID — Jenkins Secret Text credential id (GitLab private token for API)
//   CX_GITLAB_BRANCH_UI_LIMIT     — (Optional) Max branches shown per module in Active Choices HTML (default 200; keeps UI light)
//   CX_GITLAB_BRANCH_MAX_PAGES    — (Optional) Max GitLab API pages fetched (100 branches/page; default 50 = up to 5000 branches)
//   CX_JDK_VERSION_OPTIONS        — Comma-separated JDK labels for jdk_version checkbox (must align with JDK tools)
//   CX_JDK_VERSION_DEFAULT        — Default jdk_version selection (subset of CX_JDK_VERSION_OPTIONS)
//   CX_GITLAB_BRANCH_PER_PAGE     — GitLab branches API page size (e.g. 100; used by nightly branch resolver)
//
// Nightly SNAPSHOT CI (nightly-cipipeline.jenkinsfile) — reuses globals above plus:
//   JENKINS_CRON_SCHEDULE           — Quartz cron (e.g. 0 0 * * *)
//   JENKINS_FAILURE_EMAIL_RECIPIENTS — Comma-separated failure notification recipients
//   JENKINS_FAILURE_EMAIL_SUBJECT   — (Optional) subject template; placeholders {JOB_NAME},{BUILD_NUMBER},{RESULT},{BUILD_URL}
//   JENKINS_FAILURE_EMAIL_BODY     — (Optional) body template; same placeholders
//   CX_SEQUENTIAL_INSTALLER_MODULE_IDS — Installer ids in sequential lane with core; else CX_CREINSTALLER_MODULES
//   JENKINS_SONAR_REPORT_NAME or CX_SONAR_REPORT_NAME — Sonar HTML report link title for nightly publishSonarReport
//
// GitLab namespace overrides (vars/moduleSpecialCases.groovy — discovery when repo not under default namespace):
//   CX_GITLAB_NAMESPACE_OVERRIDES_EXACT — moduleId:namespace/path pairs, comma-separated
//   CX_GITLAB_NAMESPACE_OVERRIDES_REGEX — pattern:namespace rules; multiple rules separated by ;;
//
// SBOM paths / CycloneDX discovery (vars/sbomDiscovery.groovy — Dependency-Track and SBOM_FILE hint; per-module overrides in that file)
//
// Installer / Gradle special lists (optional; empty safe):
//   CX_GRADLE_ONLY_MODULES        — Comma-separated module ids — Gradle-only handling & Sonar report publish path (toolSbomJacoco / publishSonarReport)
//   CX_CREINSTALLER_MODULES       — Comma-separated module ids — creinstaller / cc-platform installer.bash behavior (bash scripts in mainBuildAndSonarScript / tagRebuildBuildScript)
//
// React / Create React App (vars/resolveReactCiEnv.groovy — export CI in run_build.sh and withEnv on JDK cells):
//   CX_REACT_CI_STRICT            — true|false|1|0|yes|no — when true, react-scripts treats ESLint warnings as errors (CI=true); default unset ⇒ false (legacy efmapp.jenkinsfile parity)
//
// Release pipeline — per-module Git tags (release-cipipeline; optional allowlist):
//   JENKINS_RELEASE_TAG_PER_MODULE — Comma-separated module ids that must use only RELEASE_TAG_PER_MODULE_CHOICES (never global RELEASE_TAG); empty/unset ⇒ all modules use RELEASE_TAG only (legacy: JENKINS_RELEASE_TAG_PER_MODULE_MODULES)
//   RELEASE_TAG_PER_MODULE_CHOICES — Job parameter (multiline): moduleId|tag per line; resolved by vars/releaseTagPerModule.groovy
//
// Sonar Jenkins report page (vars/publishSonarReport.groovy — optional; defaults if unset):
//   CX_SONAR_REPORT_METRICS       — Comma-separated Sonar metric keys for /api/measures/component (HTML report table)
//
// Defaults & UI helpers (vars/getDefaults.groovy):
//   JENKINS_DEFAULT_MODULES, JENKINS_DEFAULT_JDKS, JENKINS_DEFAULT_BRANCH,
//   JENKINS_BUILD_TYPES, JENKINS_BRANCH_FILTER, JENKINS_BRANCH_SUFFIX
//
// Security tools toggle & Dependency-Track thresholds (getDefaults.groovy; securityToolsCatalog / tool*.groovy):
//   JENKINS_RUN_SECURITY_TOOLS   — true|false|1|yes — global gate with job-level runSecurityTools
//   JENKINS_DEPENDENCY_TRACK_INSTANCE — DT Jenkins instance name (default label if unset)
//   JENKINS_DT_UNSTABLE_CRITICAL, JENKINS_DT_UNSTABLE_HIGH, JENKINS_DT_UNSTABLE_MEDIUM,
//   JENKINS_DT_FAILED_CRITICAL, JENKINS_DT_FAILED_HIGH — numeric quality gates
//
// DefectDojo (uploadToDefectDojo.groovy, toolDefectdojo.groovy, getDefectDojoProductName.groovy):
//   DOJO_URL, DOJO_PRODUCT_NAME (upload fallback)
//   JENKINS_DOJO_PRODUCT_RULES — REQUIRED for getDefectDojoProductName: neo5:Clari5_Neo5,4.10:Clari5_410 (example; first substring match wins)
//
// Slack notifications (getCredentials.groovy):
//   JENKINS_SLACK_CREDENTIALS, JENKINS_SLACK_CHANNEL
//
// Debug / diagnostics (debug.groovy, buildModulesWithJDK):
//   JENKINS_CD_BASH_X            — true → run installer bash with -x
//
// Library developer test mode (loadSharedLibVarScript.groovy):
//   SHARED_LIB_VARS_PATH         — Load vars/*.groovy from a local path
//
// ── Jenkins built-in (not global properties but referenced in vars)
//
//   BUILD_NUMBER, JOB_NAME, WORKSPACE — standard Jenkins env

/**
 * Validates required env vars for a module build and returns resolved strings (global env only — no Jenkinsfile literals).
 *
 * Side effects: sets env.EFFECTIVE_GIT_LOCAL_MIRROR_BASE and env.EFFECTIVE_GIT_ORG_DIR
 * for mirror cleanup (see header GIT_* — defaults applied here, not in Jenkinsfiles).
 *
 * @param securityToolsEnabled effective Sonar/SBOM/DT/JaCoCo flag.
 */
def call(Boolean securityToolsEnabled = true) {
    def missing = []

    def reqRaw = { String name ->
        def v = env["${name}"]
        if (v == null || v.toString().trim().isEmpty()) {
            missing << name
        }
    }

    String gitOrg = env.JENKINS_GIT_ORG?.toString()?.trim()
    if (!gitOrg) {
        missing << 'JENKINS_GIT_ORG'
    }

    String gitHost = env.CX_GIT_HOST?.toString()?.trim()
    if (!gitHost) {
        String gitUrl = env.JENKINS_GIT_URL?.toString()?.trim()
        if (gitUrl) {
            try {
                gitHost = new URL(gitUrl).host
            } catch (ignored) {
                gitHost = ''
            }
        }
    }
    if (!gitHost) {
        missing << 'CX_GIT_HOST (or parseable JENKINS_GIT_URL to derive host)'
    }

    reqRaw('BUILD_TYPE')
    def pipelineDebugRaw = env.JENKINS_PIPELINE_DEBUG?.toString()?.trim()
    if (!pipelineDebugRaw) {
        reqRaw('JENKINS_M2_DEBUG')
    }
    String gitHttpCred = firstNonEmptyEnv('JENKINS_GIT_HTTP_CREDENTIALS_ID', 'JENKINS_GIT_CREDENTIALS')
    if (!gitHttpCred) {
        missing << 'JENKINS_GIT_CREDENTIALS or JENKINS_GIT_HTTP_CREDENTIALS_ID'
    }
    reqRaw('JENKINS_NEXUS_CREDENTIALS')

    def nexusRaw = env.JENKINS_NEXUS_URL?.toString()?.trim()
    if (!nexusRaw) {
        missing << 'JENKINS_NEXUS_URL'
    }

    String sonarUrl = ''
    String sonarEnvName = ''
    String dtInst = ''
    String sonarTok = ''

    if (securityToolsEnabled) {
        def secCat = loadSharedLibVarScript('securityToolsCatalog')
        if (secCat) {
            missing.addAll(secCat.collectMissingEnvForEnabledTools(true))
        } else {
            sonarUrl = env.JENKINS_SONAR_URL?.toString()?.trim()
            if (!sonarUrl) {
                missing << 'JENKINS_SONAR_URL'
            }
            reqRaw('JENKINS_SONARQUBE_ENV_NAME')
            reqRaw('JENKINS_DEPENDENCY_TRACK_INSTANCE')
            reqRaw('JENKINS_SONAR_TOKEN_CREDENTIAL_ID')
        }
        sonarUrl = env.JENKINS_SONAR_URL?.toString()?.trim()
        sonarEnvName = env.JENKINS_SONARQUBE_ENV_NAME?.toString()?.trim() ?: ''
        dtInst = env.JENKINS_DEPENDENCY_TRACK_INSTANCE?.toString()?.trim() ?: ''
        sonarTok = env.JENKINS_SONAR_TOKEN_CREDENTIAL_ID?.toString()?.trim() ?: ''
    }

    if (missing) {
        error(
            '❌ Missing required environment variables. Set them under Manage Jenkins → Global properties.\n' +
            'See vars/cxPipelineConfig.groovy header.\n\n' +
            missing.unique().collect { "  • ${it}" }.join('\n')
        )
    }

    String m2DebugExplicit
    if (pipelineDebugRaw) {
        def pl = pipelineDebugRaw.toLowerCase()
        if (pl in ['true', '1', 'yes']) {
            m2DebugExplicit = 'true'
        } else if (pl in ['false', '0', 'no']) {
            m2DebugExplicit = 'false'
        } else {
            error("❌ JENKINS_PIPELINE_DEBUG must be true|false|1|0|yes|no (got '${env.JENKINS_PIPELINE_DEBUG}').")
        }
    } else {
        def m2 = env.JENKINS_M2_DEBUG.toString().trim().toLowerCase()
        if (m2 != 'true' && m2 != 'false') {
            error("❌ JENKINS_M2_DEBUG must be exactly 'true' or 'false' (got '${env.JENKINS_M2_DEBUG}').")
        }
        m2DebugExplicit = env.JENKINS_M2_DEBUG.toString().trim()
    }

    def mirrorOverride = env.GIT_LOCAL_MIRROR_BASE?.toString()?.trim()
    env.EFFECTIVE_GIT_LOCAL_MIRROR_BASE = mirrorOverride ?: '/jenkins_data/cx/git'
    def orgDirOverride = env.GIT_ORG_DIR?.toString()?.trim()
    env.EFFECTIVE_GIT_ORG_DIR = orgDirOverride ?: gitOrg

    def reactCiResolver = loadSharedLibVarScript('resolveReactCiEnv')
    String reactCiExport = reactCiResolver ? reactCiResolver.call() : 'false'

    return [
        gitHost                       : gitHost,
        gitOrg                        : gitOrg,
        nexusBaseUrl                  : normalizeNexusBaseUrl(nexusRaw),
        gitHttpCredentialId           : gitHttpCred,
        nexusCredentialId             : env.JENKINS_NEXUS_CREDENTIALS.toString().trim(),
        buildTypeSuffix               : env.BUILD_TYPE.toString().trim().toUpperCase(),
        m2DebugExplicit               : m2DebugExplicit,
        sonarBaseUrl                  : sonarUrl ? stripTrailingSlash(sonarUrl) : '',
        sonarQubeEnvName              : sonarEnvName,
        dependencyTrackInstance       : dtInst,
        sonarTokenCredentialId        : sonarTok,
        effectiveGitLocalMirrorBase   : env.EFFECTIVE_GIT_LOCAL_MIRROR_BASE,
        effectiveGitOrgDir            : env.EFFECTIVE_GIT_ORG_DIR,
        reactCiExport                 : reactCiExport,
    ]
}

String firstNonEmptyEnv(String... keys) {
    for (String k in keys) {
        def v = env[k]?.toString()?.trim()
        if (v) {
            return v
        }
    }
    return ''
}

def normalizeNexusBaseUrl(String raw) {
    def nexusBaseUrl = raw.toString().trim()
    if (nexusBaseUrl.endsWith('/nexus')) {
        nexusBaseUrl = nexusBaseUrl.substring(0, nexusBaseUrl.length() - 5)
    } else if (nexusBaseUrl.endsWith('/nexus/')) {
        nexusBaseUrl = nexusBaseUrl.substring(0, nexusBaseUrl.length() - 6)
    }
    if (!nexusBaseUrl.endsWith('/')) {
        nexusBaseUrl = nexusBaseUrl + '/'
    }
    return nexusBaseUrl
}

def stripTrailingSlash(String u) {
    def s = u.toString().trim()
    while (s.endsWith('/')) {
        s = s.substring(0, s.length() - 1)
    }
    return s
}
