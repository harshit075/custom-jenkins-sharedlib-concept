/**
 * Release pipeline: global RELEASE_TAG vs per-module tags (allowlist env {@code JENKINS_RELEASE_TAG_PER_MODULE}).
 * Used by {@code release-cipipeline.jenkinsfile} and optionally elsewhere.
 */

/** Pipeline step hook — use {@code loadSharedLibVarScript('releasePipelineReleaseTags')} for helper methods. */
def call() {
    return this
}

/** Parse allowlist CSV into a set of module ids (trim, drop empties). */
Set<String> parseAllowlist(String csv) {
    if (!csv?.toString()?.trim()) {
        return [] as Set
    }
    return csv.toString().split(',').collect { it.trim() }.findAll { it } as Set
}

/**
 * Parse UI value from Active Choices {@code RELEASE_TAG_PER_MODULE_CHOICES}: lines {@code moduleId=tag},
 * optional comma-separated pairs on one line; lines starting with {@code #} ignored.
 */
Map<String, String> parsePerModuleUi(String raw) {
    Map<String, String> m = [:]
    if (!raw?.toString()?.trim()) {
        return m
    }
    raw.toString().split(/\r?\n/).each { line ->
        def t = line.trim()
        if (!t || t.startsWith('#')) {
            return
        }
        t.split(',').each { part ->
            def p = part.trim()
            if (!p) {
                return
            }
            def eq = p.indexOf('=')
            if (eq > 0) {
                def k = p.substring(0, eq).trim()
                def v = p.substring(eq + 1).trim()
                if (k) {
                    m[k] = v
                }
            }
        }
    }
    return m
}

/**
 * Resolve Git release tag for one module. Allowlisted modules use only {@code perModuleMap}; never {@code globalReleaseTag}.
 */
String resolveForModule(String moduleName, Set<String> allowlist, Map perModuleMap, String globalReleaseTag) {
    def mod = moduleName?.toString()?.trim()
    if (!mod) {
        error '❌ resolveForModule: module name is empty.'
    }
    if (allowlist.contains(mod)) {
        def t = perModuleMap[mod]?.toString()?.trim()
        if (!t) {
            error "❌ Release tag is not given for module '${mod}'. Per-module tag is required (global RELEASE_TAG is not used for this module)."
        }
        return t
    }
    def g = globalReleaseTag?.toString()?.trim()
    if (!g) {
        error "❌ RELEASE_TAG is required for module '${mod}' (global release tag flow)."
    }
    return g
}
