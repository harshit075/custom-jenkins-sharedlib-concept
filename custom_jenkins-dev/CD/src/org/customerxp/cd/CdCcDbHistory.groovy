package org.customerxp.cd

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Option A: controller-side JSON file listing recent CC DB {@code setupId} values after successful CC context saves.
 * Used by job parameter {@code CC_RECENT_DB_SETUP} and {@code recordCcDbHistory} pipeline var.
 */
@SuppressWarnings(['unused'])
final class CdCcDbHistory {

    /** First choice for {@code CC_RECENT_DB_SETUP} — not a real setup name. */
    static final String CHOICE_NONE = '__CD_CC_RECENT_NONE__'

    static final int DEFAULT_MAX_ENTRIES = 50

    private CdCcDbHistory() {}

    static boolean isPathUnderAllowedPrefix(String filePath, String prefixRaw) {
        if (!prefixRaw?.toString()?.trim()) {
            return true
        }
        def norm = { String s ->
            if (!s) return ''
            s.replace('\\', '/').replaceAll(/\/+$/, '')
        }
        def p = norm.call(filePath?.toString())
        def px = norm.call(prefixRaw.toString())
        if (!px) return true
        return p == px || p.startsWith(px + '/')
    }

    static Map readRoot(String absPath) {
        if (!absPath?.trim()) {
            return [version: 1, entries: []]
        }
        def f = new File(absPath.trim())
        if (!f.exists()) {
            return [version: 1, entries: []]
        }
        try {
            def o = new JsonSlurper().parseText(f.getText('UTF-8'))
            if (o instanceof Map && o.entries instanceof List) {
                return o as Map
            }
        } catch (Exception ignored) {
        }
        [version: 1, entries: []]
    }

    /**
     * Inserts {@code newEntry} at the front, removes older rows with the same {@code setupId}, trims to {@code maxEntries}.
     * No-op if {@code allowedPrefix} is set and {@code absPath} is not under it (same gate as {@code persistDbConfig}).
     */
    static void prependDedupe(String absPath, Map newEntry, int maxEntries, String allowedPrefix) {
        if (!absPath?.trim() || !newEntry) {
            return
        }
        def path = absPath.trim()
        if (!isPathUnderAllowedPrefix(path, allowedPrefix)) {
            return
        }
        def sid = newEntry.setupId?.toString()?.trim()
        if (!sid) {
            return
        }
        def root = readRoot(path)
        def list = new ArrayList<Map>(root.entries ?: [])
        list.removeAll { entry ->
            (entry instanceof Map) && (entry.setupId?.toString()?.trim() == sid)
        }
        list.add(0, new LinkedHashMap(newEntry))
        while (list.size() > maxEntries) {
            list.remove(list.size() - 1)
        }
        def out = [version: 1, entries: list]
        def f = new File(path)
        def parent = f.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        f.text = JsonOutput.prettyPrint(JsonOutput.toJson(out))
    }

    static String lookupDbUserForSetup(String dbConfigPath, String setupId) {
        if (!dbConfigPath?.trim() || !setupId?.trim()) {
            return ''
        }
        try {
            def f = new File(dbConfigPath.trim())
            if (!f.exists()) {
                return ''
            }
            def raw = new JsonSlurper().parseText(f.text)
            def cfgMap = CdDbConfigNormalize.normalizeFull(raw)
            def cfg = cfgMap[setupId.trim()]
            if (cfg instanceof Map) {
                return (cfg.DB_USER ?: cfg.user)?.toString()?.trim() ?: ''
            }
        } catch (Exception ignored) {
        }
        ''
    }

    /**
     * If the primary (CC) module has no {@code setupId} in {@code perModuleDbSelections} but {@code recentSetupId}
     * is set, inject {@code EXISTING} credentials using {@code DB_USER} from {@code JENKINS_CD_DB_CONFIG_FILE}.
     */
    static void mergePrimaryFromRecent(Map perModuleDbSelections, String primaryModuleId, String recentSetupId, String dbConfigPath) {
        if (!primaryModuleId?.trim() || !recentSetupId?.trim()) {
            return
        }
        def rid = recentSetupId.trim()
        if (rid == CHOICE_NONE || rid.startsWith('[')) {
            return
        }
        def pid = primaryModuleId.trim().toLowerCase()
        def cur = perModuleDbSelections.get(pid)
        def hasSetup = false
        if (cur instanceof Map) {
            hasSetup = ((cur.setupId ?: cur.dbServer ?: cur.DB_SERVER)?.toString()?.trim()) as boolean
        }
        if (hasSetup) {
            return
        }
        def user = lookupDbUserForSetup(dbConfigPath, rid)
        if (!user) {
            throw new IllegalArgumentException(
                "CC_RECENT_DB_SETUP: setup '${rid}' is missing or has no DB_USER in JENKINS_CD_DB_CONFIG_FILE — pick another history row or paste **PER_MODULE_DB_JSON_SUBMITTED** for **${pid}**.")
        }
        perModuleDbSelections[pid] = [
            setupId    : rid,
            credentials: [type: 'EXISTING', user: user, pwd: '']
        ]
    }
}
