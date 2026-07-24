// vars/resolveCxSmgDevPath.groovy
/**
 * Resolves CX_SMG dev workspace path. Jenkins env vars may be "" (empty string), not null —
 * Elvis ?: does not skip "", so we must trim and test non-empty explicitly.
 *
 * Order: env.CX_SMG_DEV → env.CX_SMG_DEV_DEFAULT → cxSmgBase (tools root).
 */
def call(String cxSmgBase) {
    for (def v in [env.CX_SMG_DEV, env.CX_SMG_DEV_DEFAULT, cxSmgBase]) {
        if (v == null) continue
        def s = v.toString().trim()
        if (s) return s
    }
    return cxSmgBase?.toString()?.trim() ?: ''
}
