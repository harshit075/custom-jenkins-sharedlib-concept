/**
 * executeChainBuild.groovy
 * 
 * MAPS TO: CI/vars/executeChainBuild.groovy in CustomerXP
 * 
 * PURPOSE:
 * Builds modules ONE BY ONE in a specific order (chain order).
 * Some modules depend on others — they must build sequentially.
 *
 * CUSTOMERXP BEHAVIOR:
 * - Resolves canonical chain order from CX_CHAIN_ORDER
 * - For each module: spawns parallel JDK builds (JDK 11, 17 simultaneously)
 * - Each JDK build: clones repo → maven build → nexus upload → security scan
 * - Uses dedicated workspace per module/JDK combination
 *
 * OUR MINI VERSION:
 * - Same chain ordering logic
 * - Clones public GitHub repos
 * - Runs a simple build command (no Maven/Nexus needed)
 * - Demonstrates the sequential execution pattern
 */
def call(Map args) {
    def modules = args.modules ?: []           // List of module names to build
    def branchMap = args.branchMap ?: [:]      // module → branch mapping
    def config = args.config ?: [:]            // Pipeline config from miniPipelineConfig
    def buildType = args.buildType ?: 'snapshot'

    if (!modules) {
        error "❌ executeChainBuild: No modules provided!"
    }

    // Resolve order based on MINI_CHAIN_ORDER (same logic as CustomerXP)
    def chainOrder = config.chainOrder ?: []
    def orderedModules = []

    // First: add modules that ARE in chain order (preserving chain sequence)
    chainOrder.each { chainMod ->
        if (modules.contains(chainMod)) {
            orderedModules << chainMod
        }
    }
    // Then: add remaining modules that aren't in chain order (at the end)
    modules.each { mod ->
        if (!orderedModules.contains(mod)) {
            orderedModules << mod
            echo "⚠️ Module '${mod}' not in MINI_CHAIN_ORDER — placed at end"
        }
    }

    echo "═══════════════════════════════════════════════════"
    echo "  SEQUENTIAL CHAIN BUILD"
    echo "  Order: ${orderedModules.join(' → ')}"
    echo "═══════════════════════════════════════════════════"

    // Build each module sequentially (one after another)
    orderedModules.each { moduleName ->
        def branch = branchMap[moduleName] ?: 'main'
        def repoUrl = "${config.gitBaseUrl}/${config.gitOrg}/${moduleName}.git"

        echo ""
        echo "┌─────────────────────────────────────────────────"
        echo "│ 🏗️  CHAIN BUILD: ${moduleName}"
        echo "│ Branch: ${branch}"
        echo "│ Repo: ${repoUrl}"
        echo "└─────────────────────────────────────────────────"

        // Call the actual build function (like CustomerXP's buildModulesWithJDK)
        buildModule(
            moduleName: moduleName,
            branch: branch,
            repoUrl: repoUrl,
            config: config,
            buildType: buildType,
            isChainBuild: true
        )

        echo "✅ Chain build complete: ${moduleName}"
    }

    echo ""
    echo "═══════════════════════════════════════════════════"
    echo "  ✅ ALL CHAIN BUILDS COMPLETE"
    echo "═══════════════════════════════════════════════════"
}
