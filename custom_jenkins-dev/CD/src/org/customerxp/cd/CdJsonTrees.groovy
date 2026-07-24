package org.customerxp.cd

/**
 * JsonSlurper returns {@code LazyMap} / non-serializable structures; Jenkins CPS cannot persist them.
 * Recursively convert to {@link LinkedHashMap} / {@link ArrayList} with plain Java values.
 *
 * <p>Shared by {@code vars/jsonTreeToSerializable.groovy} and {@link CdDbCredentialsJson}.</p>
 */
@SuppressWarnings(['unused'])
final class CdJsonTrees {

    static Object toSerializablePlain(Object o) {
        if (o instanceof Map) {
            def m = new LinkedHashMap()
            ((Map) o).each { k, v -> m[k] = toSerializablePlain(v) }
            return m
        }
        if (o instanceof List) {
            return ((List) o).collect { toSerializablePlain(it) }
        }
        return o
    }
}
