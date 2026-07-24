package org.customerxp.cd

import java.nio.file.Files
import java.nio.file.Paths
import java.util.Collections

/**
 * Validates Jenkins global / job environment for the CD pipeline before deploy stages run.
 * Uses the same URL rules as {@link CdNexusEnvUrls}.
 */
@SuppressWarnings(['unused'])
final class CdPipelineEnvCheck {

    /** Env keys merged with {@code resolveCdEnv} fallbacks (Manage Jenkins globals). */
    private static final List<String> OVERLAY_KEYS = Collections.unmodifiableList([
        'JENKINS_NEXUS_URL',
        'JENKINS_NEXUS_CREDENTIALS',
        'JENKINS_NEXUS_MODULE_ARTIFACT_PATH',
        'JENKINS_NEXUS_SCRIPTS_URI_PATH',
        'JENKINS_NEXUS_CC_SETUP_SCRIPT_NAME',
        'JENKINS_NEXUS_MODULE_DEPLOY_SCRIPT_NAME',
        'JENKINS_NEXUS_MODULE_JSON_ARCHIVE_NAME',
        'JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE',
        'JENKINS_CD_CC_DB_HISTORY_FILE',
        'JENKINS_CD_WORKSPACE',
        'JENKINS_CD_SERVERS_CONFIG_FILE',
        'JENKINS_CD_DB_CONFIG_FILE',
        'JENKINS_CD_CC_DB_CONTEXT_FILE',
        'JENKINS_CD_PER_MODULE_DB_UI_MODULES',
        'JENKINS_CD_MODULE_INSTANCE_COUNT'
    ])

    static final class Result implements Serializable {
        private static final long serialVersionUID = 1L
        boolean ok
        List<String> failures = []
        List<String> warnings = []

        String summaryLine() {
            ok ? 'CD environment check passed.' : "CD environment check failed (${failures.size()} issue(s))."
        }

        String fullReport() {
            def sb = new StringBuilder()
            sb.append(summaryLine()).append('\n')
            if (failures) {
                sb.append('\nRequired:\n')
                failures.each { sb.append('  • ').append(it).append('\n') }
            }
            if (warnings) {
                sb.append('\nWarnings:\n')
                warnings.each { sb.append('  • ').append(it).append('\n') }
            }
            sb.toString()
        }
    }

    /**
     * Pipeline {@code env} plus globals resolved through {@code resolveCdEnv(name, pipelineEnv)}.
     */
    static Map<String, String> buildEffectiveEnv(def pipelineEnv, Closure resolveCdEnv) {
        Map<String, String> m = new LinkedHashMap<>()
        try {
            def ge = pipelineEnv?.getEnvironment()
            if (ge != null) {
                ge.each { k, val ->
                    if (val != null && val.toString().trim()) {
                        m[k.toString()] = val.toString().trim()
                    }
                }
            }
        } catch (Throwable ignored) {
            // non-pipeline binding
        }
        if (resolveCdEnv != null) {
            OVERLAY_KEYS.each { String name ->
                def v = m.get(name) ?: resolveCdEnv.call(name, pipelineEnv)?.toString()?.trim()
                if (v) {
                    m[name] = v
                }
            }
        }
        return m
    }

    /**
     * @param checkServersFile if {@code true}, require {@code JENKINS_CD_SERVERS_CONFIG_FILE} and verify path exists on controller
     * @param checkDbConfigFile if {@code true}, require {@code JENKINS_CD_DB_CONFIG_FILE} when using DB-backed flows (optional)
     */
    static Result verify(Map<String, String> envLike, boolean checkServersFile = true, boolean checkDbConfigFile = false) {
        Result r = new Result()

        if (!trim(envLike.JENKINS_NEXUS_CREDENTIALS)) {
            r.failures.add(
                'Nexus credentials: set JENKINS_NEXUS_CREDENTIALS (Jenkins username/password credential id for module .tgz pre-download).'
            )
        }

        if (!CdNexusEnvUrls.modulePackageBase(envLike)) {
            r.failures.add(
                'Module .tgz base: set JENKINS_NEXUS_URL and JENKINS_NEXUS_MODULE_ARTIFACT_PATH ' +
                    '(path under host, e.g. repository/package-images/modules).'
            )
        }
        if (!CdNexusEnvUrls.ccSetupScriptUrl(envLike)) {
            r.failures.add(
                'cc_setup script URL: set JENKINS_NEXUS_URL + JENKINS_NEXUS_SCRIPTS_URI_PATH + JENKINS_NEXUS_CC_SETUP_SCRIPT_NAME.'
            )
        }
        if (!CdNexusEnvUrls.moduleDeployScriptUrl(envLike)) {
            r.failures.add(
                'module_deploy script URL: set JENKINS_NEXUS_URL + JENKINS_NEXUS_SCRIPTS_URI_PATH + JENKINS_NEXUS_MODULE_DEPLOY_SCRIPT_NAME.'
            )
        }
        if (!CdNexusEnvUrls.moduleJsonTarUrl(envLike)) {
            r.failures.add(
                'module_json archive URL: set JENKINS_NEXUS_URL + JENKINS_NEXUS_SCRIPTS_URI_PATH + JENKINS_NEXUS_MODULE_JSON_ARCHIVE_NAME.'
            )
        }

        def rpTpl = trim(envLike.JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE)
        if (!rpTpl) {
            r.failures.add(
                'Remote script directory: set JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE (e.g. /home/${user}/scripts) in Manage Jenkins global env.'
            )
        }

        def cdWs = trim(envLike.JENKINS_CD_WORKSPACE)
        if (!cdWs) {
            r.failures.add(
                'CD Jenkins workspace: set JENKINS_CD_WORKSPACE (absolute path on the agent for CD checkout and env files).'
            )
        }

        if (checkServersFile) {
            verifyServersConfigFile(envLike, r)
        }
        if (checkDbConfigFile) {
            verifyDbConfigFile(envLike, r)
        }
        verifyCcDbContextFile(envLike, r)

        warnGitLabIfMissing(envLike, r)

        r.ok = r.failures.isEmpty()
        return r
    }

