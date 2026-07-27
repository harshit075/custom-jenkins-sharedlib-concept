// vars/loadSharedLibVarScript.groovy
//
// EXACT REPLICA OF: CustomerXP loadSharedLibVarScript.groovy
//
// PURPOSE:
// Dynamically loads another vars/ script by name string at runtime.
// This is the FOUNDATION of the entire shared library — every script
// that needs to call another script uses this function.
//
// CustomerXP uses this pattern so that:
// 1. Scripts can call each other without hard-coded imports
// 2. The pipeline can decide AT RUNTIME which script to load
//    (e.g., buildModulesWithJDK vs buildModulesWithJDKReleaseTag)
// 3. Missing non-critical scripts degrade gracefully (return null)
//    while missing critical scripts fail fast
//
// USAGE:
//   def buildScript = loadSharedLibVarScript('buildModulesWithJDK')
//   buildScript.call(moduleName, jdkVersion, ...)
//
//   def cfg = loadSharedLibVarScript('getDefaults')
//   boolean runSecurity = cfg.getRunSecurityTools()

def call(String scriptName) {
    // Scripts that MUST exist — pipeline fails if these cannot be loaded
    // NOTE: Defined inside call() — Jenkins CPS does not support static fields in vars/ scripts
    def criticalScripts = [
        'buildModulesWithJDK',
        'buildModulesWithJDKReleaseTag',
        'executeChainBuild',
        'cxPipelineConfig'
    ]

    if (!scriptName?.trim()) {
        echo "⚠️ loadSharedLibVarScript: empty script name provided"
        return null
    }

    boolean isCritical = criticalScripts.contains(scriptName)

    // TEST MODE: when SHARED_LIB_VARS_PATH env var is set, load from filesystem
    // This allows unit testing outside Jenkins
    def testPath = env.SHARED_LIB_VARS_PATH?.toString()?.trim()
    if (testPath) {
        try {
            return this.load("${testPath}/${scriptName}.groovy")
        } catch (Throwable t) {
            echo "⚠️ loadSharedLibVarScript [TEST MODE]: failed to load '${scriptName}': ${t.message}"
            if (isCritical) {
                error "❌ Critical script '${scriptName}' could not be loaded in test mode."
            }
            return null
        }
    }

    // PRODUCTION MODE: access vars/ scripts via Groovy property lookup
    // Jenkins shared library makes each vars/*.groovy available as a property on the script
    try {
        def script = this.getProperty(scriptName)
        if (script == null) {
            echo "⚠️ loadSharedLibVarScript: '${scriptName}' resolved to null"
            if (isCritical) {
                error "❌ Critical script '${scriptName}' resolved to null."
            }
        }
        return script
    } catch (MissingPropertyException mpe) {
        echo "⚠️ loadSharedLibVarScript: '${scriptName}' not found in shared library (MissingPropertyException)"
        if (isCritical) {
            error "❌ Critical script '${scriptName}' not found in shared library. Ensure it exists in vars/."
        }
        return null
    } catch (Throwable t) {
        echo "⚠️ loadSharedLibVarScript: unexpected error loading '${scriptName}': ${t.class.name}: ${t.message}"
        if (isCritical) {
            error "❌ Critical script '${scriptName}' failed to load: ${t.message}"
        }
        return null
    }
}
