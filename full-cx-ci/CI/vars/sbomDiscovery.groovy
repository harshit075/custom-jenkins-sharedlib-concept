// vars/sbomDiscovery.groovy
//
// EXACT REPLICA OF: CustomerXP sbomDiscovery.groovy
//
// PURPOSE:
// Discovers SBOM (Software Bill of Materials) files generated during the build.
// CycloneDX Maven/Gradle plugins generate bom.json or bom.xml in standard paths.
// This script checks multiple possible locations and returns the first found.

private List<String> sbomRelativeSuffixes(String moduleName) {
    return [
        "${moduleName}_bom.json",
        "bom.json",
        "build/reports/bom.json",
        "build/reports/bom.xml",
        "target/bom.xml",
        "target/bom.json"
    ]
}

String getRelativeSbomPath(String moduleName) {
    def overrides = [:]  // no per-module overrides locally
    return overrides[moduleName] ?: sbomRelativeSuffixes(moduleName).first() ?: 'build/reports/bom.xml'
}

Map findFirstExistingSbomDiscovery(String workspacePath, String gitOrg, String namespace, String moduleName, Closure exists) {
    def suffixes = sbomRelativeSuffixes(moduleName)
    for (suffix in suffixes) {
        def absPath = "${workspacePath}/${suffix}"
        if (exists(absPath)) {
            return [path: absPath, message: "Found SBOM: ${suffix}"]
        }
    }
    return null
}

Map resolveSbomPathForDependencyTrack(String workspacePath, String gitOrg, String namespace, String moduleName) {
    def result = findFirstExistingSbomDiscovery(workspacePath, gitOrg, namespace, moduleName) { path ->
        fileExists(path)
    }
    if (result) {
        echo "✅ sbomDiscovery: ${result.message}"
        return [path: result.path]
    }
    echo "⚠️ sbomDiscovery: no SBOM file found for ${moduleName} in workspace ${workspacePath}"
    return [path: null]
}

return this
