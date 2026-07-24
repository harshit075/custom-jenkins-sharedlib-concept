package org.customerxp.cd

import hudson.slaves.EnvironmentVariablesNodeProperty
import jenkins.model.Jenkins


/**
 * P2-3 / P3-3: Jenkins UI scripts inline copies of selected helpers in {@link CdJobParameterScripts#activeChoiceRuntimeHelpers()}.
 * DB normalization delegates to {@link CdDbConfigNormalize}; keep Active Choice strings in sync when that class changes.
 */
@SuppressWarnings(['unused', 'GrMethodMayBeStatic'])
class CdActiveChoiceRuntime {

    /**
     * Normalize MODULE_SELECTED / PT_CHECKBOX binding to lowercase module ids (list may be Collection, array, or comma string).
     */
    static List<String> selectedModulesLower(Object moduleSelected) {
        if (moduleSelected instanceof Collection) {
            return moduleSelected.collect { it?.toString()?.trim()?.toLowerCase() }.findAll { it }
        }
        if (moduleSelected != null && moduleSelected.getClass().isArray()) {
            return moduleSelected.collect { it?.toString()?.trim()?.toLowerCase() }.findAll { it }
        }
        if (moduleSelected != null && moduleSelected.toString().trim()) {
            return moduleSelected.toString().tokenize(',').collect { it.trim().toLowerCase() }.findAll { it }
        }
        return []
    }

    /** First Jenkins global env node property map, or empty map. */
    static Map firstGlobalEnvVars() {
        def globalEnv = [:]
        try {
            def instance = Jenkins.getInstanceOrNull()
            if (instance) {
                def props = instance.getGlobalNodeProperties()
                def envNodes = props.getAll(EnvironmentVariablesNodeProperty.class)
                if (envNodes && envNodes.size() > 0) {
                    globalEnv = envNodes[0].getEnvVars()
                }
            }
        } catch (Exception ignored) { }
        globalEnv
    }

    /** APP_SERVER script: first global env map or null if missing. */
    static Map appServerGlobalEnvMap() {
        try {
            def j = Jenkins.getInstance()
            if (!j) return null
            def list = j.getGlobalNodeProperties().getAll(EnvironmentVariablesNodeProperty.class)
            if (!list || list.isEmpty()) return null
            return list.get(0).getEnvVars()
        } catch (Exception ignored) {
            return null
        }
    }

    static String envLookupAppServer(String key) {
        def env = appServerGlobalEnvMap()
        if (env != null && env.get(key)) return env.get(key).toString().trim()
        return System.getenv(key)?.toString()?.trim()
    }

    /**
     * Controller path to DB JSON — {@code JENKINS_CD_DB_CONFIG_FILE} from Jenkins global env (preferred) or JVM env only.
     * @return trimmed path, or {@code null} when unset (no default path)
     */
    static String dbConfigPath(Map globalEnv) {
        def path = globalEnv?.get('JENKINS_CD_DB_CONFIG_FILE')?.toString()?.trim()
        if (path) return path
        return System.getenv('JENKINS_CD_DB_CONFIG_FILE')?.toString()?.trim() ?: null
    }

    /** Per-module / global credentials HTML: user + password + credential_id per setup. */
    static Map normalizeDbCredentials(Object raw) {
        CdDbConfigNormalize.normalizeCredentials(raw)
    }

    /** Global DB_SERVER dropdown: full fields for deploy. */
    static Map normalizeDbFullGlobal(Object raw) {
        CdDbConfigNormalize.normalizeFull(raw)
    }

    /** PER_MODULE_DB_JSON summary: light user + credential id metadata. */
    static Map normalizeDbPerModuleMeta(Object raw) {
        CdDbConfigNormalize.normalizePerModuleMeta(raw)
    }
}
