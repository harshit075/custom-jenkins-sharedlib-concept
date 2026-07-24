/**
 * executeParallelBuild.groovy
 * 
 * MAPS TO: CI/vars/executeParallelBuild.groovy in CustomerXP
 * 
 * PURPOSE:
 * Builds multiple modules SIMULTANEOUSLY (in parallel).
 * These modules don't depend on each other, so running them
 * at the same time is faster (e.g., 5 modules × 3 min each = 3 min total, not 15 min).
 *
 * CUSTOMERXP BEHAVIOR:
 * - Takes installer modules not in the sequential lane
 * - Creates a parallel{} block with one branch per module
 * - Each branch runs executeChainBuild for a single module
 * - All modules build simultaneously on available agents
 *
 * OUR MINI VERSION:
 * - Same parallel execution pattern
 * - Each parallel branch clones a public repo and builds it
 * - Shows how Jenkins runs them simultaneously in the stage view
 */
def call(Map args) {
    def modules = args.modules ?: []           // List of module names to build in parallel
    def branchMap = args.branchMap ?: [:]      // module → branch mapping
    def config = args.config ?: [:]            // Pipeline config
    def buildType = args.buildType ?: 'snapshot'

    if (!modules) {
        echo "⏭️ No parallel modules to build. Skipping."
        return
    }

    echo "═══════════════════════════════════════════════════"
    echo "  PARALLEL BUILD"
    echo "  Modules: ${modules.join(', ')}"
    echo "  (All building simultaneously!)"
    echo "═══════════════════════════════════════════════════"

    // Build the parallel task map (same pattern as CustomerXP)
    def parallelTasks = [:]

    modules.each { moduleName ->
        def branch = branchMap[moduleName] ?: 'main'
        def repoUrl = "${config.gitBaseUrl}/${config.gitOrg}/${moduleName}.git"

        // Each entry in parallelTasks becomes a concurrent branch
        parallelTasks["build-${moduleName}"] = {
            echo ""
            echo "┌─────────────────────────────────────────────────"
            echo "│ 🚀 PARALLEL BUILD: ${moduleName}"
            echo "│ Branch: ${branch}"
            echo "│ Repo: ${repoUrl}"
            echo "└─────────────────────────────────────────────────"

            buildModule(
                moduleName: moduleName,
                branch: branch,
                repoUrl: repoUrl,
                config: config,
                buildType: buildType,
                isChainBuild: false
            )

            echo "✅ Parallel build complete: ${moduleName}"
        }
    }

    // Execute all tasks in parallel — Jenkins runs them simultaneously
    parallel parallelTasks

    echo ""
    echo "═══════════════════════════════════════════════════"
    echo "  ✅ ALL PARALLEL BUILDS COMPLETE"
    echo "═══════════════════════════════════════════════════"
}
