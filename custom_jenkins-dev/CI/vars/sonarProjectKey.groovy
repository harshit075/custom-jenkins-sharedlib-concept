// vars/sonarProjectKey.groovy
//
// Single implementation of SonarQube project key derivation (scanner, publishSonarReport, QG API).

/**
 * Branch suffix for {@link #getSonarProjectKey}: strips {@code develop-}, maps default branches to empty suffix,
 * then replaces illegal characters for Sonar keys.
 *
 * @param emptyBranchMessage optional {@link #error} message when branch is blank (caller-specific)
 * @return sanitized suffix, or {@code ''} when the key should be module-only ({@code master}/{@code main}/{@code develop})
 */
String sanitizeBranchForProjectKey(String branchName, String emptyBranchMessage = null) {
    if (!branchName?.toString()?.trim()) {
        error(emptyBranchMessage ?: '❌ sanitizeBranchForProjectKey: branchName must be set.')
    }
    def sanitized = branchName.toString().trim()
    if (sanitized.startsWith('develop-')) {
        sanitized = sanitized.substring(8)
    }
    if (sanitized == 'master' || sanitized == 'main' || sanitized == 'develop') {
        return ''
    }
    return sanitized.replaceAll('[^a-zA-Z0-9.\\-]', '-')
}

/**
 * Sonar project key: {@code moduleName} only for default branches, else {@code moduleName-suffix}.
 *
 * @param emptyBranchMessage forwarded to {@link #sanitizeBranchForProjectKey} when branch is blank
 */
String getSonarProjectKey(String moduleName, String branchName, String emptyBranchMessage = null) {
    if (!moduleName?.toString()?.trim()) {
        error('❌ getSonarProjectKey: moduleName must be set.')
    }
    def mod = moduleName.toString().trim()
    def suffix = sanitizeBranchForProjectKey(branchName, emptyBranchMessage)
    return suffix ? "${mod}-${suffix}" : mod
}

return this
