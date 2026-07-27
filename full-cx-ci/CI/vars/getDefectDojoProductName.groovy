// vars/getDefectDojoProductName.groovy
//
// EXACT REPLICA OF: CustomerXP getDefectDojoProductName.groovy

def call(String branchName) {
    def rules = env.JENKINS_DOJO_PRODUCT_RULES?.trim() ?: ''
    if (!rules) return env.DOJO_PRODUCT_NAME?.trim() ?: 'CustomerXP'
    def lowerBranch = branchName?.toLowerCase() ?: ''
    for (def rule in rules.split(',')) {
        def parts = rule.trim().split(':', 2)
        if (parts.length == 2) {
            def substring = parts[0].trim().toLowerCase()
            def productName = parts[1].trim()
            if (lowerBranch.contains(substring)) return productName
        }
    }
    return env.DOJO_PRODUCT_NAME?.trim() ?: 'CustomerXP'
}

return this
