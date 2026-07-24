// vars/executeDeployPipeline.groovy
// Single class file: one `call(Map)` only — duplicate methods break Jenkins shared library compilation.
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.customerxp.cd.CdDeployModuleOrder
import org.customerxp.cd.CdRegistryDbRole
import org.customerxp.cd.CdPipelineUiJson
import org.customerxp.cd.CdDbContextPaths
import org.customerxp.cd.CdNexusEnvUrls
import org.customerxp.cd.CdDebug
import org.customerxp.cd.CdDbCredentialsJson
import org.customerxp.cd.CdSharedLibVarLookup
import org.customerxp.cd.CdDeploymentPlan
import org.customerxp.cd.CdDeployKinds
import org.customerxp.cd.CdRemoteScriptPath
/**
 * Executes the entire deployment logic for the CD pipeline.
 * Extracts validation, variable mapping, environment preparation, and deployment triggers 
 * out of the Jenkinsfile to keep it clean and match the Shared Pipeline Library architecture.
 */
def call(Map args) {
    def params = args.params
    def env = args.env
    def repoRoot = CdDbContextPaths.normalizeRepoRoot((args.repoRoot ?: env.WORKSPACE)?.toString())
    if (!repoRoot?.trim()) {
        error 'executeDeployPipeline: WORKSPACE/repoRoot is empty — cannot resolve CC DB context paths or module registry.'
    }

    if (CdDebug.enabled(env)) {
        cdDebug(message: 'CD pipeline debug logging enabled (JENKINS_CD_DEBUG)')
    }

    // Normalize MODULE_VERSION_CHOICES (plugin may pass List or comma-separated string)
    def rawChoices = params.MODULE_VERSION_CHOICES
    def choicesStr = normalizeVersionChoicesParam(rawChoices)

    // ==========================================
    // 1. Validation
    // ==========================================
    if (!params.MODULE_SELECTED) error 'At least one module must be selected'
    
    def selModules = []
    def rawMod = params.MODULE_SELECTED
    if (rawMod instanceof Collection) {
        selModules = rawMod.collect { it?.toString()?.trim()?.toLowerCase() }.findAll { it }
    } else if (rawMod != null && rawMod.getClass().isArray()) {
        selModules = rawMod.collect { it?.toString()?.trim()?.toLowerCase() }.findAll { it }
    } else if (rawMod != null && rawMod.toString().trim()) {
        rawMod.toString().trim().toLowerCase().tokenize(',').each { moduleName ->
            def trimmedModule = moduleName ? moduleName.trim() : ''
            if (trimmedModule) selModules.add(trimmedModule)
        }
    }
    def moduleRegistry = loadModuleRegistry()
    def regRef = moduleRegistry.gitRef?.toString()?.trim() ?: ''
    echo "CD deploy: using module registry version=${moduleRegistry.version} gitRef=${regRef ?: '—'} source=${moduleRegistry._registryLoadSource}"
    // P1-T7: reject legacy/typo module ids clearly (unknown ids used to fall through as “non-cc_setup”).
    selModules.each { mid ->
        if (!findRegistryModule(moduleRegistry, mid)) {
            error "MODULE_SELECTED: unknown module '${mid}' — not defined in the CD module registry (version ${moduleRegistry.version ?: '?'})."
        }
    }
    // P3-1: Module ids that use per-module DB flows — derived only from registry dbRole (not SHARED_DB).
    def registryPerModuleDbIds = perModuleDbModuleIds(moduleRegistry)
    def ccSelected = selModules.any { findRegistryModule(moduleRegistry, it)?.deployKind?.toString() == CdDeployKinds.CC_SETUP }
    def selectedPerModuleDbModules = selModules.findAll { it in registryPerModuleDbIds }
    def needsGlobalAppServer = selModules.any { !(it in registryPerModuleDbIds) }

    // Canonical per-module DB JSON: **PER_MODULE_DB_JSON_SUBMITTED** only at deploy.
    // **PER_MODULE_DB_JSON** Active Choices widget is UX-only (table + live preview); it is not used for deploy ingest.
    def perModuleDbRaw = params.get('PER_MODULE_DB_JSON_SUBMITTED')?.toString()?.trim() ?: ''
    def perModuleDbRawSource = 'params.PER_MODULE_DB_JSON_SUBMITTED'
    if (selectedPerModuleDbModules && !selectedPerModuleDbModules.isEmpty() && isPerModuleDbParamEmpty(perModuleDbRaw)) {
        error "PER_MODULE_DB_JSON_SUBMITTED is required for: ${selectedPerModuleDbModules.join(', ')}. " +
            'Fill **PER_MODULE_DB_JSON**, use **Copy JSON to clipboard**, paste into **PER_MODULE_DB_JSON_SUBMITTED**.'
    }
    def perModuleDbSelections = parsePerModuleDbSelections(perModuleDbRaw)
    logPerModuleDbIngest(env, perModuleDbRaw, perModuleDbRawSource, perModuleDbSelections, registryPerModuleDbIds as List, selectedPerModuleDbModules)

    def configPath = env.JENKINS_CD_SERVERS_CONFIG_FILE

    if (!choicesStr || !choicesStr.contains(':')) {
        error "MODULE_VERSION_CHOICES: Select a version for each module in the table above. If the table is empty, select module(s) first and reload."
    }

    // Single APP_SERVER for every module on the application host (cc, icms, ucms, ncrp, and shared modules).
    if (!params.APP_SERVER?.toString()?.trim()) {
        error 'APP_SERVER is required. Select the application server for this deployment.'
    }
    def appServerRaw = params.APP_SERVER.toString().trim()
    if (appServerRaw.startsWith('[DEBUG]') || appServerRaw.startsWith('[ERROR]') || appServerRaw.startsWith('[HIDDEN]')) {
        error "APP_SERVER: Select a real server from the list (do not leave the error/debug placeholder selected). If the list shows only [ERROR]..., fix JENKINS_CD_SERVERS_CONFIG_FILE and JSON on the controller."
    }

    // Per-module DB + credentials for registry PRIMARY_DB/CUSTOM_DB modules
    selectedPerModuleDbModules.each { mod ->
        def moduleDb = resolvePerModuleDbSelection(perModuleDbSelections, mod, params)
        def dbV = moduleDb.setupId
        def credV = moduleDb.credentialsJson
        if (CdPipelineUiJson.isPlaceholderAppOrDbChoice(dbV)) {
            echoPerModuleDbValidationFailure(mod, dbV, credV, perModuleDbSelections, perModuleDbRaw, perModuleDbRawSource)
            error "Per-module DB (${mod}): Choose a DB setup in **PER_MODULE_DB_JSON_SUBMITTED** JSON (setupId for this module)."
        }
        if (!credV || credV == 'SHARED_MODULE_NO_INPUT' || credV.startsWith('[HIDDEN]')) {
            echoPerModuleDbValidationFailure(mod, dbV, credV, perModuleDbSelections, perModuleDbRaw, perModuleDbRawSource)
            error "Per-module DB (${mod}): Provide credentials in **PER_MODULE_DB_JSON_SUBMITTED** JSON."
        }
        try {
            def credsJson = CdDbCredentialsJson.parseCredentialJson(credV)
            if (credsJson.type == 'NEW' && (!credsJson.user || !credsJson.pwd)) {
                echoPerModuleDbValidationFailure(mod, dbV, credV, perModuleDbSelections, perModuleDbRaw, perModuleDbRawSource)
                error "Per-module DB (${mod}, NEW): Username and Password are required in **PER_MODULE_DB_JSON_SUBMITTED** JSON."
            } else if (credsJson.type == 'EXISTING' && !credsJson.user) {
                echoPerModuleDbValidationFailure(mod, dbV, credV, perModuleDbSelections, perModuleDbRaw, perModuleDbRawSource)
                error "Per-module DB (${mod}, EXISTING): Existing user could not be determined in **PER_MODULE_DB_JSON_SUBMITTED** JSON."
            }
        } catch (Exception e) {
            echoPerModuleDbValidationFailure(mod, dbV, credV, perModuleDbSelections, perModuleDbRaw, perModuleDbRawSource)
            error "Failed to parse per-module DB JSON entry for ${mod} in **PER_MODULE_DB_JSON_SUBMITTED**: ${e.message}"
        }
    }

    // ==========================================
    // 2. Variable Printing
    // ==========================================
    echo "Modules: ${params.MODULE_SELECTED}"
    echo "Version choices: ${choicesStr}"
    echo "JDK: ${params.JDK_VERSION}"
    echo "Per-module infra (registry PRIMARY_DB/CUSTOM_DB): ${selectedPerModuleDbModules.join(', ') ?: '(none)'}"
    echo "Includes shared modules (use same APP_SERVER): ${needsGlobalAppServer}"

    // ==========================================
    // 3. Prepare CD Env & Execute Deploy
    // ==========================================
    // Provide isolated workspace logic inside Jenkins node correctly handled by caller
    def prepareCDEnvFileScript = CdSharedLibVarLookup.resolve(this, 'prepareCDEnvFile')
    def runDeployOnAppServerScript = CdSharedLibVarLookup.resolve(this, 'runDeployOnAppServer')
    def getDbConfigScript = CdSharedLibVarLookup.resolve(this, 'getDbConfig')
    def manageDbContextScript = CdSharedLibVarLookup.resolve(this, 'manageDbContext')
    def persistDbConfigScript = CdSharedLibVarLookup.resolve(this, 'persistDbConfig')

    if (!prepareCDEnvFileScript || !runDeployOnAppServerScript || !getDbConfigScript || !manageDbContextScript) {
        error "❌ Failed to load one or more CD shared library scripts. Ensure CD vars are published in the shared library."
    }
    echo "CD deploy (P5-2): CC DB context path → ${manageDbContextScript.getContextFilePath(repoRoot)}"

    def dbConfigPath = env.JENKINS_CD_DB_CONFIG_FILE?.toString()?.trim() ?: ''

    /** P4-3: setup name → Jenkins credential id returned by persistDbConfig after NEW credentials. */
    def persistedCredBySetup = [:]

    if (persistDbConfigScript && dbConfigPath?.trim() && !selectedModulesAreAllSharedDb(moduleRegistry, selModules)) {
        def tryPersistNewCreds = { String setupId, String credV ->
            if (isSkippableLegacyGlobalDbPersistInput(setupId, credV)) {
                return
            }
            try {
                def credsJson = CdDbCredentialsJson.parseCredentialJson(credV)
                if (credsJson.type?.toString() != 'NEW') return
                def u = credsJson.user?.toString()?.trim()
                def p = credsJson.pwd?.toString() ?: ''
                if (!u || !p) return
                if (CdPipelineUiJson.isPlaceholderAppOrDbChoice(setupId)) return
                def newCid = persistDbConfigScript.call(path: dbConfigPath, setupName: setupId, user: u, password: p)
                if (newCid?.toString()?.trim()) {
                    persistedCredBySetup[setupId] = newCid.toString().trim()
                }
            } catch (Exception e) {
                echo "WARN: persistDbConfig skipped: ${e.message}"
            }
        }
        selectedPerModuleDbModules.each { mod ->
            def moduleDb = resolvePerModuleDbSelection(perModuleDbSelections, mod, params)
            tryPersistNewCreds(moduleDb.setupId, moduleDb.credentialsJson)
        }
    } else if (needsGlobalAppServer && selectedModulesAreAllSharedDb(moduleRegistry, selModules)) {
        cdDebug(
            'persistDbConfig: skip — every selected module has registry dbRole SHARED_DB ' +
                '(deploy DB comes from cc_db_context.json for DEP_BASE_PATH).'
        )
    }

    // Persist CC DB context for shared-module steps when PRIMARY_DB (typically cc) is selected.
    if (selModules.any { CdRegistryDbRole.dbRoleForModule(moduleRegistry, it) == CdRegistryDbRole.PRIMARY_DB }) {
        def primaryDbMod = moduleRegistry.modules?.find { CdRegistryDbRole.normalizeDbRole(it?.dbRole) == CdRegistryDbRole.PRIMARY_DB }
        def primaryId = primaryDbMod?.id?.toString()?.trim()?.toLowerCase()
        def primaryModuleDb = primaryId ? resolvePerModuleDbSelection(perModuleDbSelections, primaryId, params) : [:]
        def primarySetupId = primaryModuleDb.setupId?.toString()?.trim() ?: ''
        def primaryCredJson = primaryModuleDb.credentialsJson?.toString()?.trim() ?: ''
        if (!primarySetupId || CdPipelineUiJson.isPlaceholderAppOrDbChoice(primarySetupId)) {
            error "Per-module DB (${primaryId ?: 'PRIMARY_DB'}): Choose a DB setup in **PER_MODULE_DB_JSON_SUBMITTED** JSON (cc / primary module row)."
        }
        if (!primaryCredJson || primaryCredJson == 'SHARED_MODULE_NO_INPUT' || primaryCredJson.startsWith('[HIDDEN]')) {
            error "Per-module DB (${primaryId ?: 'PRIMARY_DB'}): Provide credentials in **PER_MODULE_DB_JSON_SUBMITTED** JSON (not global DB_SERVER / DB_CREDENTIALS_INPUT)."
        }
        def ccDbCfg = getDbConfigScript.call(
            moduleType: CdRegistryDbRole.PRIMARY_DB,
            repoRoot: repoRoot,
            params: params,
            paramOverrides: [
                DB_SERVER: primarySetupId,
                DB_CREDENTIALS_INPUT: primaryCredJson
            ],
            setupConfigsFile: dbConfigPath
        )
        def setupId = primarySetupId
        // P4-3: after persist, JSON + Jenkins store hold credential_id — avoid writing plaintext password to workspace context.
        if (persistedCredBySetup[setupId]?.toString()?.trim()) {
            ccDbCfg.credential_id = persistedCredBySetup[setupId].toString().trim()
            ccDbCfg.DB_PASSWORD = ''
        }
        def ctxPwd = ccDbCfg.credential_id?.toString()?.trim() ? '' : (ccDbCfg.DB_PASSWORD?.toString() ?: '')
        manageDbContextScript.save(
            repoRoot,
            setupId,
            ccDbCfg.DB_USER,
            ctxPwd,
            ccDbCfg.credential_id ?: '',
            params.DEP_BASE_PATH?.toString()?.trim() ?: ''
        )
        try {
            def recordHist = CdSharedLibVarLookup.resolve(this, 'recordCcDbHistory')
            if (recordHist && env.JENKINS_CD_CC_DB_HISTORY_FILE?.toString()?.trim() && setupId && !CdPipelineUiJson.isPlaceholderAppOrDbChoice(setupId)) {
                recordHist(setupId: setupId)
            }
        } catch (Throwable t) {
            echo "WARN: CC DB history not recorded: ${t.message}"
        }
    }

    def getCDDefaultsScript = CdSharedLibVarLookup.resolve(this, 'getCDDefaults')

    def jdkRaw = params.JDK_VERSION?.toString()?.trim()
    if (!jdkRaw) {
        error 'executeDeployPipeline: JDK_VERSION is empty. Set Manage Jenkins globals CX_JDK_VERSION_OPTIONS and CX_JDK_VERSION_DEFAULT so the job JDK parameter is populated.'
    }
    def jdkForDeploy = jdkRaw.replaceAll('-', '')
    def ccPortParam = params.CC_PORT?.toString()?.trim()
    def ccPort = ''
    if (ccSelected) {
        if (!ccPortParam || !(ccPortParam ==~ /\d+/)) {
            error 'CC_PORT is required (numeric) when a cc_setup module is selected — set the CC_PORT job parameter.'
        }
        ccPort = ccPortParam
    } else if (ccPortParam && ccPortParam ==~ /\d+/) {
        ccPort = ccPortParam
    }
    def versionChoicesStr = choicesStr
    def moduleVersionMap = [:]
    if (versionChoicesStr) {
        versionChoicesStr.tokenize(',').each { choice ->
            def parts = choice.trim().split(':', 2)
            if (parts.length == 2) moduleVersionMap[parts[0].trim()] = parts[1].trim()
        }
    }
    def missingModuleVersions = selModules.findAll { !moduleVersionMap[it]?.trim() }
    if (!missingModuleVersions.isEmpty()) {
        error "MODULE_VERSION_CHOICES: Provide a version for each selected module. Missing: ${missingModuleVersions.join(', ')}"
    }
    def instanceCount = resolveCdEnv('JENKINS_CD_MODULE_INSTANCE_COUNT', env)?.toString()?.trim()
    if (!instanceCount) {
        error 'executeDeployPipeline: set JENKINS_CD_MODULE_INSTANCE_COUNT (Manage Jenkins → Global properties → Environment variables, or folder/job env). No pipeline parameter.'
    }
    def nexusPackageBase = CdNexusEnvUrls.modulePackageBase(env)
    if (!nexusPackageBase) {
        error 'executeDeployPipeline: set JENKINS_NEXUS_URL + JENKINS_NEXUS_MODULE_ARTIFACT_PATH for module .tgz downloads.'
    }

    def depParam = params.DEP_BASE_PATH?.toString()?.trim() ?: ''
    if (!depParam) {
        error 'DEP_BASE_PATH is required for every CD deployment (including cc and all other modules). Set the CC/platform install root on the app server. For SHARED_DB modules it must match a dep_base_path entry in cc_db_context.json.'
    }
    def effectiveDepBase = depParam

    def baseEnvTemplate = [
        JDK_VERSION: jdkForDeploy,
        DEP_BASE_PATH: effectiveDepBase,
        MODULE_INSTANCE_COUNT: instanceCount,
        NEXUS_PACKAGE_BASE_URL: nexusPackageBase.toString().replaceAll('/+$', '')
    ]
    if (env.NEXUS_PACKAGE_USER?.trim()) {
        baseEnvTemplate.NEXUS_PACKAGE_USER = env.NEXUS_PACKAGE_USER.trim()
        if (env.NEXUS_PACKAGE_PASSWORD?.trim()) baseEnvTemplate.NEXUS_PACKAGE_PASSWORD = env.NEXUS_PACKAGE_PASSWORD.trim()
    }

    def ccSetupScriptUrl = getCDDefaultsScript ? getCDDefaultsScript.getCcSetupScriptNexusUrl() : CdNexusEnvUrls.ccSetupScriptUrl(env)
    def moduleDeployScriptUrl = getCDDefaultsScript ? getCDDefaultsScript.getModuleDeployScriptNexusUrl() : CdNexusEnvUrls.moduleDeployScriptUrl(env)
    if (!ccSetupScriptUrl?.trim()) {
        error 'executeDeployPipeline: could not resolve cc_setup script URL (JENKINS_NEXUS_URL + JENKINS_NEXUS_SCRIPTS_URI_PATH + JENKINS_NEXUS_CC_SETUP_SCRIPT_NAME).'
    }
    if (!moduleDeployScriptUrl?.trim()) {
        error 'executeDeployPipeline: could not resolve module_deploy script URL (JENKINS_NEXUS_URL + JENKINS_NEXUS_SCRIPTS_URI_PATH + JENKINS_NEXUS_MODULE_DEPLOY_SCRIPT_NAME).'
    }

    def selectedModules = selModules.findAll { it }
    echo "CD deploy: module order resolver=${CdDeployModuleOrder.IMPLEMENTATION_ID}"
    def orderedIds
    try {
        orderedIds = CdDeployModuleOrder.sortForDeploy(selectedModules, moduleRegistry)
    } catch (IllegalStateException e) {
        error "CD deployment plan: ${e.message}"
    }
    if (!(orderedIds instanceof List)) {
        error "CD deployment ordering failed (Jenkins CPS / shared-library mismatch): expected List<String>, got ${orderedIds?.getClass()?.name}. Publish src/org/customerxp/cd/CdDeployModuleOrder.groovy + vars-cd/executeDeployPipeline.groovy to kl-pipelines on develop and reload the shared library."
    }
    cdDebug("CD deploy: module order → ${orderedIds.join(' -> ')}")

    def deploymentPlan = CdDeploymentPlan.buildSteps(
        orderedIds,
        moduleVersionMap,
        versionChoicesStr,
        baseEnvTemplate,
        ccSetupScriptUrl,
        moduleDeployScriptUrl,
        moduleRegistry,
        ccPort?.toString()?.trim() ?: ''
    )

    if (deploymentPlan.isEmpty()) {
        error 'No modules resolved for deployment after processing MODULE_VERSION_CHOICES.'
    }

    def moduleDeployList = deploymentPlan.collect { it.modules?.toString()?.trim() }.findAll { it }
    echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
    echo "CD deploy: ${moduleDeployList.size()} module(s) in order → ${moduleDeployList.join(' → ')}"
    echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'

    def deployCtx = [
        params                  : params,
        env                     : env,
        repoRoot                : repoRoot,
        selModules              : selModules,
        configPath              : configPath,
        moduleRegistry          : moduleRegistry,
        registryPerModuleDbIds  : registryPerModuleDbIds,
        perModuleDbSelections : perModuleDbSelections,
        effectiveDepBase        : effectiveDepBase,
        dbConfigPath            : dbConfigPath,
        prepareCDEnvFileScript  : prepareCDEnvFileScript,
        runDeployOnAppServerScript: runDeployOnAppServerScript,
        getDbConfigScript       : getDbConfigScript,
    ]

    def failedModules = [] as List<String>

    deploymentPlan.eachWithIndex { step, planIdx ->
        def mod = step.modules?.toString()?.trim() ?: ''
        def stageName = "Deploy ${mod}"
        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
            stage(stageName) {
                echo "CD deploy: starting module '${mod}' (${planIdx + 1}/${deploymentPlan.size()}, ${step.deployKind}, ${step.name})"
                try {
                    deployCdPlanStep(step, planIdx, deployCtx)
                    echo "CD deploy: module '${mod}' completed successfully."
                } catch (Throwable t) {
                    failedModules << mod
                    echo "CD deploy: module '${mod}' FAILED — ${t.class.simpleName}: ${t.message}"
                    throw t
                }
            }
        }
    }

    if (!failedModules.isEmpty()) {
        currentBuild.result = 'FAILURE'
        error "CD deploy: failed module(s): ${failedModules.join(', ')}. Remaining modules were still attempted; see per-module Deploy stages."
    }
    echo "CD deploy: all ${moduleDeployList.size()} module(s) succeeded."
}

