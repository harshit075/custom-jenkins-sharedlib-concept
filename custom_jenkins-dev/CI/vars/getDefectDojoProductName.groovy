// vars/getDefectDojoProductName.groovy
//
// Resolves DefectDojo product name from the Git branch using Jenkins global configuration.
//
// Required global property:
//   JENKINS_DOJO_PRODUCT_RULES — comma-separated ordered rules: substring:ProductName,substring:ProductName
//   First matching rule wins (substring match is case-insensitive on the branch name).
// Example (Manage Jenkins → Global properties):
//   neo5:Clari5_Neo5,4.10:Clari5_410
//
// If JENKINS_DOJO_PRODUCT_RULES is unset/blank, or the branch matches no rule, the step fails (no hardcoded fallback).

/**
 * @param branchName Git branch (e.g. develop-4.10.x, develop-neo5.x)
 * @return DefectDojo product name from the first matching rule
 */
def call(String branchName) {
    def rulesStr = env.JENKINS_DOJO_PRODUCT_RULES?.toString()?.trim()
    if (!rulesStr) {
        error(
            '❌ JENKINS_DOJO_PRODUCT_RULES must be set in Jenkins global properties. ' +
            'Format: substring:ProductName,substring:ProductName (ordered; first match wins). ' +
            'Example: neo5:Clari5_Neo5,4.10:Clari5_410'
        )
    }

    def branch = branchName?.toString()?.trim()
    if (!branch) {
        error('❌ getDefectDojoProductName: branchName must be set.')
    }
    def branchLower = branch.toLowerCase()

    def segments = rulesStr.split(',')
    for (String rawSeg : segments) {
        def seg = rawSeg?.trim()
        if (!seg) {
            continue
        }
        def idx = seg.indexOf(':')
        if (idx < 0) {
            error("❌ JENKINS_DOJO_PRODUCT_RULES invalid segment (expected substring:product): '${seg}'")
        }
        def needle = seg.substring(0, idx).trim()
        def product = seg.substring(idx + 1).trim()
        if (!needle) {
            error("❌ JENKINS_DOJO_PRODUCT_RULES empty substring before ':' in: '${seg}'")
        }
        if (!product) {
            error("❌ JENKINS_DOJO_PRODUCT_RULES empty product after ':' in: '${seg}'")
        }
        if (branchLower.contains(needle.toLowerCase())) {
            return product
        }
    }

    error(
        "❌ getDefectDojoProductName: branch '${branch}' matched no rule in JENKINS_DOJO_PRODUCT_RULES. " +
        "Add a substring rule or fix the branch name."
    )
}

return this
