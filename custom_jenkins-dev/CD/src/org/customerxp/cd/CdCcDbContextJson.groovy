package org.customerxp.cd

/**
 * Option A {@code cc_db_context.json}: root {@code schema_version} + {@code installs[]} of
 * per-CC-install records ({@code dep_base_path}, {@code db_server}, credentials, {@code timestamp}).
 * Migrates legacy single-object files on read/save.
 */
@SuppressWarnings(['DuplicateNumberLiteral'])
final class CdCcDbContextJson {

    static final int SCHEMA_VERSION = 1

    private CdCcDbContextJson() {}

    /** Trim, forward slashes, strip trailing slashes (identity key for {@code dep_base_path}). */
    static String normalizeDepBasePath(String path) {
        def s = path?.toString()?.trim() ?: ''
        if (!s) {
            return ''
        }
        s.replace('\\', '/').replaceAll(/\/+$/, '')
    }

    /**
     * Normalizes JsonSlurper output to {@code [schema_version: int, installs: List<Map>]}.
     * Legacy top-level object (no {@code installs} list) becomes one element in {@code installs}.
     */
    static Map normalizeSlurpedRoot(Object parsed) {
        if (!(parsed instanceof Map)) {
            return [schema_version: SCHEMA_VERSION, installs: []]
        }
        Map m = (Map) parsed
        if (m.installs instanceof List) {
            List raw = (List) m.installs
            List installs = raw.collect { normalizeInstallMap(it) }.findAll { it != null }
            int ver = SCHEMA_VERSION
            try {
                if (m.schema_version != null) {
                    ver = m.schema_version as int
                }
            } catch (Exception ignored) {
                ver = SCHEMA_VERSION
            }
            return [schema_version: ver, installs: installs]
        }
        Map one = normalizeInstallMap(m)
        if (!one) {
            return [schema_version: SCHEMA_VERSION, installs: []]
        }
        return [schema_version: SCHEMA_VERSION, installs: [one]]
    }

    /**
     * One install row: requires at least {@code db_server} (same as legacy file contract).
     */
    static Map normalizeInstallMap(Object obj) {
        if (!(obj instanceof Map)) {
            return null
        }
        Map ins = (Map) obj
        def out = [:]
        out.dep_base_path = normalizeDepBasePath(ins.dep_base_path?.toString())
        out.db_server = ins.db_server?.toString()?.trim() ?: ''
        out.db_user = ins.db_user?.toString()?.trim() ?: ''
        out.db_password = ins.db_password?.toString() ?: ''
        out.credential_id = ins.credential_id?.toString()?.trim() ?: ''
        long ts = System.currentTimeMillis()
        if (ins.timestamp != null) {
            try {
                ts = (ins.timestamp as Number).longValue()
            } catch (Exception ignored) {
                ts = System.currentTimeMillis()
            }
        }
        out.timestamp = ts
        if (!out.db_server) {
            return null
        }
        return out
    }

    static Map newInstallEntry(
        String dbServer,
        String dbUser,
        String dbPassword,
        String credentialId,
        String depBasePath
    ) {
        [
            dep_base_path: normalizeDepBasePath(depBasePath),
            db_server    : dbServer?.toString()?.trim() ?: '',
            db_user      : dbUser?.toString()?.trim() ?: '',
            db_password  : dbPassword?.toString() ?: '',
            credential_id: credentialId?.toString()?.trim() ?: '',
            timestamp    : System.currentTimeMillis()
        ]
    }

    /** Replace install with same normalized {@code dep_base_path}, else append. */
    static List<Map> upsertInstall(List<Map> installs, Map newEntry) {
        def key = normalizeDepBasePath(newEntry.dep_base_path?.toString())
        if (!key) {
            throw new IllegalArgumentException(
                'dep_base_path is required to save a CC install in cc_db_context.json (set DEP_BASE_PATH for the CC deploy).'
            )
        }
        List<Map> copy = []
        if (installs) {
            copy.addAll(installs)
        }
        int idx = copy.findIndexOf { Map it ->
            normalizeDepBasePath(it.dep_base_path?.toString()) == key
        }
        Map merged = newInstallEntry(
            newEntry.db_server?.toString(),
            newEntry.db_user?.toString(),
            newEntry.db_password?.toString(),
            newEntry.credential_id?.toString(),
            key
        )
        if (idx >= 0) {
            copy[idx] = merged
        } else {
            copy.add(merged)
        }
        copy
    }

    static Map findInstallByDepBase(List<Map> installs, String depParamRaw) {
        def want = normalizeDepBasePath(depParamRaw)
        if (!want) {
            return null
        }
        installs?.find { Map it ->
            normalizeDepBasePath(it.dep_base_path?.toString()) == want
        } as Map
    }
}