/** Runs SSH deploy for one plan step (invoked inside a per-module Jenkins stage). */
private void deployCdPlanStep(Map step, int planIdx, Map deployCtx) {
    def params = deployCtx.params
    def env = deployCtx.env
    def selModules = deployCtx.selModules
    def configPath = deployCtx.configPath
    def moduleRegistry = deployCtx.moduleRegistry
    def registryPerModuleDbIds = deployCtx.registryPerModuleDbIds
    def perModuleDbSelections = deployCtx.perModuleDbSelections
    def effectiveDepBase = deployCtx.effectiveDepBase
    def repoRoot = deployCtx.repoRoot
    def dbConfigPath = deployCtx.dbConfigPath
    def prepareCDEnvFileScript = deployCtx.prepareCDEnvFileScript
    def runDeployOnAppServerScript = deployCtx.runDeployOnAppServerScript
    def getDbConfigScript = deployCtx.getDbConfigScript

    def mod = step.modules?.toString()?.trim() ?: ''
    echo "Deploy Step ${planIdx + 1}: ${step.name} -> ${mod}"
    def appSel = resolveAppServerForDeployStep(mod, selModules, params, configPath, registryPerModuleDbIds, env)
    def dbCfg = resolveDbConfigForModule(mod, selModules, params, repoRoot, dbConfigPath, getDbConfigScript, moduleRegistry, perModuleDbSelections, registryPerModuleDbIds, effectiveDepBase)
    if (!dbCfg.credential_id?.toString()?.trim() && !dbCfg.DB_PASSWORD?.toString()?.trim()) {
        error "❌ No DB password or credential for module '${mod}' (step ${step.name})."
    }

    def stepEnvVars = [:] + step.envVars
    if (CdDebug.enabled(env)) {
        stepEnvVars.JENKINS_CD_DEBUG = env.JENKINS_CD_DEBUG?.toString()?.trim() ?: 'true'
    }
    def uiModuleVersion = stepEnvVars.MODULE_VERSION?.toString()?.trim() ?: ''
    def nexusModuleVersion = CdNexusEnvUrls.nexusArtifactVersionTag(uiModuleVersion)
    if (uiModuleVersion && nexusModuleVersion != uiModuleVersion) {
        echo "CD deploy: module ${mod} — GitLab tag '${uiModuleVersion}' → Nexus artifact path '${nexusModuleVersion}' (leading v stripped for .tgz pre-download only)"
    }
    stepEnvVars.NEXUS_MODULE_VERSION = nexusModuleVersion ?: uiModuleVersion
    stepEnvVars.DB_TYPE = dbCfg.DB_TYPE
    stepEnvVars.DB_SID = dbCfg.DB_SID
    stepEnvVars.DB_IP = dbCfg.DB_IP
    stepEnvVars.DB_PORT = dbCfg.DB_PORT
    stepEnvVars.DB_USER = dbCfg.DB_USER
    stepEnvVars.APP_SERVER_NAME = params.APP_SERVER?.toString()?.trim() ?: ''
    stepEnvVars.APP_SERVER_IP = appSel.host
    stepEnvVars.APP_SERVER_USER = appSel.user
    stepEnvVars.APP_SERVER_SSH_PORT = appSel.port

    def dbCredentialId = dbCfg.credential_id?.toString()?.trim()
    def rawDbPwd = dbCfg.DB_PASSWORD?.toString() ?: ''
    def sshCredentialId = appSel.sshCredentialId?.toString()?.trim()
    if (!sshCredentialId) {
        error "APP_SERVER: server '${params.APP_SERVER}' is missing sshCredentialId (resolved in resolveAppServerSelection)."
    }

    echo "CD deploy: app server ${params.APP_SERVER} → ${appSel.user}@${appSel.host}:${appSel.port} sshCredentialId=${sshCredentialId} remoteScriptDir=${appSel.remoteScriptPath} (${appSel.remoteScriptPathSource})"

    def runDeployWithSecrets = { String dbPwd, String sshPwd ->
        stepEnvVars.DB_PASSWORD = dbPwd
        def envFilePath = prepareCDEnvFileScript.call(
            envVars: stepEnvVars,
            fileName: "automation_variables_${step.name}.env"
        )
        runDeployOnAppServerScript.call(
            appServerIp: appSel.host,
            appServerUser: appSel.user,
            appServerSshPort: appSel.port,
            appServerPassword: sshPwd,
            envFilePath: envFilePath,
            scriptNexusUrl: step.scriptUrl,
            deployKind: findRegistryModule(moduleRegistry, mod)?.deployKind?.toString(),
            remotePath: appSel.remoteScriptPath,
            remoteScriptPathSource: appSel.remoteScriptPathSource
        )
    }

    def runDeployWithSsh = { String dbPwd ->
        withCredentials([string(credentialsId: sshCredentialId, variable: 'APP_SSH_PWD')]) {
            runDeployWithSecrets(dbPwd, env.APP_SSH_PWD)
        }
    }

    if (dbCredentialId) {
        withCredentials([string(credentialsId: dbCredentialId, variable: 'DB_PWD')]) {
            runDeployWithSsh(env.DB_PWD)
        }
    } else {
        runDeployWithSsh(rawDbPwd)
    }
}

