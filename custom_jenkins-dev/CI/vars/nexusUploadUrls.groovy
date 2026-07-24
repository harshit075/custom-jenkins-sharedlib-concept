// vars/nexusUploadUrls.groovy
//
// Nexus raw/hosted upload paths for installer artifacts. Used by mainBuildAndSonarScript / tagRebuildBuildScript.

/**
 * Full URL prefix for a PUT to Nexus (everything before the filename). The shell appends
 * {@code $(basename ${INST_FILE})}.
 *
 * <p>Uses path segment {@code repository/package-images/modules/} — single place to change if Nexus layout changes.
 *
 * @param nexusBaseUrl Normalized base URL ending with {@code /} (see {@code cxPipelineConfig}).
 * @param moduleName Repository module name (URL segment).
 * @param pathSegment Version/path folder (e.g. {@code nexusPathDir} or {@code nexusPathDirForTag}).
 */
String nexusPackageImagesUploadUrlPrefix(String nexusBaseUrl, String moduleName, String pathSegment) {
    final String packageImagesModulesPrefix = 'repository/package-images/modules/'
    def base = (nexusBaseUrl ?: '').toString().trim()
    if (base && !base.endsWith('/')) {
        base = base + '/'
    }
    def mod = (moduleName ?: '').toString().trim()
    def seg = (pathSegment ?: '').toString().trim()
    return "${base}${packageImagesModulesPrefix}${mod}/${seg}/"
}

/**
 * True if {@code moduleId} appears in {@code CX_CREINSTALLER_MODULES} (comma-separated), matching bash upload logic.
 */
boolean nexusCxCreinstallerModule(String moduleId, String cxCreinstallerModulesCsv) {
    def mid = (moduleId ?: '').toString().trim()
    if (!mid) {
        return false
    }
    def raw = (cxCreinstallerModulesCsv ?: '').toString().trim()
    if (!raw) {
        return false
    }
    return raw.split(',').collect { it.trim() }.findAll { it }.contains(mid)
}

/**
 * Basename of the artifact after the same normalization as {@code mainBuildAndSonarScript} / {@code tagRebuildBuildScript}
 * (curl PUT uses prefix + this name).
 * <p>CC / creinstaller: {@code cc-platform-jdk}{cleanJdk}{@code -}{tail}{@code -installer.bash} where tail is {@code nexusPathDir} with leading {@code jdk}{cleanJdk}{@code -} stripped.
 * <p>Other installers: {@code moduleName}{@code -}{nexusPathDir}{@code .tgz}.
 */
String nexusPackageImagesInstallerBasename(String moduleName, String cleanJdk, String nexusPathDir, boolean isCreinstallerModule) {
    def mod = (moduleName ?: '').toString().trim()
    def jdk = (cleanJdk ?: '').toString().trim()
    def seg = (nexusPathDir ?: '').toString().trim()
    if (isCreinstallerModule) {
        def jdkPrefix = "jdk${jdk}-"
        def tail = seg.startsWith(jdkPrefix) ? seg.substring(jdkPrefix.length()) : seg
        return "cc-platform-jdk${jdk}-${tail}-installer.bash"
    }
    return "${mod}-${seg}.tgz"
}

/** Full raw-repo URL to the uploaded installer (directory prefix + canonical basename). */
String nexusPackageImagesInstallerFileUrl(String nexusBaseUrl, String moduleName, String pathSegment, String installerBasename) {
    return nexusPackageImagesUploadUrlPrefix(nexusBaseUrl, moduleName, pathSegment) + (installerBasename ?: '').toString().trim()
}
