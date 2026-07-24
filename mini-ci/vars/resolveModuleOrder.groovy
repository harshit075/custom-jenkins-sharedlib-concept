/**
 * resolveModuleOrder.groovy
 * 
 * MAPS TO: CI/vars/resolveChainBuildModules.groovy in CustomerXP
 * 
 * PURPOSE:
 * Given a list of selected modules, returns them in the correct build order
 * based on MINI_CHAIN_ORDER (like CX_CHAIN_ORDER in CustomerXP).
 * 
 * Example:
 *   MINI_CHAIN_ORDER = "commons,core-lib,api-base,cc"
 *   Selected: [api-base, commons]
 *   Result:   [commons, api-base]   ← commons must build first!
 */
def call(List<String> selectedModules, List<String> chainOrder) {
    def ordered = []
    def selectedSet = selectedModules.collect { it.trim() } as Set

    // Add selected modules in chain order sequence
    chainOrder.each { chainMod ->
        if (selectedSet.contains(chainMod)) {
            ordered << chainMod
        }
    }

    // Add any selected modules NOT in chain order (at the end)
    selectedModules.each { mod ->
        if (!ordered.contains(mod.trim())) {
            ordered << mod.trim()
        }
    }

    return ordered
}