/** True when every selected module uses {@link CdRegistryDbRole#SHARED_DB} (DB from cc_db_context, not job DB_SERVER). */
private static boolean selectedModulesAreAllSharedDb(Map moduleRegistry, List<String> moduleIds) {
    def ids = moduleIds?.findAll { it?.toString()?.trim() } ?: []
    if (ids.isEmpty()) {
        return false
    }
    ids.every { CdRegistryDbRole.dbRoleForModule(moduleRegistry, it) == CdRegistryDbRole.SHARED_DB }
}

/** Legacy global DB job fields: skip persist when UI placeholder or empty (not used for SHARED_DB-only deploys). */
private static boolean isSkippableLegacyGlobalDbPersistInput(String setupId, String credV) {
    def c = credV?.toString()?.trim() ?: ''
    if (!c || c == 'SHARED_MODULE_NO_INPUT' || c.startsWith('[HIDDEN]')) {
        return true
    }
    if (CdPipelineUiJson.isPlaceholderAppOrDbChoice(setupId?.toString()?.trim())) {
        return true
    }
    return false
}

/** Application host: single **APP_SERVER** for all modules. */
def resolveAppServerForDeployStep(String mod, List selModules, def params, String configPath, Set registryPerModuleDbIds, def pipelineEnv) {
    if ((mod in registryPerModuleDbIds) && selModules.contains(mod)) {
        def choice = params.APP_SERVER?.toString()?.trim()
        if (CdPipelineUiJson.isPlaceholderAppOrDbChoice(choice)) {
            error "APP_SERVER is required for module ${mod}."
        }
        return resolveAppServerSelection(configPath, choice, pipelineEnv)
    }
    def g = params.APP_SERVER?.toString()?.trim()
    if (CdPipelineUiJson.isPlaceholderAppOrDbChoice(g)) {
        error 'APP_SERVER is required for shared modules (deploy where CC runs).'
    }
    return resolveAppServerSelection(configPath, g, pipelineEnv)
}

