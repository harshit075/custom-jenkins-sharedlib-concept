package org.customerxp.cd

/**
 * P3-7: Data-driven deployment steps — script URL + env vars by {@code deployKind}, with optional per-module
 * overrides from {@code module-registry.json} {@code scripts.ccSetupUrl} / {@code scripts.moduleDeployUrl}.
 * Remote SSH/bash dispatch stays in {@link CdRemoteDeployTemplates} (point 1); this class owns plan rows only.
 * <p>{@link CdDeployKinds} values are registry identifiers, not script filenames; Nexus script names come from env
 * ({@code JENKINS_NEXUS_CC_SETUP_SCRIPT_NAME}, {@code JENKINS_NEXUS_MODULE_DEPLOY_SCRIPT_NAME}, full URLs via {@link CdNexusEnvUrls}).
 */
@SuppressWarnings(['unused'])
final class CdDeploymentPlan {

    private static final Set<String> KNOWN_KINDS = [CdDeployKinds.CC_SETUP, CdDeployKinds.MODULE_DEPLOY] as Set

    /**
     * One ordered list of deploy steps after {@link CdDeployModuleOrder#sortForDeploy}.
     *
     * @param ccPort resolved CC listen port (written into {@code envVars.CC_PORT} for {@link CdDeployKinds#CC_SETUP} steps only)
     */
    static List<Map> buildSteps(
        List<String> orderedModuleIds,
        Map moduleVersionMap,
        String versionChoicesStr,
        Map baseEnvTemplate,
        String defaultCcSetupScriptUrl,
        String defaultModuleDeployScriptUrl,
        Map moduleRegistry,
        String ccPort
    ) {
        def selectedVersionChoices = versionChoicesStr
            ? versionChoicesStr.tokenize(',').collect { it.trim() }.findAll { it }
            : []
        def base = baseEnvTemplate ?: [:]
        def portTrim = ccPort?.toString()?.trim() ?: ''
        def plan = []
        orderedModuleIds.each { mod ->
            def entry = CdRegistryDbRole.findModuleEntry(moduleRegistry, mod)
            def dk = entry?.deployKind?.toString()?.trim()
            if (!dk || !(dk in KNOWN_KINDS)) {
                throw new IllegalArgumentException(
                    "CD deployment plan: unknown or missing deployKind '${dk}' for module '${mod}' (expected ${KNOWN_KINDS})."
                )
            }
            def scriptUrl = resolveScriptUrl(entry, dk, defaultCcSetupScriptUrl, defaultModuleDeployScriptUrl)
            def envVars = buildEnvVarsForKind(dk, mod, moduleVersionMap, selectedVersionChoices, base, portTrim)
            if (envVars == null) {
                return
            }
            plan << [
                name           : stepName(dk, mod),
                modules        : mod,
                scriptUrl      : scriptUrl,
                envVars        : envVars,
                deployKind     : dk
            ]
        }
        plan
    }

    /** Prefer registry {@code scripts.*}; fall back to pipeline defaults. */
    static String resolveScriptUrl(
        Object registryEntry,
        String deployKind,
        String defaultCcSetupScriptUrl,
        String defaultModuleDeployScriptUrl
    ) {
        def scripts = registryEntry?.scripts
        if (scripts instanceof Map) {
            if (deployKind == CdDeployKinds.CC_SETUP) {
                def u = scripts.ccSetupUrl?.toString()?.trim()
                if (u) {
                    return u
                }
            } else if (deployKind == CdDeployKinds.MODULE_DEPLOY) {
                def u = scripts.moduleDeployUrl?.toString()?.trim()
                if (u) {
                    return u
                }
            }
        }
        return deployKind == CdDeployKinds.CC_SETUP ? defaultCcSetupScriptUrl : defaultModuleDeployScriptUrl
    }

    /**
     * @return env map for the step, or {@code null} to omit the module (e.g. {@link CdDeployKinds#MODULE_DEPLOY} without version)
     */
    static Map buildEnvVarsForKind(
        String deployKind,
        String moduleId,
        Map moduleVersionMap,
        List<String> selectedVersionChoices,
        Map baseEnvTemplate,
        String ccPortTrim
    ) {
        if (deployKind == CdDeployKinds.CC_SETUP) {
            def choice = selectedVersionChoices.find { it.startsWith("${moduleId}:") } ?: ''
            def ev = [:] + baseEnvTemplate + [
                MODULE_NAME             : moduleId,
                MODULE_VERSION          : moduleVersionMap[moduleId] ?: '',
                MODULE_VERSION_CHOICES  : choice
            ]
            if (ccPortTrim) {
                ev.CC_PORT = ccPortTrim
            }
            return ev
        }
        if (deployKind == CdDeployKinds.MODULE_DEPLOY) {
            def ver = moduleVersionMap[moduleId]?.toString()?.trim() ?: ''
            if (!ver) {
                return null
            }
            return [:] + baseEnvTemplate + [
                MODULE_NAME             : moduleId,
                MODULE_VERSION          : ver,
                MODULE_VERSION_CHOICES  : "${moduleId}:${ver}"
            ]
        }
        null
    }

    static String stepName(String deployKind, String moduleId) {
        deployKind == CdDeployKinds.CC_SETUP
            ? "${CdDeployKinds.CC_SETUP}_${moduleId}"
            : "${CdDeployKinds.MODULE_DEPLOY}_${moduleId}"
    }
}
