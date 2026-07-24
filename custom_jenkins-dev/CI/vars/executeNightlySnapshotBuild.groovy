/**
 * Nightly SNAPSHOT build orchestration (core + installer, CX_CHAIN_ORDER) — no release tags.
 */
def call(Map args) {
    List<String> coreModuleList = (args.coreModuleList ?: []) as List
    List<String> instModuleList = (args.instModuleList ?: []) as List
    Map coreBranchMap = (args.coreBranchMap ?: [:]) as Map
    Map instBranchMap = (args.instBranchMap ?: [:]) as Map
    String jdkVersions = (args.jdkVersions ?: '').toString().trim()
    String cxSmgBase = args.cxSmgBase?.toString()
    if (!jdkVersions) {
        error '❌ executeNightlySnapshotBuild: jdkVersions is required (from CX_JDK_VERSION_DEFAULT / JENKINS_DEFAULT_JDKS).'
    }

    def getDefaultsScript = loadSharedLibVarScript('getDefaults')
    if (!getDefaultsScript) {
        error '❌ executeNightlySnapshotBuild: getDefaults could not be loaded from the shared library.'
    }
    boolean runSecurityTools = args.runSecurityTools != null
        ? (args.runSecurityTools as boolean)
        : getDefaultsScript.getRunSecurityTools()

    def chainOrderList = (env.CX_CHAIN_ORDER ?: '').tokenize(',').collect { it.trim() }.findAll { it }
    def chainIndexMap = [:]
    chainOrderList.eachWithIndex { moduleName, idx ->
        if (!chainIndexMap.containsKey(moduleName)) {
            chainIndexMap[moduleName] = idx
        }
    }

    def orderModulesByChain = { List<String> modules ->
        def normalized = (modules ?: []).collect { it?.toString()?.trim() }.findAll { it }
        def ordered = []
        def selectedSet = normalized as Set
        chainOrderList.each { chainMod ->
            if (selectedSet.contains(chainMod)) {
                ordered << chainMod
            }
        }
        normalized.each { modName ->
            if (!chainIndexMap.containsKey(modName) && !ordered.contains(modName)) {
                ordered << modName
            }
        }
        return ordered
    }
    def warnIfMissingInChain = { List<String> modules, String laneName ->
        def missing = (modules ?: []).findAll { it && !chainIndexMap.containsKey(it) }.unique()
        if (!missing.isEmpty()) {
            echo "⚠️ ${laneName} module(s) missing in CX_CHAIN_ORDER (placed at end): ${missing.join(', ')}"
        }
    }

    def sequentialInstallerSpecialSet = getDefaultsScript.getSequentialInstallerModuleIds() as Set
    def sequentialInstallerModules = instModuleList.findAll { sequentialInstallerSpecialSet.contains(it) }
    def parallelInstallerModules = instModuleList.findAll { !sequentialInstallerSpecialSet.contains(it) }

    warnIfMissingInChain(coreModuleList, 'core')
    warnIfMissingInChain(instModuleList, 'installer')

    def orderedCoreModuleList = orderModulesByChain(coreModuleList)
    def orderedSequentialInstallerModules = orderModulesByChain(sequentialInstallerModules)
    def orderedParallelInstallerModules = orderModulesByChain(parallelInstallerModules)

    def sequentialBuildPlan = []
    def sequentialInstallerSet = orderedSequentialInstallerModules as Set
    def coreSet = orderedCoreModuleList as Set
    def orderedSequentialNames = []
    chainOrderList.each { chainMod ->
        if (coreSet.contains(chainMod) || sequentialInstallerSet.contains(chainMod)) {
            orderedSequentialNames << chainMod
        }
    }
    orderedCoreModuleList.each { modName ->
        if (!chainIndexMap.containsKey(modName) && !orderedSequentialNames.contains(modName)) {
            orderedSequentialNames << modName
        }
    }
    orderedSequentialInstallerModules.each { modName ->
        if (!chainIndexMap.containsKey(modName) && !orderedSequentialNames.contains(modName)) {
            orderedSequentialNames << modName
        }
    }
    orderedSequentialNames.each { modName ->
        if (coreSet.contains(modName)) {
            sequentialBuildPlan << [moduleName: modName, isCoreType: true, branchName: coreBranchMap[modName]]
        } else {
            sequentialBuildPlan << [moduleName: modName, isCoreType: false, branchName: instBranchMap[modName]]
        }
    }

    if (!sequentialBuildPlan.isEmpty()) {
        echo "Nightly sequential SNAPSHOT build (${sequentialBuildPlan.size()} module(s)): ${sequentialBuildPlan.collect { it.moduleName }.join(', ')}"
        sequentialBuildPlan.each { item ->
            def modName = item.moduleName
            def targetBranch = item.branchName?.toString()?.trim()
            def moduleLabel = item.isCoreType ? 'core module' : 'installer module'
            if (!targetBranch) {
                error "❌ Nightly CI: no branch for ${moduleLabel} '${modName}'."
            }
            echo "🏗️ NIGHTLY ${item.isCoreType ? 'CORE' : 'SEQ-INSTALLER'}: [${modName}] ON [${targetBranch}]"
            executeChainBuild(
                selectedModulesList: [modName],
                jdkVersions: jdkVersions,
                cxSmgBase: cxSmgBase,
                isCoreType: item.isCoreType as boolean,
                customBranchOverride: targetBranch,
                runSecurityTools: runSecurityTools
            )
        }
    } else {
        echo 'No sequential modules for nightly build.'
    }

    if (!orderedParallelInstallerModules.isEmpty()) {
        def parallelTasks = [:]
        orderedParallelInstallerModules.each { modName ->
            def targetBranch = instBranchMap[modName]
            if (!targetBranch?.trim()) {
                error "❌ Nightly CI: no branch for installer module '${modName}'."
            }
            parallelTasks["build-${modName}"] = {
                stage("Build ${modName}") {
                    executeChainBuild(
                        selectedModulesList: [modName],
                        jdkVersions: jdkVersions,
                        cxSmgBase: cxSmgBase,
                        isCoreType: false,
                        customBranchOverride: targetBranch,
                        runSecurityTools: runSecurityTools
                    )
                }
            }
        }
        echo "🚀 Nightly PARALLEL installer build: ${orderedParallelInstallerModules.join(', ')}"
        parallel parallelTasks
    } else {
        echo 'No parallel installer modules for nightly build.'
    }

    def allModulesForSonar = []
    orderedCoreModuleList.each { modName ->
        def b = coreBranchMap[modName]
        if (!b?.trim()) {
            error "❌ Nightly Sonar report: branch missing for core '${modName}'."
        }
        allModulesForSonar << [moduleName: modName, branchName: b]
    }
    (orderedSequentialInstallerModules + orderedParallelInstallerModules).each { modName ->
        def b = instBranchMap[modName]
        if (!b?.trim()) {
            error "❌ Nightly Sonar report: branch missing for installer '${modName}'."
        }
        allModulesForSonar << [moduleName: modName, branchName: b]
    }
    if (!allModulesForSonar.isEmpty()) {
        publishSonarReport(
            modules: allModulesForSonar,
            reportName: getDefaultsScript.getSonarReportDisplayName(),
            failOnError: false
        )
    }
}

return this
