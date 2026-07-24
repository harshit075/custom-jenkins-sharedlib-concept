// vars/getDbConfig.groovy
import groovy.json.JsonSlurper
import org.customerxp.cd.CdDbConfigNormalize
import org.customerxp.cd.CdDbCredentialsJson
import org.customerxp.cd.CdSharedLibVarLookup

/**
 * Helper to retrieve the active Database configuration details based on module type.
 * For primary/custom modules, it reads the user-selected input (Dynamic JSON).
 * For shared modules, it retrieves the cached CC context.
 *
 * <p>DB setups JSON: {@code JENKINS_CD_DB_CONFIG_FILE} only (absolute path on the pipeline node). No libraryResource fallback.
 */
def call(Map args) {
    String moduleType = args.moduleType
    String repoRoot = args.repoRoot
    def params = args.params
    String setupConfigsFile = args.setupConfigsFile?.toString()?.trim()

    def requireDbType = { Map cfg, String setupId ->
        def t = cfg?.DB_TYPE?.toString()?.trim()
        if (!t) {
            error "❌ DB_TYPE missing or empty for setup '${setupId}' in db-config.json — add DB_TYPE to that setup."
        }
        return t
    }
    def requireDbPort = { Map cfg, String setupId ->
        def p = cfg?.DB_PORT?.toString()?.trim()
        if (!p) {
            error "❌ DB_PORT missing or empty for setup '${setupId}' in db-config.json — add DB_PORT to that setup."
        }
        return p
    }
    /** Optional [DB_SERVER: '...', DB_CREDENTIALS_INPUT: '...'] to override params for per-module deploys */
    Map paramOverrides = args.paramOverrides ?: [:]
    def pDbServer = { paramOverrides.containsKey('DB_SERVER') ? paramOverrides.DB_SERVER : params.DB_SERVER }
    def pDbCreds = { paramOverrides.containsKey('DB_CREDENTIALS_INPUT') ? paramOverrides.DB_CREDENTIALS_INPUT : params.DB_CREDENTIALS_INPUT }

    def dbConfigMap = loadDbConfigMapFromPath(setupConfigsFile)

    def config = [
        DB_TYPE: '',
        DB_SID: '',
        DB_IP: '',
        DB_PORT: '',
        DB_USER: '',
        DB_PASSWORD: '',
        credential_id: ''
    ]

    if (moduleType in ['PRIMARY_DB', 'CUSTOM_DB']) {
        // Active DB settings chosen in UI (or per-module overrides)
        def setupId = pDbServer()?.toString()?.trim()
        if (!setupId || !dbConfigMap[setupId]) {
            error "❌ Setup '${setupId}' not found in DB config (${setupConfigsFile})"
        }
        
        def cfg = dbConfigMap[setupId]
        config.DB_TYPE = requireDbType(cfg as Map, setupId)
        config.DB_SID = cfg.DB_SID?.toString() ?: ''
        config.DB_IP = cfg.DB_IP?.toString() ?: ''
        config.DB_PORT = requireDbPort(cfg as Map, setupId)

        // Parse the dynamic credentials JSON input
        def dbCredsStr = pDbCreds()?.toString()?.trim()
        if (!dbCredsStr) {
            error '❌ DB_CREDENTIALS_INPUT / per-module credentials payload is empty — select credentials for this module.'
        }
        def credsJson = CdDbCredentialsJson.parseCredentialJson(dbCredsStr)
        def userType = credsJson.type?.toString()

        if (userType == 'EXISTING') {
            config.DB_USER = credsJson.user?.toString() ?: ''
            // P4-3: Prefer Jenkins credential; never use plaintext password from file when credential_id is set.
            config.credential_id = cfg.credential_id?.toString()?.trim() ?: cfg.credentialId?.toString()?.trim() ?: ''
            config.DB_PASSWORD = cfg.DB_PASSWORD?.toString() ?: ''
            if (config.credential_id?.trim()) {
                config.DB_PASSWORD = ''
            }
        } else if (userType == 'NEW') {
            config.DB_USER = credsJson.user?.toString() ?: ''
            config.DB_PASSWORD = credsJson.pwd?.toString() ?: ''
            config.credential_id = '' // New user implies raw password provided
        } else {
            error "❌ Invalid DB_CREDENTIALS_INPUT type '${userType}'"
        }
        
        if (!config.DB_USER || (!config.DB_PASSWORD && !config.credential_id)) {
            error "❌ DB user and password/credential are required for ${moduleType}. Setup='${setupId}', userType='${userType}', DB_USER='${config.DB_USER}', credential_id='${config.credential_id}'"
        }

    } else if (moduleType == 'SHARED_DB') {
        // Reuse context from prior CC deploy — cc_db_context.json only (no env fallback).
        def manageDbContextScript = CdSharedLibVarLookup.resolve(this, 'manageDbContext')
        if (!manageDbContextScript) {
            error "❌ Could not load manageDbContext shared library script."
        }

        def ctxPath = manageDbContextScript.getContextFilePath(repoRoot)
        if (!manageDbContextScript.exists(repoRoot)) {
            error "❌ SHARED_DB modules require cc_db_context.json at JENKINS_CD_CC_DB_CONTEXT_FILE — not found at ${ctxPath}. " +
                'Deploy a PRIMARY_DB (cc) module first so manageDbContext.save creates the file, or fix the path in Manage Jenkins global env.'
        }

        def depForShared = args.depBasePathForSharedDb?.toString()?.trim() ?: ''
        if (!depForShared) {
            error '❌ DEP_BASE_PATH is required for SHARED_DB modules. Set the job parameter to the CC install root; it must match a dep_base_path entry in cc_db_context.json (DB credentials are taken from that install row only).'
        }
        def ccContext = manageDbContextScript.loadInstallContext(repoRoot, depForShared)
        def setupId = ccContext.db_server
        if (!setupId || !dbConfigMap[setupId]) {
            error "❌ Shared Module Deployment Failed: Cached CC DB context points to an invalid or missing server '${setupId}'"
        }
        def cfg = dbConfigMap[setupId]
        config.DB_TYPE = requireDbType(cfg as Map, setupId)
        config.DB_SID = cfg.DB_SID?.toString() ?: ''
        config.DB_IP = cfg.DB_IP?.toString() ?: ''
        config.DB_PORT = requireDbPort(cfg as Map, setupId)
        config.DB_USER = ccContext.db_user
        config.DB_PASSWORD = ccContext.db_password
        config.credential_id = ccContext.credential_id ?: ''
        if (config.credential_id?.toString()?.trim()) {
            config.DB_PASSWORD = ''
        }
    }

    return config
}

/** Load setups map from {@code JENKINS_CD_DB_CONFIG_FILE} (passed as {@code setupConfigsFile}). */
private Map loadDbConfigMapFromPath(String path) {
    if (!path?.trim()) {
        error 'getDbConfig: set JENKINS_CD_DB_CONFIG_FILE (absolute path on the pipeline node to your DB setups JSON).'
    }
    def f = new File(path.trim())
    if (!f.isFile()) {
        error "getDbConfig: JENKINS_CD_DB_CONFIG_FILE is not a readable file on this node (${path})."
    }
    try {
        return CdDbConfigNormalize.normalizeFull(new JsonSlurper().parseText(f.getText('UTF-8')))
    } catch (Exception e) {
        error "getDbConfig: cannot parse JENKINS_CD_DB_CONFIG_FILE (${path}): ${e.message}"
    }
}
