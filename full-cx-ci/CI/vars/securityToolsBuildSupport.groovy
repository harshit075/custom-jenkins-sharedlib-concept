// vars/securityToolsBuildSupport.groovy
//
// EXACT REPLICA OF: CustomerXP securityToolsBuildSupport.groovy
// Deprecated facade — delegates to securityToolsOrchestrator

void runBranchSecurityTooling(Map args) {
    loadSharedLibVarScript('securityToolsOrchestrator').runBranchSecurityTooling(args)
}

void runTagRebuildSecurityTooling(Map args) {
    loadSharedLibVarScript('securityToolsOrchestrator').runTagRebuildSecurityTooling(args)
}

return this
