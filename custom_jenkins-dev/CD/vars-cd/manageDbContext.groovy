// vars/manageDbContext.groovy
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.customerxp.cd.CdCcDbContextJson

/**
 * Manage storing and loading the Database context for the 'cc' module.
 * We store this in a workspace/repo configuration file to persist
 * between jobs (or within a run for shared modules).
 *
 * <p><b>File shape (Option A):</b> {@code { "schema_version": 1, "installs": [ { dep_base_path, db_server, ... }, ... ] }}.
 * Legacy single-object files are migrated on read/save. Each successful CC save <b>upserts</b> the install keyed by
 * normalized {@code dep_base_path} (append new path, replace same path). Other installs are never removed.
 *
 * <p><b>Path:</b> {@code JENKINS_CD_CC_DB_CONTEXT_FILE} only — absolute path on the pipeline node (required; no relative or workspace default).
 *
 * <p>P4-3: When {@code credentialId} is set, {@code dbPassword} should be empty — password lives in Jenkins Credentials only.
 * When {@code JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX} is set, writes must fall under that prefix.
 *
 * <p>{@code depBasePath}: CC / platform install root on the app server — <b>always required</b> on save (pipeline job
 * parameter {@code DEP_BASE_PATH}). No inferring from an existing file. {@link #loadInstallContext} requires a
 * non-empty path to match {@code installs[].dep_base_path} for SHARED_DB lookups.
 */

def save(String repoRoot, String dbServer, String dbUser, String dbPassword, String credentialId = '', String depBasePath = '') {
    def contextFileAbs = getContextFilePath(repoRoot)
    def mergedDep = CdCcDbContextJson.normalizeDepBasePath(depBasePath?.toString() ?: '')
    if (!mergedDep) {
        error 'manageDbContext.save: DEP_BASE_PATH is required to register this CC install in cc_db_context.json (platform install root on the app server).'
    }

    def fExisting = new File(contextFileAbs)
    Map root
    if (fExisting.exists()) {
        try {
            root = CdCcDbContextJson.normalizeSlurpedRoot(new JsonSlurper().parseText(fExisting.text))
        } catch (Exception e) {
            error "manageDbContext.save: cannot parse existing ${contextFileAbs}: ${e.message}"
        }
    } else {
        root = [schema_version: CdCcDbContextJson.SCHEMA_VERSION, installs: []]
    }
    def newEntry = CdCcDbContextJson.newInstallEntry(dbServer, dbUser, dbPassword, credentialId, mergedDep)
    List installsIn = (root.installs instanceof List) ? (List) root.installs : []
    def updatedInstalls = CdCcDbContextJson.upsertInstall(installsIn, newEntry)
    def contextData = [
        schema_version: CdCcDbContextJson.SCHEMA_VERSION,
        installs        : updatedInstalls
    ]

    def jsonString = JsonOutput.prettyPrint(JsonOutput.toJson(contextData))
    if (!isCcContextPathUnderAllowedPrefix(contextFileAbs)) {
        error "manageDbContext.save: JENKINS_CD_CC_DB_CONTEXT_FILE (${contextFileAbs}) is not under JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX — refusing write."
    }
    def fout = new File(contextFileAbs)
    def parent = fout.parentFile
    if (parent && !parent.exists()) {
        parent.mkdirs()
    }
    atomicWriteText(fout, jsonString)
    def ctxLabel = contextFileAbs.contains('/') ? contextFileAbs.tokenize('/').last() : contextFileAbs
    echo "✅ Saved primary CC DB Context (${ctxLabel}) — install dep_base_path=${CdCcDbContextJson.normalizeDepBasePath(mergedDep)} (${updatedInstalls.size()} install(s) in file)."
}

/**
 * Best-effort atomic replace. Temp file is created under {@code java.io.tmpdir} first — not always next to {@code target}
 * — because {@code File.createTempFile(..., parent)} fails with permission denied when the target parent (e.g.
 * {@code /var/lib/jenkins}) is not writable for temp creation even though the final file may be updatable.
 */
