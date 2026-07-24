// vars/getCDDefaults.groovy
import org.customerxp.cd.CdNexusEnvUrls

/**
 * CD configuration from Jenkins env. Missing values that callers require must be set in
 * Manage Jenkins global properties or job/folder env — no hardcoded product defaults in this var.
 * Set in Jenkins global config or job env (e.g. CX_CD_*).
 *
 * Full list of CD globals and CI aliases: {@code CD-pipeline/config/cdGlobalEnvInventory.groovy}
 */

/**
 * Comma-separated deployable module ids (sorted). Single source of truth: {@code loadModuleRegistry()}
 * / {@code CD-pipeline/config/module-registry.json} (unless overridden below).
 *
 * Optional override (takes precedence): {@code CX_CD_DEFAULT_MODULES}.
 */
def getDefaultDeployModules() {
    def explicit = env.CX_CD_DEFAULT_MODULES?.toString()?.trim()
    if (explicit) {
        return explicit
    }
    try {
        def reg = loadModuleRegistry()
        return reg.modules.collect { it.id.toString().trim().toLowerCase() }.sort().join(',')
    } catch (Exception e) {
        error "getDefaultDeployModules: set CX_CD_DEFAULT_MODULES or fix registry load — ${e.message}"
    }
}

/**
 * Nexus host base URL — {@code JENKINS_NEXUS_URL} (no default).
 */
def getNexusBaseUrl() {
    def b = CdNexusEnvUrls.nexusHostBase(env)
    if (!b) {
        error 'Set JENKINS_NEXUS_URL (Nexus host base URL).'
    }
    return b
}

/**
 * Full URL to cc_setup.sh on Nexus — host + {@code JENKINS_NEXUS_SCRIPTS_URI_PATH} + script name env.
 */
def getCcSetupScriptNexusUrl() {
    def u = CdNexusEnvUrls.ccSetupScriptUrl(env)
    if (!u) {
        error 'Set JENKINS_NEXUS_URL + JENKINS_NEXUS_SCRIPTS_URI_PATH + JENKINS_NEXUS_CC_SETUP_SCRIPT_NAME.'
    }
    return u
}

/**
 * Full URL to module_deploy.sh on Nexus.
 */
def getModuleDeployScriptNexusUrl() {
    def u = CdNexusEnvUrls.moduleDeployScriptUrl(env)
    if (!u) {
        error 'Set JENKINS_NEXUS_URL + JENKINS_NEXUS_SCRIPTS_URI_PATH + JENKINS_NEXUS_MODULE_DEPLOY_SCRIPT_NAME.'
    }
    return u
}

/**
 * Full URL to module_json archive on Nexus.
 */
def getModuleJsonNexusUrl() {
    def u = CdNexusEnvUrls.moduleJsonTarUrl(env)
    if (!u) {
        error 'Set JENKINS_NEXUS_URL + JENKINS_NEXUS_SCRIPTS_URI_PATH + JENKINS_NEXUS_MODULE_JSON_ARCHIVE_NAME.'
    }
    return u
}

/**
 * Jenkins workspace path used for CD (env file, etc.). Set JENKINS_CD_WORKSPACE — no default path.
 */
def getCDWorkspacePath() {
    def p = env.JENKINS_CD_WORKSPACE?.toString()?.trim()
    if (!p) {
        error 'Set JENKINS_CD_WORKSPACE (directory on the Jenkins agent for CD workspace files).'
    }
    return p
}

return this
