// vars/moduleSpecialCases.groovy
//
// EXACT REPLICA OF: CustomerXP moduleSpecialCases.groovy
//
// PURPOSE:
// Some modules don't live in the default Git namespace/org.
// This script maps module names to their actual repository locations.
//
// CustomerXP uses this because some modules are in different GitLab subgroups.
// We use it to map module names to their actual GitHub repo URLs and owners.
//
// ADAPTED FOR LOCAL JENKINS:
// Maps module IDs → public GitHub repo URLs (different orgs per module)

// Module registry: maps module ID → actual GitHub repo URL
private static final Map<String, Map> MODULE_REGISTRY = [
    // Core modules (sequential builds)
    '2048'              : [repoUrl: 'https://github.com/gabrielecirulli/2048.git',          defaultBranch: 'master'],
    'markdown-here'     : [repoUrl: 'https://github.com/adam-p/markdown-here.git',          defaultBranch: 'master'],
    'flask'             : [repoUrl: 'https://github.com/pallets/flask.git',                 defaultBranch: 'main'],
    'express'           : [repoUrl: 'https://github.com/expressjs/express.git',             defaultBranch: 'master'],

    // Installer modules (parallel builds)
    'html5-boilerplate' : [repoUrl: 'https://github.com/h5bp/html5-boilerplate.git',       defaultBranch: 'main'],
    'you-dont-need-js'  : [repoUrl: 'https://github.com/you-dont-need/You-Dont-Need-JavaScript.git', defaultBranch: 'master'],
    'spring-petclinic'  : [repoUrl: 'https://github.com/spring-projects/spring-petclinic.git', defaultBranch: 'main'],
    'react-shopping-cart': [repoUrl: 'https://github.com/jeffersonRibeiro/react-shopping-cart.git', defaultBranch: 'master'],
    'node-todo'         : [repoUrl: 'https://github.com/scotch-io/node-todo.git',           defaultBranch: 'master'],
]

/**
 * Returns the actual repo URL for a module.
 * Falls back to constructing URL from CX_GIT_BASE_URL + CX_GIT_ORG + moduleName.
 * MAPS TO: CustomerXP's namespace discovery via GitLab API
 */
String getRepoUrl(String moduleName, Map cfg = null) {
    def entry = MODULE_REGISTRY[moduleName]
    if (entry?.repoUrl) return entry.repoUrl
    // Fallback: construct from config
    def base = cfg?.gitBaseUrl ?: env.CX_GIT_BASE_URL ?: 'https://github.com'
    def org  = cfg?.gitOrg ?: env.CX_GIT_ORG ?: 'unknown'
    echo "⚠️ moduleSpecialCases: '${moduleName}' not in registry — using ${base}/${org}/${moduleName}.git"
    return "${base}/${org}/${moduleName}.git"
}

/**
 * Returns the default branch for a module.
 * MAPS TO: CustomerXP's branch resolution via GitLab API
 */
String getDefaultBranch(String moduleName) {
    return MODULE_REGISTRY[moduleName]?.defaultBranch ?: 'main'
}

/**
 * Returns the namespace (GitHub org/user) for a module.
 * MAPS TO: CustomerXP's GitLab namespace discovery
 */
String getNamespace(String moduleName, String defaultOrg = null) {
    def url = MODULE_REGISTRY[moduleName]?.repoUrl ?: ''
    if (url) {
        def parts = url.replaceAll('https://github.com/', '').split('/')
        return parts.length >= 2 ? parts[0] : (defaultOrg ?: env.CX_GIT_ORG ?: 'unknown')
    }
    return defaultOrg ?: env.CX_GIT_ORG ?: 'unknown'
}

/**
 * Checks if a module is in the registry.
 */
boolean isKnownModule(String moduleName) {
    return MODULE_REGISTRY.containsKey(moduleName)
}

/**
 * Returns all registered module IDs.
 */
List<String> getAllModuleIds() {
    return MODULE_REGISTRY.keySet().toList()
}

return this