def resolveDbConfigForModule(String mod, List selModules, def params, String repoRoot, String dbConfigPath, def getDbConfigScript, Map moduleRegistry, Map perModuleDbSelections = [:], Set registryPerModuleDbIds = null, String depBasePathForSharedDb = '') {
    def perModuleDbIds = registryPerModuleDbIds != null ? registryPerModuleDbIds : perModuleDbModuleIds(moduleRegistry)
    if ((mod in perModuleDbIds) && selModules.contains(mod)) {
        def moduleDb = resolvePerModuleDbSelection(perModuleDbSelections, mod, params)
        def oServer = moduleDb.setupId
        def oCreds = moduleDb.credentialsJson
        def entry = findRegistryModule(moduleRegistry, mod)
        def mtype = CdRegistryDbRole.normalizeDbRole(entry?.dbRole)
        if (!(mtype in [CdRegistryDbRole.PRIMARY_DB, CdRegistryDbRole.CUSTOM_DB])) {
            mtype = CdRegistryDbRole.CUSTOM_DB
        }
        return getDbConfigScript.call(
            moduleType: mtype,
            params: params,
            paramOverrides: [DB_SERVER: oServer, DB_CREDENTIALS_INPUT: oCreds],
            repoRoot: repoRoot,
            setupConfigsFile: dbConfigPath
        )
    }
    return getDbConfigScript.call(
        moduleType: CdRegistryDbRole.SHARED_DB,
        params: params,
        repoRoot: repoRoot,
        setupConfigsFile: dbConfigPath,
        depBasePathForSharedDb: depBasePathForSharedDb?.toString()?.trim() ?: ''
    )
}

