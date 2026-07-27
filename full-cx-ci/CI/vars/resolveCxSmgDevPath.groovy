// vars/resolveCxSmgDevPath.groovy
//
// EXACT REPLICA OF: CustomerXP resolveCxSmgDevPath.groovy
//
// PURPOSE:
// Resolves the workspace/build-tools root path for a module build.
// CustomerXP uses SMG (Source Management Generator) tools under CX_SMG_DEV.
// We use this same path for the dedicated workspace per module/JDK.
//
// Resolution order (first non-empty wins):
//   1. env.CX_SMG_DEV       (explicitly set per-run)
//   2. env.CX_SMG_DEV_DEFAULT (Jenkins global default)
//   3. cxSmgBase arg         (passed from Jenkinsfile)
//   4. CX_WORKSPACE_BASE     (fallback)

def call(String cxSmgBase = null) {
    for (def v in [env.CX_SMG_DEV, env.CX_SMG_DEV_DEFAULT, cxSmgBase, env.CX_WORKSPACE_BASE]) {
        if (v == null) continue
        def s = v.toString().trim()
        if (s) return s
    }
    // Last resort — use Jenkins workspace
    return env.WORKSPACE ?: '/var/jenkins_home/cx-builds'
}

return this
