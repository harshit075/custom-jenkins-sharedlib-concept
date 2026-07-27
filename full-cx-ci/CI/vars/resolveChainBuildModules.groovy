// vars/resolveChainBuildModules.groovy
//
// EXACT REPLICA OF: CustomerXP resolveChainBuildModules.groovy
//
// PURPOSE:
// Given a comma-separated list of selected modules, returns them
// in the correct build order based on CX_CHAIN_ORDER.
//
// WHY THIS MATTERS:
// Module B may depend on Module A's compiled artifacts.
// If you build B before A, the build fails with ClassNotFoundException.
// CX_CHAIN_ORDER defines the canonical dependency sequence.
//
// Example:
//   CX_CHAIN_ORDER = "commons,core-lib,api-base,cc,cmq,efmapp"
//   Selected: ["api-base", "commons", "efmapp"]
//   Result:   ["commons", "api-base", "efmapp"]  ← commons MUST build first

def call(String selectedModulesCsv) {
    if (!selectedModulesCsv?.trim()) return []

    def selected = selectedModulesCsv.split(',').collect { it.trim() }.findAll { it }
    def chainOrder = (env.CX_CHAIN_ORDER ?: '').split(',').collect { it.trim() }.findAll { it }

    def selectedSet = selected as Set
    def ordered = []

    // First: add modules that ARE in chain order (in chain sequence)
    chainOrder.each { chainMod ->
        if (selectedSet.contains(chainMod)) {
            ordered << chainMod
        }
    }

    // Then: add any selected modules NOT in chain order (at end, in original order)
    selected.each { mod ->
        if (!ordered.contains(mod)) {
            echo "⚠️ resolveChainBuildModules: '${mod}' not found in CX_CHAIN_ORDER — appended at end"
            ordered << mod
        }
    }

    return ordered
}

return this