def resolveAppServerSelection(String configPath, def appServerParam, def pipelineEnv) {
    def selectedServer = appServerParam?.toString()?.trim()
    if (!selectedServer) {
        error 'APP_SERVER: Select a valid application server.'
    }

    if (!configPath?.trim()) {
        error 'APP_SERVER: Configure JENKINS_CD_SERVERS_CONFIG_FILE with sshCredentialId (Jenkins Secret text) per server.'
    }

    try {
        def configText = readFile(configPath)
        def cfg = jsonTreeToSerializable(new JsonSlurper().parseText(configText)) as Map
        def entry = cfg.servers?.find { it.name?.toString()?.trim() == selectedServer }
        if (!entry) {
            error "APP_SERVER: Server '${selectedServer}' not found in ${configPath}"
        }

        def appHost = entry.host?.toString()?.trim()
        def appUser = entry.user?.toString()?.trim()
        if (!appUser) {
            error "APP_SERVER: server '${selectedServer}' is missing or empty field 'user' in ${configPath}."
        }
        def appPort = entry.sshPort?.toString()?.trim()
        if (!appPort) {
            error "APP_SERVER: server '${selectedServer}' is missing or empty field 'sshPort' in ${configPath}."
        }
        def sshCredentialId = entry.sshCredentialId?.toString()?.trim() ?: ''

        def deprecatedSshFields = []
        if (entry.sshPasswordCredentialId?.toString()?.trim()) {
            deprecatedSshFields << 'sshPasswordCredentialId'
        }
        if (entry.SSH_CREDENTIAL_ID?.toString()?.trim()) {
            deprecatedSshFields << 'SSH_CREDENTIAL_ID'
        }
        if (entry.password?.toString()?.trim()) {
            deprecatedSshFields << 'password'
        }
        if (entry.sshPassword?.toString()?.trim()) {
            deprecatedSshFields << 'sshPassword'
        }
        if (entry.appPassword?.toString()?.trim()) {
            deprecatedSshFields << 'appPassword'
        }
        if (entry.APP_SERVER_PASSWORD?.toString()?.trim()) {
            deprecatedSshFields << 'APP_SERVER_PASSWORD'
        }

        if (!appHost) {
            error "APP_SERVER: Server '${selectedServer}' is missing host in ${configPath}"
        }
        if (!sshCredentialId) {
            error "APP_SERVER: Server '${selectedServer}' needs sshCredentialId (Jenkins **Secret text** credential id) in ${configPath}."
        }
        if (!deprecatedSshFields.isEmpty()) {
            error "APP_SERVER: Server '${selectedServer}' uses deprecated SSH fields in ${configPath}: ${deprecatedSshFields.join(', ')}. Use sshCredentialId only; remove plaintext passwords from the file."
        }

        Map<String, String> remoteResolved
        try {
            // Plain Map for CPS: workflow env is EnvActionImpl, not java.util.Map (see CdRemoteScriptPath.readEnv).
            def pathEnv = [
                JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE: pipelineEnv?.JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE?.toString()?.trim() ?: '',
            ]
            remoteResolved = CdRemoteScriptPath.resolve(entry as Map, pathEnv)
        } catch (IllegalArgumentException e) {
            error "APP_SERVER '${selectedServer}': ${e.message}"
        }

        return [
            host: appHost,
            user: appUser,
            port: appPort,
            sshCredentialId: sshCredentialId,
            remoteScriptPath: remoteResolved.path,
            remoteScriptPathSource: remoteResolved.source
        ]
    } catch (hudson.AbortException e) {
        throw e
    } catch (Exception e) {
        error "APP_SERVER: Failed to resolve '${selectedServer}' from ${configPath}: ${e.message}"
    }
}