private void atomicWriteText(File target, String text) {
    def dir = target.parentFile
    if (dir && !dir.exists()) {
        dir.mkdirs()
    }

    File tmp = null
    try {
        tmp = File.createTempFile('cd_cc_db_ctx_', '.tmp')
    } catch (IOException e) {
        target.text = text
        return
    }

    try {
        tmp.text = text
        try {
            java.nio.file.Files.move(
                tmp.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (Exception ignored) {
            try {
                java.nio.file.Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (Exception ignored2) {
                java.nio.file.Files.copy(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
    } catch (Exception e) {
        try {
            target.text = text
        } catch (Exception ignored) {
            throw e
        }
    } finally {
        if (tmp != null && tmp.exists()) {
            tmp.delete()
        }
    }
}

/**
 * Loads one install from {@code installs[]} where {@code dep_base_path} matches {@code depBasePathParam} (normalized).
 * {@code depBasePathParam} must be non-empty (callers such as SHARED_DB / {@code getDbConfig} require the job
 * {@code DEP_BASE_PATH}). Exception: a single install row with an empty stored {@code dep_base_path} (legacy) may be
 * selected when {@code depBasePathParam} is the path supplied by the user for this run.
 */
def loadInstallContext(String repoRoot, String depBasePathParam = '') {
    def contextFile = getContextFilePath(repoRoot)
    def file = new File(contextFile)
    if (!file.exists()) {
        error "❌ Cannot load CC DB Context: file ${contextFile} does not exist. Did you deploy the 'cc' module first?"
    }
    Map root
    try {
        root = CdCcDbContextJson.normalizeSlurpedRoot(new JsonSlurper().parseText(file.text))
    } catch (Exception e) {
        error "❌ Cannot parse CC DB Context ${contextFile}: ${e.message}"
    }
    List installs = (root.installs instanceof List) ? (List) root.installs : []
    if (!installs) {
        error "❌ cc_db_context.json has no registered CC installs (empty installs[]). Re-run a CC deploy with DEP_BASE_PATH set."
    }
    def depAsk = depBasePathParam?.toString()?.trim() ?: ''
    if (!depAsk) {
        error '❌ DEP_BASE_PATH is required to load a CC DB install from cc_db_context.json (no empty selector).'
    }
    Map pick = CdCcDbContextJson.findInstallByDepBase(installs, depAsk)
    if (!pick && installs.size() == 1) {
        Map only = installs[0] as Map
        if (!CdCcDbContextJson.normalizeDepBasePath(only.dep_base_path?.toString())) {
            pick = only
        }
    }
    if (!pick) {
        def known = installs.collect { CdCcDbContextJson.normalizeDepBasePath(it.dep_base_path?.toString()) }.findAll { it }.unique().join(', ')
        def want = CdCcDbContextJson.normalizeDepBasePath(depAsk)
        error "❌ DEP_BASE_PATH '${want}' does not match any install in cc_db_context.json. Known dep_base_path value(s): ${known ?: '(none listed)'}"
    }
    return jsonTreeToSerializable(pick)
}

/**
 * Resolves the single registered install when the file has exactly one {@code dep_base_path}; for ambiguous files,
 * callers must use {@link #loadInstallContext} with an explicit path.
 */
def load(String repoRoot) {
    def d = readDepBasePathIfUnambiguous(repoRoot)?.toString()?.trim() ?: ''
    if (!d) {
        error 'manageDbContext.load: cc_db_context.json has zero or multiple installs, or the sole install has no dep_base_path — use loadInstallContext(repoRoot, DEP_BASE_PATH).'
    }
    return loadInstallContext(repoRoot, d)
}

/**
 * Returns normalized {@code dep_base_path} when the file exists and contains exactly one install with a non-empty path;
 * otherwise empty string (caller supplies {@code DEP_BASE_PATH}).
 */
String readDepBasePathIfUnambiguous(String repoRoot) {
    def contextFile = getContextFilePath(repoRoot)
    def f = new File(contextFile)
    if (!f.exists()) {
        return ''
    }
    try {
        Map root = CdCcDbContextJson.normalizeSlurpedRoot(new JsonSlurper().parseText(f.text))
        List inst = (root.installs instanceof List) ? (List) root.installs : []
        if (inst.size() == 1) {
            return CdCcDbContextJson.normalizeDepBasePath((inst[0] as Map).dep_base_path?.toString())
        }
    } catch (Exception ignored) {
        return ''
    }
    return ''
}

/** Returns true if the CC DB context file exists (e.g. after a prior CC deploy in same workspace). */
boolean exists(String repoRoot) {
    def contextFile = getContextFilePath(repoRoot)
    return new File(contextFile).exists()
}

def getContextFilePath(String repoRoot = null) {
    def abs = env.JENKINS_CD_CC_DB_CONTEXT_FILE?.toString()?.trim()
    if (!abs) {
        error 'manageDbContext: set JENKINS_CD_CC_DB_CONTEXT_FILE (absolute path on the pipeline node to cc_db_context.json).'
    }
    return new File(abs).absolutePath
}

/** Same path-prefix rule as {@code persistDbConfig} when {@code JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX} is set. */
private boolean isCcContextPathUnderAllowedPrefix(String filePath) {
    def prefixRaw = env.JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX?.toString()?.trim()
    if (!prefixRaw) {
        return true
    }
    def norm = { String s ->
        if (!s) {
            return ''
        }
        s.replace('\\', '/').replaceAll(/\/+$/, '')
    }
    def p = norm(filePath)
    def px = norm(prefixRaw)
    if (!px) {
        return true
    }
    return p == px || p.startsWith(px + '/')
}
