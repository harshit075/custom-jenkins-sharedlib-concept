package org.customerxp.cd

/**
 * P3-3: Single implementation for DB setup JSON shapes ({@code databases[]} vs flat map) used by
 * pipeline vars and Active Choice scripts ({@link CdActiveChoiceRuntime} delegates here).
 * Missing {@code DB_TYPE} / {@code DB_PORT} normalize to empty string — callers (e.g. {@code getDbConfig}) apply defaults.
 */
@SuppressWarnings(['unused'])
final class CdDbConfigNormalize {

    /** Full deploy shape: used by {@code vars-cd/getDbConfig} and Active Choice ({@code __cdNormalizeDbFullGlobal}). */
    static Map normalizeFull(Object raw) {
        def normalized = [:]

        if (raw instanceof Map && raw.databases instanceof List) {
            raw.databases.each { item ->
                def name = item?.name?.toString()?.trim()
                if (name) {
                    normalized[name] = [
                        DB_TYPE      : item.type?.toString() ?: item.DB_TYPE?.toString() ?: '',
                        DB_SID       : item.sid?.toString() ?: item.DB_SID?.toString() ?: '',
                        DB_IP        : item.host?.toString() ?: item.DB_IP?.toString() ?: '',
                        DB_PORT      : item.port?.toString() ?: item.DB_PORT?.toString() ?: '',
                        DB_USER      : item.user?.toString() ?: item.DB_USER?.toString() ?: '',
                        DB_PASSWORD  : item.password?.toString() ?: item.DB_PASSWORD?.toString() ?: '',
                        credential_id: item.credential_id?.toString()?.trim() ?: item.credentialId?.toString()?.trim() ?: ''
                    ]
                }
            }
            return normalized
        }

        if (raw instanceof Map) {
            raw.each { key, value ->
                if (!key.toString().startsWith('_') && value instanceof Map) {
                    normalized[key.toString()] = [
                        DB_TYPE      : value.DB_TYPE?.toString() ?: value.type?.toString() ?: '',
                        DB_SID       : value.DB_SID?.toString() ?: value.sid?.toString() ?: '',
                        DB_IP        : value.DB_IP?.toString() ?: value.host?.toString() ?: '',
                        DB_PORT      : value.DB_PORT?.toString() ?: value.port?.toString() ?: '',
                        DB_USER      : value.DB_USER?.toString() ?: value.user?.toString() ?: '',
                        DB_PASSWORD  : value.DB_PASSWORD?.toString() ?: value.password?.toString() ?: '',
                        credential_id: value.credential_id?.toString()?.trim() ?: value.credentialId?.toString()?.trim() ?: ''
                    ]
                }
            }
        }

        normalized
    }

    /** Credential-focused projection (user/password/credential_id per setup name). */
    static Map normalizeCredentials(Object raw) {
        def normalized = [:]
        if (raw instanceof Map && raw.databases instanceof List) {
            raw.databases.each { item ->
                def name = item?.name?.toString()?.trim()
                if (name) {
                    normalized[name] = [
                        DB_USER      : item.user?.toString() ?: item.DB_USER?.toString() ?: '',
                        DB_PASSWORD  : item.password?.toString() ?: item.DB_PASSWORD?.toString() ?: '',
                        credential_id: item.credential_id?.toString() ?: ''
                    ]
                }
            }
            return normalized
        }
        if (raw instanceof Map) {
            raw.each { key, value ->
                if (!key.toString().startsWith('_') && value instanceof Map) {
                    normalized[key.toString()] = value
                }
            }
        }
        normalized
    }

    /** Per-module DB UI metadata (user + credential id hints). */
    static Map normalizePerModuleMeta(Object raw) {
        def normalized = [:]
        if (raw instanceof Map && raw.databases instanceof List) {
            raw.databases.each { item ->
                def name = item?.name?.toString()?.trim()
                if (name) {
                    normalized[name] = [
                        DB_USER      : item.user?.toString() ?: item.DB_USER?.toString() ?: '',
                        credential_id: item.credential_id?.toString() ?: item.credentialId?.toString() ?: ''
                    ]
                }
            }
            return normalized
        }
        if (raw instanceof Map) {
            raw.each { key, value ->
                if (!key.toString().startsWith('_') && value instanceof Map) {
                    normalized[key.toString()] = [
                        DB_USER      : value.DB_USER?.toString() ?: value.user?.toString() ?: '',
                        credential_id: value.credential_id?.toString() ?: value.credentialId?.toString() ?: ''
                    ]
                }
            }
        }
        normalized
    }
}