/**
 * Module ids whose registry {@code dbRole} uses per-module DB flows (see {@link CdRegistryDbRole}).
 * P3-1 / P3-2: delegated to {@link CdRegistryDbRole#moduleIdsWithPerModuleDbAsSet}.
 */
def perModuleDbModuleIds(Map moduleRegistry) {
    CdRegistryDbRole.moduleIdsWithPerModuleDbAsSet(moduleRegistry)
}

def findRegistryModule(Map moduleRegistry, String id) {
    CdRegistryDbRole.findModuleEntry(moduleRegistry, id)
}

/** Normalize MODULE_VERSION_CHOICES from plugin (may be List or comma-separated string). */
def normalizeVersionChoicesParam(raw) {
    if (raw == null) return ''
    def s
    if (raw instanceof List) {
        s = raw.collect { it?.toString()?.trim() }.findAll { it }.join(',')
    } else {
        s = raw.toString().trim()
        if (s.startsWith('[') && s.endsWith(']')) {
            s = s.substring(1, s.length() - 1).trim()
        }
    }
    // Trim trailing/leading commas (plugin sometimes sends "cc:v0.0.1,")
    while (s.endsWith(',')) s = s.substring(0, s.length() - 1).trim()
    while (s.startsWith(',')) s = s.substring(1).trim()
    return s
}

