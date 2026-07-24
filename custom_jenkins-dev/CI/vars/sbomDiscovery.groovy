// vars/sbomDiscovery.groovy
//
// CycloneDX SBOM path discovery and helpers for SBOM_FILE / Dependency-Track.
// Load via {@code loadSharedLibVarScript('sbomDiscovery')} wherever an SBOM path is needed.
//
// Discovery order: for each relative suffix, tries workspace root then
// {@code .../<gitOrg>/<namespace>/<module>/}. Gradle CycloneDX often emits
// {@code build/reports/bom.json}; Maven often {@code target/bom.xml}.
// Per-module overrides: {@link #sbomRelativePathOverrideByModule}.
//
// NOTE: Do not use {@code private static final} fields — Jenkins Pipeline CPS can fail on them.
// Use private methods returning maps/lists instead.

/**
 * Relative path segments (under workspace root or under {@code .../org/namespace/module}) tried in order.
 */
private List<String> sbomRelativeSuffixes(String moduleName) {
    def m = moduleName?.toString()?.trim() ?: ''
    [
        "${m}_bom.json",
        'bom.json',
        'build/reports/bom.json',
        'build/reports/bom.xml',
        'target/bom.xml',
    ]
}

/** Module id → full relative path override for {@code SBOM_FILE} hint (optional). */
private Map sbomRelativePathOverrideByModule() {
    [:]
}

private List<Map> orderedSbomDiscoveryPairs(String workspace, String projectDir, String moduleName) {
    def s = sbomRelativeSuffixes(moduleName)
    List<Map> pairs = []
    for (String suffix : s) {
        pairs << [dir: workspace, suffix: suffix]
        pairs << [dir: projectDir, suffix: suffix]
    }
    pairs
}

private String sbomDiscoveryLabel(String relativeSuffix, String moduleName, boolean projectDir) {
    def place = projectDir ? 'project directory' : 'workspace root'
    def mod = moduleName?.toString()?.trim() ?: ''
    if (relativeSuffix == "${mod}_bom.json") {
        return "Found SBOM: ${mod}_bom.json in ${place} (Gradle JSON format)"
    }
    if (relativeSuffix == 'bom.json') {
        return "Found SBOM: bom.json in ${place} (Gradle JSON format)"
    }
    if (relativeSuffix == 'build/reports/bom.json') {
        return "Found SBOM: build/reports/bom.json in ${place} (Gradle CycloneDX JSON)"
    }
    if (relativeSuffix == 'build/reports/bom.xml') {
        return "Found SBOM: build/reports/bom.xml in ${place} (Gradle XML format)"
    }
    if (relativeSuffix == 'target/bom.xml') {
        return "Found SBOM: target/bom.xml in ${place} (Maven XML format)"
    }
    return "Found SBOM: ${relativeSuffix} in ${place}"
}

/**
 * Paths to print when SBOM is missing (workspace vs clone dir).
 */
Map getSbomMissEchoSections(String workspace, String gitOrg, String namespace, String moduleName) {
    def mod = moduleName?.toString()?.trim() ?: ''
    def ws = workspace?.toString()?.trim() ?: ''
    def org = gitOrg?.toString()?.trim() ?: ''
    def ns = namespace?.toString()?.trim() ?: ''
    def projDir = "${ws}/${org}/${ns}/${mod}"
    def suf = sbomRelativeSuffixes(mod)
    [
        workspacePaths: suf.collect { "${ws}/${it}" },
        projectDir    : projDir,
        projectPaths  : suf.collect { "${projDir}/${it}" },
    ]
}

/**
 * Finds the first existing SBOM file using the shared discovery order.
 *
 * @param exists closure taking absolute path returning boolean (typically {@code fileExists})
 * @return map {@code [path: String, message: String]} or {@code null}
 */
Map findFirstExistingSbomDiscovery(String workspace, String gitOrg, String namespace, String moduleName, Closure exists) {
    def mod = moduleName?.toString()?.trim()
    if (!mod) {
        return null
    }
    def ws = workspace?.toString()?.trim() ?: ''
    def org = gitOrg?.toString()?.trim() ?: ''
    def ns = namespace?.toString()?.trim() ?: ''
    def projDir = "${ws}/${org}/${ns}/${mod}"

    for (Map pair : orderedSbomDiscoveryPairs(ws, projDir, mod)) {
        def abs = "${pair.dir}/${pair.suffix}"
        if (exists(abs)) {
            boolean inProjectClone = (pair.dir == projDir)
            return [path: abs, message: sbomDiscoveryLabel(pair.suffix as String, mod, inProjectClone)]
        }
    }
    return null
}

/**
 * Hint path for {@code SBOM_FILE}: per-module override, else first suffix in discovery order.
 */
String getRelativeSbomPath(String moduleName) {
    def id = moduleName?.toString()?.trim()
    if (!id) {
        return 'build/reports/bom.xml'
    }
    def override = sbomRelativePathOverrideByModule()[id]
    if (override) {
        return override
    }
    def suf = sbomRelativeSuffixes(id)
    return suf ? suf[0] : 'build/reports/bom.xml'
}

/**
 * Resolves absolute SBOM path for Dependency-Track (echoes discovery message when found).
 *
 * @return map {@code [path: String or null]} — path is absolute when present
 */
Map resolveSbomPathForDependencyTrack(String workspaceAbsolutePath, String gitOrg, String namespace, String moduleName) {
    def discovery = findFirstExistingSbomDiscovery(
        workspaceAbsolutePath,
        gitOrg,
        namespace,
        moduleName,
        { String p -> fileExists(p) }
    )
    if (discovery?.message) {
        echo "   ${discovery.message}"
    }
    [path: discovery?.path]
}

/**
 * Logs standard “SBOM not found” messages for Dependency-Track skip paths.
 */
void echoSbomMissForDependencyTrack(
    String workspaceAbsolutePath,
    String gitOrg,
    String namespace,
    String moduleName,
    String skipContextSuffix,
    boolean includeMavenGradleHints
) {
    def miss = getSbomMissEchoSections(workspaceAbsolutePath, gitOrg, namespace, moduleName)
    echo '⚠️ SBOM file not found. Checked locations:'
    echo '   Workspace root:'
    miss.workspacePaths.each { echo "     - ${it}" }
    echo "   Project directory (${miss.projectDir}):"
    miss.projectPaths.each { echo "     - ${it}" }
    echo "⚠️ Skipping Dependency-Track publish for ${moduleName}${skipContextSuffix}."
    echo '⚠️ Ensure CycloneDX plugin is properly configured in your build tool (Maven/Gradle).'
    if (includeMavenGradleHints) {
        echo '⚠️ For Gradle projects, ensure org.cyclonedx.bom plugin is applied and gradle cyclonedxBom succeeds.'
        echo '⚠️ For Maven projects, ensure cyclonedx-maven-plugin is configured in pom.xml.'
    }
    echo '⚠️ Note: Gradle CycloneDX often writes build/reports/bom.json or {moduleName}_bom.json in the project root.'
    echo "⚠️ Current workspace: ${workspaceAbsolutePath}"
}

return this
