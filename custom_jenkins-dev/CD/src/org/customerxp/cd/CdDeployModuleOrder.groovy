package org.customerxp.cd

import com.cloudbees.groovy.cps.NonCPS

/**
 * CPS-safe deploy ordering (insertion sort only — no anonymous {@code Comparator}).
 * Used by {@code executeDeployPipeline} for CPS-safe deploy ordering (insertion sort, no anonymous Comparator).
 */
@SuppressWarnings(['unused'])
final class CdDeployModuleOrder {

    /** Log / support marker — confirms this class is on the shared-library classpath. */
    static final String IMPLEMENTATION_ID = 'cd-deploy-module-order-v2-insertion-sort'

    @NonCPS
    private static int deployKindRank(Object deployKind) {
        def dk = deployKind?.toString()?.trim()
        return dk == 'cc_setup' ? 0 : 1
    }

    @NonCPS
    private static int deployOrderValue(Map moduleEntry) {
        if (moduleEntry == null) {
            return 200
        }
        def raw = moduleEntry.deployOrder
        if (raw != null && raw.toString().trim()) {
            try {
                return Integer.parseInt(raw.toString().trim())
            } catch (NumberFormatException ignored) {
                return deployKindRank(moduleEntry.deployKind) == 0 ? 0 : 100
            }
        }
        return deployKindRank(moduleEntry.deployKind) == 0 ? 0 : 100
    }

    @NonCPS
    private static int compareModules(String idA, String idB, Map moduleRegistry) {
        def ea = CdRegistryDbRole.findModuleEntry(moduleRegistry, idA)
        def eb = CdRegistryDbRole.findModuleEntry(moduleRegistry, idB)
        def oa = deployOrderValue(ea as Map)
        def ob = deployOrderValue(eb as Map)
        if (oa != ob) {
            return oa <=> ob
        }
        def ka = deployKindRank(ea?.deployKind)
        def kb = deployKindRank(eb?.deployKind)
        if (ka != kb) {
            return ka <=> kb
        }
        idA <=> idB
    }

    @NonCPS
    private static void sortModuleIdsInPlace(List<String> ids, Map moduleRegistry) {
        int n = ids.size()
        if (n < 2) {
            return
        }
        for (int i = 1; i < n; i++) {
            String key = ids.get(i)
            int j = i - 1
            while (j >= 0 && compareModules(ids.get(j), key, moduleRegistry) > 0) {
                ids.set(j + 1, ids.get(j))
                j--
            }
            ids.set(j + 1, key)
        }
    }

    /**
     * @param selectedIds module ids from the job (any case)
     * @param moduleRegistry parsed module-registry.json
     * @return deploy order (lowercase ids)
     */
    @NonCPS
    static List<String> sortForDeploy(List<String> selectedIds, Map moduleRegistry) {
        def selected = selectedIds.findAll { it?.toString()?.trim() }.collect { it.toString().trim().toLowerCase() }.unique()
        if (selected.isEmpty()) {
            return []
        }
        def idSet = selected as Set

        def inDegree = [:]
        def successors = [:].withDefault { [] as List }

        selected.each { mid -> inDegree[mid] = 0 }
        selected.each { mid ->
            def entry = CdRegistryDbRole.findModuleEntry(moduleRegistry, mid) as Map
            def deps = entry?.dependsOn
            if (deps instanceof List) {
                deps.each { d ->
                    def did = d?.toString()?.trim()?.toLowerCase()
                    if (did && idSet.contains(did)) {
                        successors[did].add(mid)
                        inDegree[mid] = (inDegree[mid] ?: 0) + 1
                    }
                }
            }
        }

        def ready = selected.findAll { (inDegree[it] ?: 0) == 0 } as List<String>
        sortModuleIdsInPlace(ready, moduleRegistry)
        def order = [] as List<String>

        while (!ready.isEmpty()) {
            def u = ready.remove(0)
            order.add(u)
            (successors[u] ?: []).each { v ->
                inDegree[v] = (inDegree[v] ?: 0) - 1
                if (inDegree[v] == 0) {
                    ready.add(v)
                    sortModuleIdsInPlace(ready, moduleRegistry)
                }
            }
        }

        if (order.size() != selected.size()) {
            throw new IllegalStateException(
                "module-registry: cyclic dependsOn among selected modules [${selected.join(', ')}]; fix dependsOn in module-registry.json")
        }
        order
    }
}
