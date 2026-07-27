// vars/sonarProjectKey.groovy
//
// EXACT REPLICA OF: CustomerXP sonarProjectKey.groovy

String sanitizeBranchForProjectKey(String branchName, String emptyBranchMessage = null) {
    if (!branchName?.trim()) return ''
    def sanitized = branchName.trim().replaceAll(/^develop-/, '').replaceAll(/^release-/, '')
    if (sanitized in ['master', 'main', 'develop']) return ''
    return sanitized.replaceAll(/[^a-zA-Z0-9_\-:]/, '-')
}

String getSonarProjectKey(String moduleName, String branchName, String emptyBranchMessage = null) {
    def suffix = sanitizeBranchForProjectKey(branchName, emptyBranchMessage)
    return suffix ? "${moduleName}-${suffix}" : moduleName
}

return this
