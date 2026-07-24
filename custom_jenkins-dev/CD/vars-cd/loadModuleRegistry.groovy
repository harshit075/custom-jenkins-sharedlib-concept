// vars/loadModuleRegistry.groovy
import groovy.json.JsonSlurper
import org.customerxp.cd.CdRegistryDbRole

/**
 * Load the CD module registry from the shared library only.
 *
 * <p>Required global env: {@code JENKINS_CD_MODULE_REGISTRY_LIBRARY_RESOURCE} — path passed to
 * {@code libraryResource(...)} (e.g. {@code cd/module-registry.json}). No file-path or workspace fallbacks.
 *
 * @param args ignored (kept for call-site compatibility)
 * @return Map with keys version, modules (List), plus _registryLoadSource
 */
def call(Map args = [:]) {
    def resourceName = env.JENKINS_CD_MODULE_REGISTRY_LIBRARY_RESOURCE?.toString()?.trim()
    if (!resourceName) {
        error 'loadModuleRegistry: set JENKINS_CD_MODULE_REGISTRY_LIBRARY_RESOURCE (Manage Jenkins global env, e.g. cd/module-registry.json).'
    }

    def slurper = new JsonSlurper()
    def source = "libraryResource(${resourceName})"

    String text
    try {
        text = libraryResource(resourceName)?.toString()
    } catch (Exception e) {
        error "loadModuleRegistry: libraryResource(${resourceName}) failed — ${e.message}. " +
            'Confirm the resource exists under resources/ in the shared library and reload the library.'
    }
    if (!text?.trim()) {
        error "loadModuleRegistry: libraryResource(${resourceName}) returned empty content."
    }

    def plain = jsonTreeToSerializable(slurper.parseText(text))
    if (plain == null || !(plain instanceof Map)) {
        error "loadModuleRegistry: parsed JSON is not an object (source=${source})"
    }
    def data = plain as Map
    if (data.modules == null || !(data.modules instanceof List)) {
        error "loadModuleRegistry: missing or invalid modules[] (source=${source})"
    }
    validateRegistrySemantics(data, source)

    def out = new LinkedHashMap()
    out.putAll(data)
    out._registryLoadSource = source
    try {
        if (binding?.hasVariable('env')) {
            env.CX_CD_MODULE_REGISTRY_VERSION = out.version?.toString() ?: ''
            env.CX_CD_MODULE_REGISTRY_SOURCE = source
        }
    } catch (Throwable ignored) {
    }
    return out
}

/** Phase 1 tests P1-T2–T4: required fields, enums, duplicate ids (mirrors validate-module-registry.py basic checks). */
void validateRegistrySemantics(Map data, String source) {
    def errs = []
    if (!data.version?.toString()?.trim()) {
        errs.add('missing or empty version')
    }
    def mods = data.modules
    if (!(mods instanceof List) || mods.isEmpty()) {
        errs.add('modules must be a non-empty array')
        failRegistryValidation(source, errs)
        return
    }
    def ALLOWED_DB = CdRegistryDbRole.allowedDbRoles()
    def ALLOWED_DK = ['cc_setup', 'module_deploy'] as Set
    def REQUIRED = ['id', 'displayName', 'dbRole', 'deployKind', 'scripts', 'repoPath']
    def allRegistryIds = mods.collect { it instanceof Map ? it.id?.toString()?.trim()?.toLowerCase() : null }.findAll { it }.toSet()
    def seenIds = [] as Set
    mods.eachWithIndex { m, i ->
        def p = "modules[${i}]"
        if (!(m instanceof Map)) {
            errs.add("${p} must be an object")
            return
        }
        REQUIRED.each { k ->
            if (!m.containsKey(k)) {
                errs.add("${p} missing required field '${k}'")
            }
        }
        def mid = m.id?.toString()?.trim()?.toLowerCase()
        if (mid) {
            if (seenIds.contains(mid)) {
                errs.add("duplicate module id '${mid}' (${p})")
            }
            seenIds.add(mid)
        }
        def db = m.dbRole?.toString()
        if (!ALLOWED_DB.contains(db)) {
            errs.add("${p}.dbRole must be one of ${ALLOWED_DB.sort()}, got ${db}")
        }
        def dk = m.deployKind?.toString()
        if (!ALLOWED_DK.contains(dk)) {
            errs.add("${p}.deployKind must be one of ${ALLOWED_DK.sort()}, got ${dk}")
        }
        def scripts = m.scripts
        if (scripts != null && !(scripts instanceof Map)) {
            errs.add("${p}.scripts must be an object")
        }
        def depList = m.dependsOn
        if (depList != null) {
            if (!(depList instanceof List)) {
                errs.add("${p}.dependsOn must be an array")
            } else {
                depList.eachWithIndex { d, di ->
                    def ref = d?.toString()?.trim()?.toLowerCase()
                    if (!ref) {
                        errs.add("${p}.dependsOn[${di}] is empty")
                    } else if (ref == mid) {
                        errs.add("${p}.dependsOn must not reference itself ('${ref}')")
                    } else if (!allRegistryIds.contains(ref)) {
                        errs.add("${p}.dependsOn references unknown module '${ref}'")
                    }
                }
            }
        }
        def ordRaw = m.deployOrder
        if (ordRaw != null && ordRaw.toString().trim()) {
            try {
                Integer.parseInt(ordRaw.toString().trim())
            } catch (NumberFormatException e) {
                errs.add("${p}.deployOrder must be an integer, got ${ordRaw}")
            }
        }
    }
    if (!errs.isEmpty()) {
        failRegistryValidation(source, errs)
    }
}

void failRegistryValidation(String source, List errs) {
    error "loadModuleRegistry: registry validation failed (source=${source}):\n  - ${errs.join('\n  - ')}"
}
