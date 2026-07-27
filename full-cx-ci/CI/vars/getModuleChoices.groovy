// vars/getModuleChoices.groovy
//
// EXACT REPLICA OF: CustomerXP getModuleChoices.groovy
//
// PURPOSE:
// Dynamically discovers available modules. CustomerXP probes GitLab via
// git ls-remote across namespaces. We read from module-registry.json instead.

import groovy.json.JsonSlurper

def call() {
    // Try to load from module-registry.json resource
    try {
        def registryContent = libraryResource('module-registry.json')
        def registry = new JsonSlurper().parseText(registryContent)
        return registry.modules.collect { it.id }
    } catch (Throwable t) {
        echo "⚠️ getModuleChoices: could not load module-registry.json — falling back to env vars"
    }

    // Fallback: read from env vars (same as CustomerXP fallback)
    def core = (env.CX_CORE_MODULE_IDS ?: '').split(',').collect { it.trim() }.findAll { it }
    def inst = (env.CX_INSTALLER_MODULE_IDS ?: '').split(',').collect { it.trim() }.findAll { it }
    def all  = (core + inst).unique()

    if (!all) error "❌ getModuleChoices: no modules found in module-registry.json or env vars"
    return all
}

return this
