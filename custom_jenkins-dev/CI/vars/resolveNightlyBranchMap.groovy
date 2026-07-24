/**
 * Resolves a Git branch per module for nightly SNAPSHOT CI (develop* preference via JENKINS_BRANCH_SUFFIX).
 */
def call(List<String> moduleNames) {
    if (!moduleNames || moduleNames.isEmpty()) {
        return [:]
    }

    def getDefaultsScript = loadSharedLibVarScript('getDefaults')
    if (!getDefaultsScript) {
        error '❌ resolveNightlyBranchMap: getDefaults could not be loaded from the shared library.'
    }

    def apiUrl = (env.CX_GITLAB_API_URL ?: '').trim()
    def gitOrg = (env.CX_GITLAB_ORG ?: '').trim()
    def namespaces = (env.CX_GITLAB_NAMESPACES ?: '')
        .split(',')
        .collect { it.trim() }
        .findAll { it }
    def credentialsId = (env.CX_GITLAB_TOKEN_CREDENTIAL_ID ?: '').trim()
    def fallbackBranch = getDefaultsScript.getDefaultBranch()
    def branchPrefix = getDefaultsScript.getBranchSuffix()
    def preferredBranch = getDefaultsScript.getDefaultBranch()
    int maxPages = getDefaultsScript.getGitlabBranchMaxPages()
    int perPage = getDefaultsScript.getGitlabBranchPerPage()

    if (!apiUrl || !gitOrg || !namespaces || !credentialsId) {
        echo "⚠️ resolveNightlyBranchMap: GitLab API globals incomplete — using JENKINS_DEFAULT_BRANCH (${fallbackBranch}) for all modules."
        return moduleNames.collectEntries { [(it): fallbackBranch] }
    }

    def branchMap = [:]
    withCredentials([string(credentialsId: credentialsId, variable: 'GITLAB_PRIVATE_TOKEN')]) {
        moduleNames.each { moduleName ->
            def branches = []
            def resolved = false
            namespaces.each { namespace ->
                if (resolved) {
                    return
                }
                try {
                    def projectPath = "${gitOrg}/${namespace}/${moduleName}"
                    def encodedPath = java.net.URLEncoder.encode(projectPath, 'UTF-8').replace('+', '%20')
                    def allNames = []
                    def page = 1
                    while (page <= maxPages) {
                        def url = "${apiUrl}/projects/${encodedPath}/repository/branches?per_page=${perPage}&page=${page}"
                        def conn = new URL(url).openConnection()
                        conn.setRequestProperty('PRIVATE-TOKEN', env.GITLAB_PRIVATE_TOKEN)
                        def code = conn.responseCode
                        if (code != 200) {
                            break
                        }
                        def json = new groovy.json.JsonSlurper().parseText(conn.inputStream.text)
                        if (!json || json.isEmpty()) {
                            break
                        }
                        json.each { allNames << it.name }
                        if (json.size() < perPage) {
                            break
                        }
                        page++
                    }
                    def matchingBranches = allNames.unique().findAll { it.startsWith(branchPrefix) }.sort()
                    if (matchingBranches) {
                        branches = matchingBranches
                        resolved = true
                    }
                } catch (Throwable t) {
                    echo "⚠️ resolveNightlyBranchMap: GitLab lookup failed for '${moduleName}' (${namespace}): ${t.message}"
                }
            }
            if (branches) {
                def chosen = branches.find { it == preferredBranch } ?: branches[0]
                branchMap[moduleName] = chosen
                echo "ℹ️ Nightly branch for '${moduleName}': ${chosen}"
            } else {
                branchMap[moduleName] = fallbackBranch
                echo "⚠️ resolveNightlyBranchMap: no ${branchPrefix}* branch for '${moduleName}' — fallback ${fallbackBranch}"
            }
        }
    }
    return branchMap
}

return this