def parsePerModuleDbSelections(raw) {
    if (raw instanceof Map) {
        def out = new LinkedHashMap()
        raw.each { k, v ->
            def key = k?.toString()?.trim()?.toLowerCase()
            if (!key) {
                return
            }
            if (v instanceof Map) {
                out[key] = new LinkedHashMap(v as Map)
            }
        }
        return out
    }
    def normalized = CdPipelineUiJson.normalizeStructuredParam(raw)
    if (!normalized) {
        return [:]
    }
    try {
        def parsed = jsonTreeToSerializable(new JsonSlurper().parseText(normalized))
        return (parsed instanceof Map) ? parsed : [:]
    } catch (Exception e) {
        echo "WARN: PER_MODULE_DB_JSON_SUBMITTED is not valid JSON after normalization: ${e.message}"
        return [:]
    }
}

def resolvePerModuleDbSelection(Map perModuleDbSelections, String mod, def params) {
    def key = mod?.toString()?.trim()?.toLowerCase()
    def entry = key ? perModuleDbSelections[key] : null
    if (entry instanceof Map) {
        def setupId = entry.setupId?.toString()?.trim() ?: entry.dbServer?.toString()?.trim() ?: entry.DB_SERVER?.toString()?.trim() ?: ''
        def creds = entry.credentials
        def credJson = ''
        if (creds instanceof Map) {
            credJson = JsonOutput.toJson(creds)
        } else if (entry.DB_CREDENTIALS_INPUT != null) {
            credJson = entry.DB_CREDENTIALS_INPUT.toString().trim()
        } else if (entry.credentialsJson != null) {
            credJson = entry.credentialsJson.toString().trim()
        }
        if (setupId || credJson) {
            return [setupId: setupId, credentialsJson: credJson]
        }
    }

    return [setupId: '', credentialsJson: '']
}

