// vars/releaseTagPerModule.groovy
//
// Release pipeline: modules listed in env JENKINS_RELEASE_TAG_PER_MODULE use only
// RELEASE_TAG_PER_MODULE_CHOICES (moduleId|tag) — never global RELEASE_TAG.
// See vars/cxPipelineConfig.groovy inventory.

/**
 * Module ids that require a dedicated per-module Git release tag (comma-separated env).
 */
Set<String> allowlistFromEnv() {
    def raw = env.JENKINS_RELEASE_TAG_PER_MODULE?.toString()?.trim()
    if (!raw) {
        raw = env.JENKINS_RELEASE_TAG_PER_MODULE_MODULES?.toString()?.trim()
    }
    if (!raw) {
        return [] as Set
    }
    return raw.split(',').collect { it.trim() }.findAll { it } as Set
}

/**
 * Parse job parameter text: one entry per line or comma-separated; lines starting with # ignored.
 * Format: {@code moduleId|tag} (pipe separates id from tag).
 */
Map<String, String> parsePerModuleChoices(String raw) {
    Map<String, String> m = [:]
    if (!raw?.trim()) {
        return m
    }
    raw.replace('\r\n', '\n').split(/[\n,]/).each { line ->
        def t = line.trim()
        if (!t || t.startsWith('#')) {
            return
        }
        def parts = t.split('\\|', 2)
        if (parts.length == 2) {
            def mod = parts[0].trim()
            def tag = parts[1].trim()
            if (mod) {
                m[mod] = tag
            }
        }
    }
    return m
}

/**
 * Validates global vs per-module tags for the current selection.
 * <ul>
 *   <li>Any selected module <b>not</b> in the allowlist requires non-empty {@code globalReleaseTag}.</li>
 *   <li>Any selected module <b>in</b> the allowlist requires non-empty {@code perModuleMap[module]}.</li>
 * </ul>
 */
void validateReleaseTagInputs(List selectedModules, Set<String> allowlist, String globalReleaseTag, Map perModuleMap) {
    List<String> sel = (selectedModules ?: []).collect { it?.toString()?.trim() }.findAll { it }.unique()
    if (sel.isEmpty()) {
        return
    }
    String global = globalReleaseTag?.toString()?.trim() ?: ''
    boolean needsGlobal = sel.any { mod -> !allowlist.contains(mod) }
    if (needsGlobal && !global) {
        error '❌ RELEASE_TAG is required when any selected module is not listed in JENKINS_RELEASE_TAG_PER_MODULE (those modules use the global release tag only).'
    }
    sel.each { mod ->
        if (allowlist.contains(mod)) {
            def t = perModuleMap[mod]?.toString()?.trim()
            if (!t) {
                error "❌ Release tag is not given for module '${mod}'. It is listed in JENKINS_RELEASE_TAG_PER_MODULE — set RELEASE_TAG_PER_MODULE_CHOICES (one line per module: moduleId|tag)."
            }
        }
    }
}

/**
 * Resolved Git tag for one module after validation (allowlist → per-module map only; else → global).
 */
String effectiveReleaseTagForModule(String moduleId, Set<String> allowlist, Map perModuleMap, String globalReleaseTag) {
    String mid = moduleId?.toString()?.trim()
    if (!mid) {
        error '❌ effectiveReleaseTagForModule: module id is empty.'
    }
    if (allowlist.contains(mid)) {
        def t = perModuleMap[mid]?.toString()?.trim()
        if (!t) {
            error "❌ Release tag is not given for module '${mid}'."
        }
        return t
    }
    def g = globalReleaseTag?.toString()?.trim()
    if (!g) {
        error "❌ RELEASE_TAG is required for module '${mid}'."
    }
    return g
}

/**
 * Subset map for {@code executeParallelBuild(releaseTagByModule: ...)} — only allowlisted parallel installers.
 */
Map parallelReleaseTagOverrides(List parallelModules, Set<String> allowlist, Map perModuleMap) {
    Map out = [:]
    (parallelModules ?: []).each { modName ->
        String mid = modName?.toString()?.trim()
        if (!mid) {
            return
        }
        if (allowlist.contains(mid)) {
            def t = perModuleMap[mid]?.toString()?.trim()
            if (t) {
                out[mid] = t
            }
        }
    }
    return out
}
