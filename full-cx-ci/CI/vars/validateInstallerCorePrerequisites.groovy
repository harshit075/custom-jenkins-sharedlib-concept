// vars/validateInstallerCorePrerequisites.groovy
//
// EXACT REPLICA OF: CustomerXP validateInstallerCorePrerequisites.groovy
//
// PURPOSE:
// Fails the pipeline early if an installer module is selected but
// its required CORE prerequisite modules are not also selected.
//
// WHY: Installer modules depend on core module artifacts. If you try
// to build an installer without first building its core dependency,
// the build will fail mid-way with cryptic "artifact not found" errors.
// This validator catches that BEFORE any build starts.
//
// Example:
//   CX_CHAIN_ORDER = "commons,core-lib,cc,cmq"
//   CX_CORE_MODULE_IDS = "commons,core-lib"
//   Selected installers: [cmq]
//   Selected core: []  ← MISSING! commons and core-lib must be selected too
//   Result: FAIL with clear message

def call(Map args) {
    def installerModules  = args.installerModules ?: []
    def selectedCoreModules = args.selectedCoreModules ?: []
    def coreModuleIds     = args.coreModuleIds ?:
                            (env.CX_CORE_MODULE_IDS ?: '').split(',').collect { it.trim() }.findAll { it }
    def chainOrderStr     = args.fullOrder ?: env.CX_CHAIN_ORDER ?: ''
    def chainOrder        = chainOrderStr.split(',').collect { it.trim() }.findAll { it }

    if (!installerModules) {
        echo "✅ validateInstallerCorePrerequisites: no installer modules selected — skipping"
        return
    }

    def coreSet = coreModuleIds as Set
    def selectedCoreSet = selectedCoreModules as Set

    // Find which core modules come BEFORE any selected installer in chain order
    def firstInstallerIdx = Integer.MAX_VALUE
    installerModules.each { inst ->
        def idx = chainOrder.indexOf(inst)
        if (idx >= 0 && idx < firstInstallerIdx) firstInstallerIdx = idx
    }

    def requiredCoreModules = []
    chainOrder.eachWithIndex { mod, idx ->
        if (idx < firstInstallerIdx && coreSet.contains(mod)) {
            requiredCoreModules << mod
        }
    }

    def missingCore = requiredCoreModules.findAll { !selectedCoreSet.contains(it) }

    if (missingCore) {
        error """❌ validateInstallerCorePrerequisites FAILED:
Installer modules ${installerModules} require core prerequisites that are NOT selected.
Missing core modules: ${missingCore.join(', ')}
Please also select these core modules in CORE_SELECTED, or deselect the installer modules.
Chain order: ${chainOrder.join(' → ')}"""
    }

    echo "✅ validateInstallerCorePrerequisites: all prerequisites satisfied"
    echo "   Installer modules: ${installerModules.join(', ')}"
    echo "   Required core: ${requiredCoreModules.join(', ')}"
    echo "   Selected core: ${selectedCoreModules.join(', ')}"
}

return this
