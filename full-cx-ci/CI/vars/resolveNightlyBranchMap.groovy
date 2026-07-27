// vars/resolveNightlyBranchMap.groovy
//
// EXACT REPLICA OF: CustomerXP resolveNightlyBranchMap.groovy
//
// PURPOSE: Resolves a default branch per module for nightly builds.
// CustomerXP calls GitLab API to find the latest develop branch.
// We use moduleSpecialCases defaults since we have no GitLab API.

def call(List<String> moduleIds) {
    def branchMap = [:]
    def moduleSpecialCasesScript = loadSharedLibVarScript('moduleSpecialCases')
    def defaultBranch = env.CX_DEFAULT_BRANCH?.trim() ?: 'main'

    moduleIds.each { moduleId ->
        def branch = defaultBranch
        if (moduleSpecialCasesScript) {
            def discovered = moduleSpecialCasesScript.getDefaultBranch(moduleId)
            if (discovered) branch = discovered
        }
        branchMap[moduleId] = branch
        echo "   resolveNightlyBranchMap: ${moduleId} → ${branch}"
    }
    return branchMap
}

return this
