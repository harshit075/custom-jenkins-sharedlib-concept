// vars/securityToolsBuildSupport.groovy
// Deprecated facade — implementation lives in toolSonarqube.groovy, toolSbomJacoco.groovy,
// toolDependencyTrack.groovy, toolDefectdojo.groovy, securityToolsOrchestrator.groovy.

void runBranchSecurityTooling(Map args) {
    loadSharedLibVarScript('securityToolsOrchestrator').runBranchSecurityTooling(args)
}

void runTagRebuildSecurityTooling(Map args) {
    loadSharedLibVarScript('securityToolsOrchestrator').runTagRebuildSecurityTooling(args)
}

return this
