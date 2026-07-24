// vars/resolveChainBuildModules.groovy

// --- CENTRALIZED Shared Library Loading ---
def sharedLibLoader = load('sharedLibLoader') // Add this line at the top

/**
 * Resolves the final build order for a list of selected modules based on a predefined
 * dependency chain order. This script ensures that if a module is selected for a chain build,
 * all its preceding dependencies in the `CX_CHAIN_ORDER` are also considered.
 *
 * The canonical dependency chain is retrieved from the `CX_CHAIN_ORDER` environment variable,
 * which should be a comma-separated string of all core module names in their required build sequence.
 *
 * @param selectedModulesString A comma-separated string of modules explicitly selected by the user for the build.
 *                        This list is used to filter the canonical chain.
 * @return A `List<String>` of module names in the resolved, sequential build order,
 *         containing only the *selected* modules, but maintaining their canonical order.
 */
def call(String selectedModulesString) {
    echo "⚙️ Entering 'resolveChainBuildModules' to determine build order for: '${selectedModulesString}'"

    def parsedSelectedModules = selectedModulesString.split(',').collect { it.trim() }.findAll { it }

    if (parsedSelectedModules.isEmpty()) {
        echo "⚠️ WARNING: No selected modules provided to 'resolveChainBuildModules'. Returning an empty list."
        return []
    }

    def canonicalChainOrderEnvVar = env.CX_CHAIN_ORDER ?: ''
    def canonicalChainOrderList = canonicalChainOrderEnvVar.split(',').collect { it.trim() }.findAll { it }

    if (canonicalChainOrderList.isEmpty()) {
        echo "⚠️ WARNING: Environment variable CX_CHAIN_ORDER is empty or invalid. Cannot apply a canonical order."
        echo "DEBUG: Returning selected modules as-is: ${parsedSelectedModules.join(', ')}"
        return parsedSelectedModules
    }

    def resolvedBuildOrder = []
    
    canonicalChainOrderList.each { canonicalModule ->
        if (parsedSelectedModules.contains(canonicalModule)) {
            resolvedBuildOrder.add(canonicalModule)
        }
    }

    if (resolvedBuildOrder.isEmpty()) {
        echo "⚠️ WARNING: After applying canonical order, no selected core modules remain in the build list."
        return []
    }

    echo "DEBUG: Original Selected Modules (for filtering): ${parsedSelectedModules.join(', ')}"
    echo "DEBUG: Full CX_CHAIN_ORDER (canonical): ${canonicalChainOrderList.join(' → ')}"
    echo "➡️ Final Resolved Chain Build Order: ${resolvedBuildOrder.join(' → ')}"

    return resolvedBuildOrder
}