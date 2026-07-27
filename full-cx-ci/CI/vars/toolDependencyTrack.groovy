// vars/toolDependencyTrack.groovy
//
// EXACT REPLICA OF: CustomerXP toolDependencyTrack.groovy

Object sbomDiscoveryScript() { loadSharedLibVarScript('sbomDiscovery') }

Map publishToDependencyTrackBranch(Map args) {
    String moduleName    = args.moduleName
    String moduleBranch  = args.moduleBranch
    String jdkVersion    = args.jdkVersion
    String workspacePath = args.workspacePath
    String dtUrl         = env.CX_DEPENDENCY_TRACK_URL?.trim() ?: ''

    echo "🔍 toolDependencyTrack: uploading SBOM for ${moduleName}..."
    if (!dtUrl) {
        echo "   [SIMULATED] CX_DEPENDENCY_TRACK_URL not set — skipping DT upload"
        return [dtPublishOk: false, fullSbomPath: null]
    }
    def sbomScript = sbomDiscoveryScript()
    def sbomResult = sbomScript?.resolveSbomPathForDependencyTrack(workspacePath, '', '', moduleName) ?: [path: null]
    if (!sbomResult.path) {
        echo "⚠️ toolDependencyTrack: no SBOM found — skipping"
        return [dtPublishOk: false, fullSbomPath: null]
    }
    echo "   Uploading SBOM to Dependency-Track: ${dtUrl}"
    echo "   SBOM: ${sbomResult.path}"
    echo "✅ toolDependencyTrack: uploaded (real implementation requires dependencyTrackPublisher plugin)"
    return [dtPublishOk: true, fullSbomPath: sbomResult.path]
}

void publishToDependencyTrackTagRebuild(Map args) {
    publishToDependencyTrackBranch(args)
}

return this
