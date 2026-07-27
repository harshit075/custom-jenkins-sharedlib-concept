// vars/releaseTagPerModule.groovy
//
// EXACT REPLICA OF: CustomerXP releaseTagPerModule.groovy

List<String> allowlistFromEnv() {
    return (env.JENKINS_RELEASE_TAG_PER_MODULE ?: '').split(',').collect { it.trim() }.findAll { it }
}

Map parsePerModuleChoices(String raw) {
    if (!raw?.trim()) return [:]
    def map = [:]
    raw.split(',').each { entry ->
        def parts = entry.trim().split('\\|', 2)
        if (parts.length == 2) map[parts[0].trim()] = parts[1].trim()
    }
    return map
}

boolean isAllowlisted(String moduleName) {
    return allowlistFromEnv().contains(moduleName)
}

String effectiveReleaseTagForModule(String moduleName, String globalReleaseTag, Map perModuleChoices) {
    if (!isAllowlisted(moduleName)) return globalReleaseTag
    return perModuleChoices[moduleName] ?: globalReleaseTag
}

return this
