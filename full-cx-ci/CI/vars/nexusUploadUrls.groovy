// vars/nexusUploadUrls.groovy
//
// EXACT REPLICA OF: CustomerXP nexusUploadUrls.groovy
//
// PURPOSE:
// Constructs Nexus artifact upload/download URLs for module artifacts.
// CustomerXP uploads built JARs/WARs to Nexus after each successful build.
//
// ADAPTED FOR LOCAL JENKINS:
// No Nexus server available — methods return simulated URLs and log
// what would have been uploaded. Same method signatures preserved.

def getSnapshotRepoUrl(Map cfg = null) {
    def nexusBase = cfg?.nexusBaseUrl ?: env.CX_NEXUS_URL?.trim() ?: ''
    if (!nexusBase) {
        echo "ℹ️ nexusUploadUrls: CX_NEXUS_URL not set — Nexus upload simulated"
        return 'http://nexus-not-configured/repository/maven-snapshots'
    }
    return "${nexusBase}/repository/maven-snapshots"
}

def getReleaseRepoUrl(Map cfg = null) {
    def nexusBase = cfg?.nexusBaseUrl ?: env.CX_NEXUS_URL?.trim() ?: ''
    if (!nexusBase) {
        return 'http://nexus-not-configured/repository/maven-releases'
    }
    return "${nexusBase}/repository/maven-releases"
}

def getModuleArtifactPath(String moduleName, String version, String buildTypeSuffix = '-SNAPSHOT') {
    def groupId  = "com.customerxp.${moduleName.replaceAll('-', '.')}"
    def artifact = moduleName
    def ver      = "${version}${buildTypeSuffix}"
    return "com/customerxp/${moduleName.replaceAll('-', '/')}/${ver}/${artifact}-${ver}.jar"
}

def getFullUploadUrl(String moduleName, String version, String buildTypeSuffix = '-SNAPSHOT', Map cfg = null) {
    def repoUrl = buildTypeSuffix.contains('SNAPSHOT') ? getSnapshotRepoUrl(cfg) : getReleaseRepoUrl(cfg)
    def path    = getModuleArtifactPath(moduleName, version, buildTypeSuffix)
    return "${repoUrl}/${path}"
}

def simulateNexusUpload(String moduleName, String version, String buildTypeSuffix = '-SNAPSHOT', Map cfg = null) {
    def url = getFullUploadUrl(moduleName, version, buildTypeSuffix, cfg)
    echo "📦 [NEXUS SIMULATED] Would upload: ${moduleName}-${version}${buildTypeSuffix}.jar"
    echo "   Target URL: ${url}"
    echo "   (Set CX_NEXUS_URL in Jenkins globals to enable real Nexus upload)"
}

return this
