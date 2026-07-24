// vars/loadSharedLibVarScript.groovy

// This script *is* the loader DSL step.
// When called from other shared library scripts as `loadSharedLibVarScript('someScript')`,
// it will execute this `call` method.

/**
 * Dynamically loads another shared library script (from 'vars/') or accesses a class (from 'src/').
 *
 * This function handles both 'test mode' (loading from a specific file path)
 * and 'production mode' (using Jenkins' standard `getProperty` access for shared library scripts/classes).
 *
 * @param scriptName The name of the `vars/` script (e.g., 'getCredentials', 'buildModulesWithJDK')
 *                   or the full name of a class (e.g., 'org.example.MyClass').
 * @return The loaded script object (for `vars` scripts) or the referenced class object,
 *         or `null` if loading fails after logging a warning (for non-critical scripts).
 * @throws FlowInterruptedException when a critical build script fails to load (e.g. syntax error).
 */
def call(String scriptName) {
    def criticalScripts = ['buildModulesWithJDK']  // Scripts that must load; pipeline fails if they don't
    if (env.SHARED_LIB_VARS_PATH) {
        // --- TEST MODE (for local library development/testing) ---
        echo "ℹ️ [Loader Test Mode] Attempting to load '${scriptName}.groovy' manually from '${env.SHARED_LIB_VARS_PATH}'."
        try {
            // In test mode, use the explicit 'load' step on the current script context.
            // This 'this' correctly refers to the script instance itself that has the 'load' method.
            return this.load("${env.SHARED_LIB_VARS_PATH}/${scriptName}.groovy")
        } catch (Exception e) {
            echo "⚠️ WARNING: [Loader Test Mode] Failed to load '${scriptName}.groovy' from path: ${e.message}"
            return null
        }
    } else {
        // --- PRODUCTION MODE (standard Jenkins Shared Library usage) ---
        try {
            // In production, when this `vars/loadSharedLibVarScript.groovy` is called,
            // `this.getProperty(scriptName)` directly requests other `vars/` scripts or `src/` classes.
            return this.getProperty(scriptName)
        } catch (MissingPropertyException e) {
            echo "⚠️ WARNING: [Loader Prod Mode] Could not load shared library script/class '${scriptName}' via direct property access. Error: ${e.message}"
            if (criticalScripts.contains(scriptName)) {
                error "❌ Failed to load build script '${scriptName}'. Pipeline must fail when the script cannot be loaded (e.g. missing or misconfigured). Error: ${e.message}"
            }
            return null
        } catch (Exception e) {
            echo "⚠️ WARNING: [Loader Prod Mode] Unexpected error when loading script/class '${scriptName}': ${e.message}"
            if (criticalScripts.contains(scriptName)) {
                error "❌ Failed to load build script '${scriptName}' (e.g. syntax/compile error). Pipeline must fail. Fix the script and re-run. Error: ${e.message}"
            }
            return null
        }
    }
}
// This vars/ script implicitly returns the 'call' method, making 'loadSharedLibVarScript(...)'
// a direct DSL method for any other script in the shared library (and Jenkinsfile).