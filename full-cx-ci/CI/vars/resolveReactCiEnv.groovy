// vars/resolveReactCiEnv.groovy
//
// EXACT REPLICA OF: CustomerXP resolveReactCiEnv.groovy
//
// PURPOSE:
// Single source of truth for whether CI=true should be exported for React/CRA builds.
// When CI=true, react-scripts treats warnings as errors — breaks many repos.
// CX_REACT_CI_STRICT=false (default) keeps builds permissive locally.
//
// Used in buildModulesWithJDK to set CI env var before npm build.

def call() {
    return effective(null)
}

def effective(Map pipelineCfg) {
    // Check injected config first (from pipelineCfg Map)
    if (pipelineCfg != null) {
        def v = pipelineCfg.reactCiExport?.toString()?.trim()
        if (v in ['true', 'false']) return v
    }
    return parseCxReactCiStrict()
}

private String parseCxReactCiStrict() {
    def v = env.CX_REACT_CI_STRICT?.toString()?.trim()?.toLowerCase()
    if (v == 'true') return 'true'
    return 'false'  // Default: do NOT set CI=true (permissive builds)
}

return this
