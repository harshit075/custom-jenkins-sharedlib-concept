/**
 * Fails the pipeline when an installer module is selected but required CORE modules
 * (from {@code CX_CORE_MODULE_IDS} or {@code coreModuleIds}) that appear before that installer
 * in {@code CX_CHAIN_ORDER} are not selected under CORE_SELECTED for this job.
 * Core ids must be provided via env {@code CX_CORE_MODULE_IDS} or {@code args.coreModuleIds}; there is no built-in default list.
 */
def call(Map args) {
    List<String> installerModules = (args.installerModules ?: []).collect { it?.toString()?.trim() }.findAll { it }
    List<String> selectedCoreModules = (args.selectedCoreModules ?: []).collect { it?.toString()?.trim() }.findAll { it }

    if (installerModules.isEmpty()) {
        return
    }

    String fullOrderStr = (args.fullOrder ?: env.CX_CHAIN_ORDER ?: '').toString().trim()
    String coreIdsStr = (args.coreModuleIds ?: env.CX_CORE_MODULE_IDS ?: '').toString().trim()

    if (!coreIdsStr) {
        error(
            '❌ validateInstallerCorePrerequisites: core module ids are required. ' +
            'Set env CX_CORE_MODULE_IDS (comma-separated; must match CORE_SELECTED / job definition) ' +
            'or pass coreModuleIds in the step arguments.'
        )
    }

    if (!fullOrderStr) {
        echo '⚠️ validateInstallerCorePrerequisites: CX_CHAIN_ORDER is empty; skipping prerequisite validation.'
        return
    }

    List<String> fullOrder = fullOrderStr.split(',').collect { it.trim() }.findAll { it }
    Set<String> coreSet = coreIdsStr.split(',').collect { it.trim() }.findAll { it } as Set
    Set<String> selectedCoreSet = selectedCoreModules as Set

    List<String> errors = []

    installerModules.each { String inst ->
        int idx = fullOrder.indexOf(inst)
        if (idx < 0) {
            errors.add("  • '${inst}' is not listed in CX_CHAIN_ORDER — update the full product order in the Jenkinsfile.")
            return
        }

        List<String> prereqCores = []
        for (int i = 0; i < idx; i++) {
            String m = fullOrder[i]
            if (coreSet.contains(m)) {
                prereqCores.add(m)
            }
        }

        List<String> missing = prereqCores.findAll { !selectedCoreSet.contains(it) }
        if (missing) {
            errors.add("  • ${inst}: select these CORE modules (or remove this installer): ${missing.join(', ')}")
        }
    }

    if (errors) {
        error(
            '❌ Installer build(s) require prerequisite core module(s) to be selected under CORE_SELECTED:\n' +
            errors.join('\n')
        )
    }

    echo "✅ Core prerequisites satisfied for installer module(s): ${installerModules.join(', ')}"
}
