package org.customerxp.cd

import com.cloudbees.groovy.cps.NonCPS

/**
 * P3-2: Single source for module registry {@code dbRole} semantics — mirrors validation in
 * {@code loadModuleRegistry} ({@code PRIMARY_DB}, {@code CUSTOM_DB}, {@code SHARED_DB}).
 */
@SuppressWarnings(['unused'])
final class CdRegistryDbRole {

    static final String PRIMARY_DB = 'PRIMARY_DB'
    static final String CUSTOM_DB = 'CUSTOM_DB'
    static final String SHARED_DB = 'SHARED_DB'

    private static final Set<String> ALLOWED = [PRIMARY_DB, CUSTOM_DB, SHARED_DB] as Set

    /** Same allowed values as registry validation — use in {@code loadModuleRegistry} checks. */
    static Set<String> allowedDbRoles() {
        new LinkedHashSet<>(ALLOWED)
    }

    /** Canonical role string, or {@link #SHARED_DB} if unknown / blank. */
    static String normalizeDbRole(Object raw) {
        def s = raw?.toString()?.trim()
        if (!s) return SHARED_DB
        if (ALLOWED.contains(s)) return s
        SHARED_DB
    }

    /** True when registry routing uses per-module DB UI ({@code PER_MODULE_DB_JSON}), i.e. not {@link #SHARED_DB}. */
    static boolean usesPerModuleDbInfrastructure(Object rawDbRole) {
        normalizeDbRole(rawDbRole) != SHARED_DB
    }

    /** CPS-safe: used inside {@code @NonCPS} deploy ordering — avoid CPS dispatch mismatches. */
    @NonCPS
    static Object findModuleEntry(Map moduleRegistry, String moduleId) {
        if (!moduleRegistry?.modules || !moduleId?.toString()?.trim()) {
            return null
        }
        def x = moduleId.toString().trim().toLowerCase()
        moduleRegistry.modules.find { it?.id?.toString()?.trim()?.toLowerCase() == x }
    }

    /** DB role for {@code moduleId}, normalized — {@link #SHARED_DB} if unknown module or invalid role. */
    static String dbRoleForModule(Map moduleRegistry, String moduleId) {
        normalizeDbRole(findModuleEntry(moduleRegistry, moduleId)?.dbRole)
    }

    /** Sorted lowercase ids for modules whose role uses per-module DB flows (PRIMARY_DB / CUSTOM_DB). */
    static List<String> moduleIdsWithPerModuleDbSorted(Map moduleRegistry) {
        if (!moduleRegistry?.modules) {
            return []
        }
        moduleRegistry.modules.findAll { usesPerModuleDbInfrastructure(it?.dbRole) }
            .collect { it?.id?.toString()?.trim()?.toLowerCase() }
            .findAll { it }
            .sort()
    }

    /**
     * Optional Manage Jenkins / pipeline env filter for which modules may appear in {@code PER_MODULE_DB_JSON} (Build with Parameters)
     * and deploy-time per-module DB validation (comma-separated ids, case-insensitive). The UI row list is further intersected with {@code MODULE_SELECTED}.
     * <p>Each token must be in {@code registryPerDbIds} (PRIMARY_DB/CUSTOM_DB). Order follows the env list.
     * Blank env → all registry per-DB modules (sorted). If every token is invalid, falls back to all registry ids.
     */
    static List<String> perModuleDbUiModuleList(Set<String> registryPerDbIds, String envRaw) {
        def reg = (registryPerDbIds ?: [] as Set).collect { it?.toString()?.trim()?.toLowerCase() }.findAll { it } as Set
        if (!reg) {
            return []
        }
        def s = envRaw?.toString()?.trim()
        if (!s) {
            return reg.sort() as List<String>
        }
        def out = [] as List<String>
        def seen = [] as Set
        s.split(',').each { token ->
            def id = token?.toString()?.trim()?.toLowerCase()
            if (!id || seen.contains(id)) {
                return
            }
            if (id in reg) {
                seen.add(id)
                out.add(id)
            }
        }
        if (!out.isEmpty()) {
            return out
        }
        return reg.sort() as List<String>
    }

    /** Same ids as {@link #moduleIdsWithPerModuleDbSorted} as a {@link LinkedHashSet} (deterministic order). */
    static Set<String> moduleIdsWithPerModuleDbAsSet(Map moduleRegistry) {
        new LinkedHashSet(moduleIdsWithPerModuleDbSorted(moduleRegistry))
    }
}
