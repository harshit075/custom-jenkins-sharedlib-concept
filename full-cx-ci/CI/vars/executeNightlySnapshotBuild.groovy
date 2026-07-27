// vars/executeNightlySnapshotBuild.groovy
//
// EXACT REPLICA OF: CustomerXP executeNightlySnapshotBuild.groovy
//
// PURPOSE: Orchestrates the full nightly snapshot build — resolves modules,
// branches, executes chain + parallel builds, publishes report, sends failure email.

def call(Map args = [:]) {
    def selection = loadSharedLibVarScript('resolveNightlyCiSelection').call()
    def coreModules    = selection.coreModuleList
    def instModules    = selection.instModuleList
    def jdkVersions    = selection.jdkVersions
    def runSecTools    = selection.runSecurityTools

    // Resolve branches for all modules
    def branchResolver = loadSharedLibVarScript('resolveNightlyBranchMap')
    def allModules = coreModules + instModules
    def branchMap  = branchResolver.call(allModules)

    def cfg = cxPipelineConfig(runSecTools)
    def serde = loadSharedLibVarScript('pipelineConfigSerde')
    if (serde) env.CX_PIPELINE_CFG_JSON = serde.pipelineConfigToJson(cfg)

    def workspaceBase = cfg.workspaceBase ?: env.CX_WORKSPACE_BASE

    // Sequential chain build (core modules)
    if (coreModules) {
        coreModules.each { modName ->
            def branch = branchMap[modName] ?: 'main'
            echo "🌙 Nightly chain build: ${modName} @ ${branch}"
            executeChainBuild(
                selectedModulesList: [modName],
                jdkVersions: jdkVersions,
                cxSmgBase: workspaceBase,
                isCoreType: true,
                customBranchOverride: branch,
                runSecurityTools: runSecTools,
                pipelineCfg: cfg
            )
        }
    }

    // Parallel installer builds
    if (instModules) {
        def parallelTasks = [:]
        instModules.each { modName ->
            def branch = branchMap[modName] ?: 'main'
            echo "🌙 Nightly parallel: ${modName} @ ${branch}"
            parallelTasks["nightly-${modName}"] = {
                executeChainBuild(
                    selectedModulesList: [modName],
                    jdkVersions: jdkVersions,
                    cxSmgBase: workspaceBase,
                    isCoreType: false,
                    customBranchOverride: branch,
                    runSecurityTools: runSecTools,
                    pipelineCfg: cfg
                )
            }
        }
        parallel parallelTasks
    }

    // Report
    def allForReport = allModules.collect { [moduleName: it, branchName: branchMap[it] ?: 'main'] }
    def reportName = env.JENKINS_SONAR_REPORT_NAME ?: env.CX_SONAR_REPORT_NAME ?: 'Nightly CI Report'
    publishSonarReport(modules: allForReport, reportName: reportName, failOnError: false)
}

return this