/** True when **PER_MODULE_DB_JSON_SUBMITTED** has no usable per-module DB JSON payload yet. */
boolean isPerModuleDbParamEmpty(def raw) {
    if (raw == null) {
        return true
    }
    if (raw instanceof Map) {
        return raw.isEmpty()
    }
    def t = raw.toString().trim()
    return !t || t == '{}' || t.equalsIgnoreCase('null')
}

/** Mask JSON string values for pwd/password keys (best-effort for logs). */
String maskPerModuleDbSecretsForLog(String s) {
    if (s == null || !s.toString().trim()) {
        return '(empty)'
    }
    def t = s.toString()
    t = t.replaceAll(/"pwd"\s*:\s*"[^"]*"/, '"pwd":"***"')
    t = t.replaceAll(/"password"\s*:\s*"[^"]*"/, '"password":"***"')
    return t
}

void logPerModuleDbIngest(def envBinding, def raw, String rawSource, Map parsed, List registryPerModuleIds, List selectedNeedingPmdb) {
    if (!CdDebug.enabled(envBinding)) {
        return
    }
    def rawClass = raw == null ? 'null' : raw.getClass().simpleName
    def rawLen = (raw instanceof CharSequence) ? raw.toString().length() : 'n/a'
    cdDebug(message: "Per-module DB JSON ingest source=${rawSource} rawClass=${rawClass} rawLen=${rawLen}")
    def preview
    if (raw instanceof Map) {
        preview = maskPerModuleDbSecretsForLog(JsonOutput.toJson(raw))
    } else {
        def s = raw?.toString() ?: ''
        preview = maskPerModuleDbSecretsForLog(s.length() > 800 ? s.substring(0, 800) + '…(truncated)' : s)
    }
    cdDebug(message: "Per-module DB JSON masked preview: ${preview}")
    cdDebug(message: "Per-module DB JSON registry PRIMARY/CUSTOM ids: ${registryPerModuleIds.sort().join(', ')}")
    cdDebug(message: "Per-module DB JSON selected needing per-row DB: ${selectedNeedingPmdb.join(', ')}")
    cdDebug(message: "Per-module DB JSON parsed map keys: ${(parsed?.keySet() ?: []).sort().join(', ') ?: '(none)'}")
    parsed?.each { k, v ->
        if (v instanceof Map) {
            def sid = (v.setupId ?: v.dbServer ?: v.DB_SERVER)?.toString()?.trim() ?: ''
            def credObj = v.credentials instanceof Map ? v.credentials : null
            def credType = credObj?.type?.toString() ?: '(n/a)'
            def credUser = credObj?.user?.toString() ?: ''
            def credLen = credObj ? JsonOutput.toJson(credObj).length() : 0
            cdDebug(message: "  [${k}] setupId='${sid}' cred.type=${credType} cred.user.len=${credUser.length()} cred.mapJson.len=${credLen}")
        }
    }
}

void echoPerModuleDbValidationFailure(String mod, String setupId, String credJson, Map perMap, def rawParam, String rawSource) {
    echo '━━━ Per-module DB JSON diagnostics (failure; no passwords printed) ━━━'
    echo "  module=${mod}  rawSource=${rawSource}"
    echo "  resolved setupId='${setupId}'  treatAsUnselected=${CdPipelineUiJson.isPlaceholderAppOrDbChoice(setupId)}"
    echo "  credentialsJson present=${credJson as boolean}  length=${credJson?.length() ?: 0}"
    echo "  parsed JSON keys: ${perMap?.keySet()?.sort()?.join(', ') ?: '(empty map)'}"
    def rc = rawParam == null ? 'null' : rawParam.getClass().simpleName
    def rl = (rawParam instanceof CharSequence) ? rawParam.toString().length() : 'n/a'
    echo "  raw parameter class=${rc}  stringLength=${rl}"
    echo '  hint: Deploy reads **PER_MODULE_DB_JSON_SUBMITTED** only; copy from **PER_MODULE_DB_JSON** preview + Copy button.'
    echo '  tip: set JENKINS_CD_DEBUG=true for masked JSON preview.'
}