    private static String trim(def v) {
        v?.toString()?.trim() ?: ''
    }

    private static void verifyServersConfigFile(Map envLike, Result r) {
        def p = trim(envLike.JENKINS_CD_SERVERS_CONFIG_FILE)
        if (!p) {
            r.failures.add('JENKINS_CD_SERVERS_CONFIG_FILE — absolute path on the Jenkins controller to the APP_SERVER JSON (required for deploy UI).')
            return
        }
        try {
            def path = Paths.get(p)
            if (!Files.isRegularFile(path)) {
                r.warnings.add(
                    "JENKINS_CD_SERVERS_CONFIG_FILE is not a readable file from this pipeline node (${p}). " +
                        'Confirm the path exists on the Jenkins controller (agents often cannot see controller-only paths).'
                )
            }
        } catch (Throwable t) {
            r.warnings.add("Could not verify JENKINS_CD_SERVERS_CONFIG_FILE (${p}): ${t.message}")
        }
    }

    private static void verifyDbConfigFile(Map envLike, Result r) {
        def p = trim(envLike.JENKINS_CD_DB_CONFIG_FILE)
        if (!p) {
            r.failures.add('JENKINS_CD_DB_CONFIG_FILE — set when using DB-backed deploy steps.')
            return
        }
        try {
            def path = Paths.get(p)
            if (!Files.isRegularFile(path)) {
                r.warnings.add(
                    "JENKINS_CD_DB_CONFIG_FILE not readable from this pipeline node (${p}); confirm on controller."
                )
            }
        } catch (Throwable t) {
            r.warnings.add("Could not verify JENKINS_CD_DB_CONFIG_FILE (${p}): ${t.message}")
        }
    }

    /** Requires env path; warns if file not created yet (first cc deploy creates it). */
    private static void verifyCcDbContextFile(Map envLike, Result r) {
        def p = trim(envLike.JENKINS_CD_CC_DB_CONTEXT_FILE)
        if (!p) {
            r.failures.add(
                'JENKINS_CD_CC_DB_CONTEXT_FILE — set absolute path on the pipeline node to cc_db_context.json (Manage Jenkins global env).'
            )
            return
        }
        try {
            def path = Paths.get(p)
            if (!Files.isRegularFile(path)) {
                r.warnings.add(
                    "JENKINS_CD_CC_DB_CONTEXT_FILE not a regular file yet (${p}); " +
                        'SHARED_DB deploys need this file after a PRIMARY_DB (cc) save.'
                )
            }
        } catch (Throwable t) {
            r.warnings.add("Could not verify JENKINS_CD_CC_DB_CONTEXT_FILE (${p}): ${t.message}")
        }
    }

    private static void warnGitLabIfMissing(Map envLike, Result r) {
        def gl = trim(envLike.CX_GITLAB_API_URL)
        def tok = trim(envLike.CX_GITLAB_TOKEN_CREDENTIAL_ID)
        if (!gl) {
            r.warnings.add('GitLab API URL unset (CX_GITLAB_API_URL) — MODULE_VERSION tag dropdown may not work; use manual Nexus versions.')
        }
        if (!tok) {
            r.warnings.add('GitLab token credential id unset (CX_GITLAB_TOKEN_CREDENTIAL_ID) — tag fetch may fail.')
        }
    }
}
