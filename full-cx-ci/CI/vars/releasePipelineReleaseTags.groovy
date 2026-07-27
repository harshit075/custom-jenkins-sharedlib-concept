// vars/releasePipelineReleaseTags.groovy
//
// EXACT REPLICA OF: CustomerXP releasePipelineReleaseTags.groovy

def call() { return this }

List<String> parseAllowlist(String csv) {
    return (csv ?: '').split(',').collect { it.trim() }.findAll { it }
}

Map parsePerModuleUi(String raw) {
    if (!raw?.trim()) return [:]
    def map = [:]
    raw.split(',').each { entry ->
        def parts = entry.trim().split('=', 2)
        if (parts.length == 2) map[parts[0].trim()] = parts[1].trim()
    }
    return map
}

String resolveForModule(String moduleName, String globalReleaseTag, Map perModuleMap, List<String> allowlist) {
    if (!allowlist?.contains(moduleName)) return globalReleaseTag
    return perModuleMap[moduleName] ?: globalReleaseTag
}

return this
