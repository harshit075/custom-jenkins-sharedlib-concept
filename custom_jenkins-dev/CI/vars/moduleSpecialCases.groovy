// vars/moduleSpecialCases.groovy
//
// Single place documenting and implementing **module-specific** build/clone behaviour that
// differs from the generic platform / product-modules layout.
//
// ── Cases (edit here when Git layout changes for a module) ──
//
// 1) GitLab namespace (discover.sh)
//    Default discovery: git ls-remote `platform/<module>` then `product-modules/<module>`.
//    Nested-group overrides: set env (Jenkins global or job):
//      CX_GITLAB_NAMESPACE_OVERRIDES_EXACT — comma-separated module:namespace (e.g. cmq-common:platform/component-utilities,...)
//      CX_GITLAB_NAMESPACE_OVERRIDES_REGEX — pattern:namespace rules, multiple rules separated by ;; (e.g. cmq-(client|driver):platform/component-sdk)
//    If nothing matches: discover.sh exits 1 (stderr message) — no fallback to platform.
//
// SBOM path discovery and SBOM_FILE hints: vars/sbomDiscovery.groovy
//
// NOTE: Do not use `private static final` fields here — Jenkins Pipeline CPS cannot resolve them from
// instance methods (MissingPropertyException). Use private methods returning maps instead.

/** Parses {@code env.CX_GITLAB_NAMESPACE_OVERRIDES_EXACT}: comma-separated {@code moduleId:namespace/path}. */
private Map parseGitlabNamespaceExactFromEnv() {
    String raw = (env.CX_GITLAB_NAMESPACE_OVERRIDES_EXACT ?: '').toString().trim()
    if (!raw) {
        return [:]
    }
    Map result = [:]
    raw.split(',').each { String seg ->
        String t = seg.trim()
        if (!t) {
            return
        }
        int idx = t.indexOf(':')
        if (idx < 0) {
            error("❌ moduleSpecialCases: invalid CX_GITLAB_NAMESPACE_OVERRIDES_EXACT entry (expected module:namespace): '${t}'")
        }
        String k = t.substring(0, idx).trim()
        String v = t.substring(idx + 1).trim()
        if (!k || !v) {
            error("❌ moduleSpecialCases: empty module or namespace in CX_GITLAB_NAMESPACE_OVERRIDES_EXACT: '${t}'")
        }
        result[k] = v
    }
    return result
}

/** Parses {@code env.CX_GITLAB_NAMESPACE_OVERRIDES_REGEX}: rules {@code pattern:namespace}, multiple rules separated by {@code ;;}. */
private List<Map> parseGitlabNamespaceRegexFromEnv() {
    String raw = (env.CX_GITLAB_NAMESPACE_OVERRIDES_REGEX ?: '').toString().trim()
    if (!raw) {
        return []
    }
    List<Map> out = []
    raw.split(';;').each { String rule ->
        String t = rule.trim()
        if (!t) {
            return
        }
        int idx = t.indexOf(':')
        if (idx < 0) {
            error("❌ moduleSpecialCases: invalid CX_GITLAB_NAMESPACE_OVERRIDES_REGEX entry (expected pattern:namespace): '${t}'")
        }
        String pat = t.substring(0, idx).trim()
        String ns = t.substring(idx + 1).trim()
        if (!pat || !ns) {
            error("❌ moduleSpecialCases: empty pattern or namespace in CX_GITLAB_NAMESPACE_OVERRIDES_REGEX: '${t}'")
        }
        out << [pattern: pat, namespace: ns]
    }
    return out
}

/** Exact module id → GitLab namespace segment (when generic ls-remote probes miss). From {@code CX_GITLAB_NAMESPACE_OVERRIDES_EXACT}. */
private Map gitlabNamespaceExactMap() {
    parseGitlabNamespaceExactFromEnv()
}

/** Bash extended-regex → namespace (evaluated after exact-module lines). From {@code CX_GITLAB_NAMESPACE_OVERRIDES_REGEX}. */
private List<Map> gitlabNamespaceRegexList() {
    parseGitlabNamespaceRegexFromEnv()
}

/**
 * Full {@code discover.sh} body: generic {@code git ls-remote} probes, then special-case {@code elif}s.
 * If nothing matches, prints to stderr and exits {@code 1} (no silent {@code platform} default).
 *
 * @param gitHost From {@code cxPipelineConfig}
 * @param cxGitOrg Git org segment (same as {@code env.CX_GIT_ORG})
 * @param moduleName Module id for this build
 */
String buildDiscoverNamespaceScript(String gitHost, String cxGitOrg, String moduleName) {
    def mod = moduleName?.toString()?.trim() ?: ''
    if (!mod) {
        error('❌ moduleSpecialCases.buildDiscoverNamespaceScript: moduleName is required.')
    }

    def elifExact = new StringBuilder()
    gitlabNamespaceExactMap().each { String mid, String ns ->
        elifExact << "                        elif [ \"${mod}\" == \"${mid}\" ]; then echo \"${ns}\"\n"
    }

    def elifRegex = new StringBuilder()
    gitlabNamespaceRegexList().each { Map m ->
        def pat = m.pattern?.toString()
        def ns = m.namespace?.toString()
        if (pat && ns) {
            elifRegex << "                        elif [[ \"${mod}\" =~ ${pat} ]]; then echo \"${ns}\"\n"
        }
    }

    return """#!/bin/bash
                        GIT_ROOT="https://${gitHost}/${cxGitOrg}"
                        if git ls-remote -h "\${GIT_ROOT}/platform/${mod}.git" HEAD >/dev/null 2>&1; then echo "platform"
                        elif git ls-remote -h "\${GIT_ROOT}/product-modules/${mod}.git" HEAD >/dev/null 2>&1; then echo "product-modules"
${elifExact}${elifRegex}                        else echo "moduleSpecialCases: could not resolve GitLab namespace for module '${mod}' (generic ls-remote probes and special-cases did not match). Set CX_GITLAB_NAMESPACE_OVERRIDES_EXACT / CX_GITLAB_NAMESPACE_OVERRIDES_REGEX or fix the module id." >&2; exit 1; fi
"""
}
