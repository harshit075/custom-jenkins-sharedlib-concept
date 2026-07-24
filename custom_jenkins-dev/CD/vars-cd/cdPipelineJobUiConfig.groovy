// vars/cdPipelineJobUiConfig.groovy — P2-1: single source for CD Build-with-Parameters UI data (registry, JDK, GitLab bindings).
import groovy.json.JsonOutput
import org.customerxp.cd.CdJobParameterScripts
import org.customerxp.cd.CdRegistryDbRole
import org.customerxp.cd.CdDeployKinds

/**
 * Builds all derived values for {@code cdPipelineParameters} so the Jenkinsfile only wires Active Choices / properties.
 *
 * @param pipelineEnv pass {@code env} from a {@code script { }} stage so workspace + Manage Jenkins globals resolve correctly.
 * @return Map with registry, choice scripts, JDK strings, row-visibility escapers, and baked MODULE_VERSION_CHOICES Groovy body.
 */
def call(Object pipelineEnv = null) {
    def regUi = loadModuleRegistry()

    def moduleIdsUi = regUi.modules.collect { it.id.toString().trim().toLowerCase() }.sort()
    def moduleSelectedScript = 'return ' + JsonOutput.toJson(moduleIdsUi)
    def registryPerDbSorted = CdRegistryDbRole.moduleIdsWithPerModuleDbSorted(regUi)
    def registryPerDbSet = (registryPerDbSorted ?: []) as Set
    def perModuleDbUiEnv = resolveCdEnv('JENKINS_CD_PER_MODULE_DB_UI_MODULES', pipelineEnv)?.toString()?.trim()
    def perDbIdsUi = CdRegistryDbRole.perModuleDbUiModuleList(registryPerDbSet, perModuleDbUiEnv)
    def perDbJsonForScripts = JsonOutput.toJson(perDbIdsUi)
    // Row-visibility bootstrap (MODULE_VERSION_CHOICES) used to map every per-DB registry module → the same
    // parameter name PER_MODULE_DB_JSON; sync() then toggled that row and often left it display:none (OR logic
    // still loses when rowForParam misses in some Jenkins layouts, or sticky !important from an older run).
    // The PER_MODULE_DB_JSON Active Choice body already shows "not required" when empty — keep the table row visible.
    def jsMapFromRegistry = '{}'

    // JDK dropdown: only Manage Jenkins globals CX_JDK_VERSION_OPTIONS / CX_JDK_VERSION_DEFAULT (same as CI) — no baked defaults.
    def jdkOptsRaw = resolveCdEnv('CX_JDK_VERSION_OPTIONS', pipelineEnv)?.toString() ?: ''
    def jdkOptsStr = jdkOptsRaw.split(',').collect { it.trim() }.findAll { it }.join(',')
    def jdkDefStr = resolveCdEnv('CX_JDK_VERSION_DEFAULT', pipelineEnv)?.toString()?.trim() ?: ''
    def jdkChoiceValues = jdkOptsStr
    def jdkChoiceDefault = jdkDefStr

    def ccSetupModuleIdUi = regUi.modules.find { it.deployKind?.toString() == CdDeployKinds.CC_SETUP }?.id?.toString()?.trim()?.toLowerCase() ?: ''
    def ccPortMidEscJs = ccSetupModuleIdUi.replace('\\', '\\\\').replace('\'', '\\\'')

    // GitLab tag lookup (MODULE_VERSION_CHOICES): CX_* globals only — no host/org defaults.
    def glApi = resolveCdEnv('CX_GITLAB_API_URL', pipelineEnv)?.trim() ?: ''
    def glOrg = resolveCdEnv('CX_GITLAB_ORG', pipelineEnv)?.trim() ?: ''
    def glNs = resolveCdEnv('CX_GITLAB_NAMESPACES', pipelineEnv)?.trim() ?: ''
    def glCred = resolveCdEnv('CX_GITLAB_TOKEN_CREDENTIAL_ID', pipelineEnv)?.trim() ?: ''

    if (!glCred?.trim()) {
        error 'cdPipelineJobUiConfig: set CX_GITLAB_TOKEN_CREDENTIAL_ID for MODULE_VERSION_CHOICES.'
    }

    def moduleVersionChoicesScript = CdJobParameterScripts.moduleVersionChoicesScript(glApi, glOrg, glNs, glCred, jsMapFromRegistry, ccSetupModuleIdUi ?: '')

    return [
        registry                : regUi,
        moduleIdsUi             : moduleIdsUi,
        moduleSelectedScript    : moduleSelectedScript,
        perDbIdsUi              : perDbIdsUi,
        perDbJsonForScripts     : perDbJsonForScripts,
        perModuleDbUiEnv        : perModuleDbUiEnv ?: '',
        jsMapFromRegistry       : jsMapFromRegistry,
        jdkChoiceValues         : jdkChoiceValues,
        jdkChoiceDefault        : jdkChoiceDefault,
        ccSetupModuleIdUi       : ccSetupModuleIdUi,
        ccPortMidEscJs          : ccPortMidEscJs,
        moduleVersionChoicesScript: moduleVersionChoicesScript,
        gitlabApiUrlResolved              : glApi,
        gitlabOrgResolved                 : glOrg,
        gitlabTokenCredentialIdResolved   : glCred,
    ]
}
