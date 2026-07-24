// vars/securityToolsCommon.groovy
// Shared bits used by multiple security tool scripts (not a “tool” itself).

/** True if {@code moduleName} is listed in {@code env.CX_GRADLE_ONLY_MODULES}. */
boolean isGradleOnlyModule(String moduleName) {
    String m = moduleName?.toString()?.trim()
    if (!m) {
        return false
    }
    List<String> ids = (env.CX_GRADLE_ONLY_MODULES ?: '').split(',').collect { it.trim() }.findAll { it }
    return ids.contains(m)
}

/** Nexus short segment from branch name (same semantics as buildModulesWithJDK / Dependency-Track versioning). */
String getNexusShortVersion(String branchOrTag) {
    if (!branchOrTag?.toString()?.trim()) {
        error('❌ getNexusShortVersion: branchOrTag must be set.')
    }
    branchOrTag = branchOrTag.toString().trim()
    def m = (branchOrTag =~ /(?:develop|release)-(.+?)\.x$/)
    if (m) return m[0][1]
    m = (branchOrTag =~ /(?:develop|release)-(.+)$/)
    if (m) return m[0][1]
    return branchOrTag
}

return this
