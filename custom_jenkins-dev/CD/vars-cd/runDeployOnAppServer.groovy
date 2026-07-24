// vars/runDeployOnAppServer.groovy
import org.customerxp.cd.CdRemoteDeployTemplates
import org.customerxp.cd.CdNexusEnvUrls
import org.customerxp.cd.CdSharedLibVarLookup

/**
 * Runs the CD deployment on the application server via SSH.
 * Copies automation_variables.env to the server, then downloads and executes
 * the requested deployment script from Nexus.
 * <p>Before each run, Nexus shell scripts that the CD remote step relies on are removed from
 * Remote script dir from {@code executeDeployPipeline} / {@link org.customerxp.cd.CdRemoteScriptPath} ({@code deploy_cc.sh}, {@code cc_install.sh} — cached by {@code cc_setup.sh} when missing —
 * and the main deploy script basename from {@code scriptNexusUrl}) so the next {@code wget} always pulls fresh copies.
 *
 * <p>P4-5: SSH password is written to a chmod 600 workspace temp file and passed to {@code sshpass -f}
 * (not {@code withEnv}), then removed — avoids leaking {@code SSH_PASS} into environment dumps. Remote bash uses {@code set +x}.
 */
def call(Map args) {
    String appServerIp = args.appServerIp
    String appServerUser = args.appServerUser
    String appServerSshPort = args.appServerSshPort?.toString()?.trim()
    if (!appServerSshPort) {
        error 'runDeployOnAppServer: appServerSshPort is required (set sshPort on the selected server in servers JSON).'
    }
    String appServerPassword = args.appServerPassword
    String envFilePath = args.envFilePath
    String scriptNexusUrl = args.scriptNexusUrl
    /** Registry deployKind (cc_setup | module_deploy); preferred over inferring from Nexus script URL. */
    String deployKind = args.deployKind
    String moduleJsonUrl = args.moduleJsonUrl
    String remotePath = args.remotePath
    String remoteScriptPathSource = args.remoteScriptPathSource?.toString()?.trim() ?: ''

    if (!appServerIp?.trim() || !appServerUser?.trim()) {
        error 'runDeployOnAppServer: appServerIp and appServerUser are required.'
    }
    if (!appServerPassword?.trim()) {
        error 'runDeployOnAppServer: appServerPassword is required.'
    }
    if (!envFilePath?.trim()) {
        error 'runDeployOnAppServer: envFilePath is required.'
    }

    def getCDDefaultsScript = null
    try {
        getCDDefaultsScript = CdSharedLibVarLookup.resolve(this, 'getCDDefaults')
    } catch (Exception ignored) {
        echo "getCDDefaults not found in shared library, using defaults."
    }

    if (!scriptNexusUrl?.trim() && getCDDefaultsScript) {
        scriptNexusUrl = getCDDefaultsScript.getModuleDeployScriptNexusUrl()
    }
    if (!scriptNexusUrl?.trim()) {
        scriptNexusUrl = CdNexusEnvUrls.moduleDeployScriptUrl(env)
    }
    if (!scriptNexusUrl?.trim()) {
        error 'runDeployOnAppServer: set scriptNexusUrl or JENKINS_NEXUS_URL + JENKINS_NEXUS_SCRIPTS_URI_PATH + JENKINS_NEXUS_MODULE_DEPLOY_SCRIPT_NAME.'
    }

    if (!moduleJsonUrl?.trim() && getCDDefaultsScript) {
        moduleJsonUrl = getCDDefaultsScript.getModuleJsonNexusUrl()
    }
    if (!moduleJsonUrl?.trim()) {
        moduleJsonUrl = CdNexusEnvUrls.moduleJsonTarUrl(env)
    }
    if (!moduleJsonUrl?.trim()) {
        error 'runDeployOnAppServer: set moduleJsonUrl or JENKINS_NEXUS_URL + JENKINS_NEXUS_SCRIPTS_URI_PATH + JENKINS_NEXUS_MODULE_JSON_ARCHIVE_NAME.'
    }

    def sshPassword = appServerPassword.trim()
    def remoteUser = appServerUser.trim()
    def remoteHost = appServerIp.trim()
    def sshPort = appServerSshPort.trim()
    def scriptFileName = scriptNexusUrl.tokenize('/') ? scriptNexusUrl.tokenize('/')[-1] : ''
    if (!scriptFileName?.trim()) {
        error 'runDeployOnAppServer: could not derive script file name from scriptNexusUrl.'
    }
    def sshOpt = (sshPort != '22') ? "-p ${sshPort}" : ''
    def scpOpt = (sshPort != '22') ? "-P ${sshPort}" : ''

    if (!remotePath?.trim()) {
        error 'runDeployOnAppServer: remotePath is required. executeDeployPipeline must resolve it from APP_SERVER (JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE or servers[].remoteScriptPath).'
    }

    cdDebug("runDeployOnAppServer remotePath=${remotePath} source=${remoteScriptPathSource ?: 'args'}")
    echo "CD deploy: remote script directory ${remotePath}${remoteScriptPathSource ? " (source: ${remoteScriptPathSource})" : ''}"

    def wsDir = env.WORKSPACE?.toString()?.trim()
    if (!wsDir) {
        try {
            wsDir = pwd()
        } catch (Exception ignored) {
            wsDir = '.'
        }
    }

    def rand = java.util.UUID.randomUUID().toString().replaceAll('-', '').take(16)
    def bn = env.BUILD_NUMBER?.toString() ?: 'na'
    def passFile = ".cd_sshpw_${bn}_${rand}"
    writeFile file: passFile, text: sshPassword

    def moduleJsonFile = moduleJsonUrl.tokenize('/') ? moduleJsonUrl.tokenize('/')[-1] : ''
    if (!moduleJsonFile?.trim()) {
        error 'runDeployOnAppServer: could not derive archive file name from moduleJsonUrl.'
    }
    def remoteScriptExecution = CdRemoteDeployTemplates.buildRemoteScriptExecution(
        remotePath,
        scriptFileName,
        deployKind,
        env.JENKINS_CD_REMOTE_BASH?.toString()?.trim()
    )

    echo "CD deploy: wget module JSON archive → ${moduleJsonUrl}"
    echo "CD deploy: wget shell script (deployKind=${deployKind ?: '(infer)'}) → ${scriptNexusUrl}"
    echo "CD deploy: script installs as ${remotePath}/${scriptFileName}"
    cdDebug("runDeployOnAppServer scriptNexusUrl=${scriptNexusUrl} moduleJsonUrl=${moduleJsonUrl} scriptFileName=${scriptFileName}")

    try {
        sh """
            set +x
            cd '${wsDir}'
            chmod 600 '${passFile}' 2>/dev/null || true
            PF="\$(pwd)/${passFile}"
            trap 'rm -f "\$PF" 2>/dev/null || true' EXIT INT HUP

            command -v sshpass >/dev/null 2>&1 || { echo 'ERROR: sshpass is not installed on this Jenkins agent.'; exit 127; }

            echo "========= Verifying SSH connectivity (port ${sshPort}) =========="
            sshpass -f "\$PF" ssh -o StrictHostKeyChecking=no ${sshOpt} ${remoteUser}@${remoteHost} "echo '--- SSH probe (who / where) ---'; whoami; id; pwd; echo '--- end probe ---'"

            echo "========= Copying env file to app server =========="
            sshpass -f "\$PF" scp -o StrictHostKeyChecking=no ${scpOpt} '${envFilePath}' ${remoteUser}@${remoteHost}:/tmp/automation_variables.env

            echo "========= Running deployment on app server =========="
            sshpass -f "\$PF" ssh -o StrictHostKeyChecking=no ${sshOpt} ${remoteUser}@${remoteHost} "
                set +x
                set -e
                mv /tmp/automation_variables.env ~/automation_variables.env || true
                # Non-login SSH does not read ~/.bash_profile — load JAVA_HOME from server profile (JAVA_HOME is not set by Jenkins).
                [ -f ~/.bash_profile ] && . ~/.bash_profile || true
                [ -f ~/.bashrc ] && . ~/.bashrc || true
                . ~/automation_variables.env
                # JAVA_HOME must come from the app server profile — not Jenkins env (prepareCDEnvFile does not inject JAVA_HOME).
                if [ -z "\\\$JAVA_HOME" ]; then
                    echo 'ERROR: JAVA_HOME is empty after sourcing shell profiles and automation_variables.env. Set JAVA_HOME on the app server for this JDK_VERSION.'
                    exit 1
                fi
                if [ ! -x "\\\$JAVA_HOME/bin/java" ]; then
                    echo 'ERROR: JAVA_HOME/bin/java is missing or not executable. Fix the JDK on the app server.'
                    exit 1
                fi
                export JAVA_HOME=\\\$JAVA_HOME
                export PATH=\\\$JAVA_HOME/bin:\\\$PATH
                # ORACLE_HOME must come from the app server profile — not Jenkins (prepareCDEnvFile does not inject ORACLE_HOME).
                if [ -z "\\\$ORACLE_HOME" ]; then
                    echo 'ERROR: ORACLE_HOME is empty after sourcing shell profiles and automation_variables.env. Set ORACLE_HOME on the app server for Oracle client.'
                    exit 1
                fi
                if [ ! -d "\\\$ORACLE_HOME/bin" ]; then
                    echo 'ERROR: ORACLE_HOME bin directory is missing. Check ORACLE_HOME on the app server.'
                    exit 1
                fi
                export PATH=\\\$ORACLE_HOME/bin:\\\$PATH
                command -v sqlplus >/dev/null 2>&1 || { echo 'ERROR: sqlplus not found in PATH. Install Oracle SQL*Plus on the app server.'; exit 1; }
                echo MODULE_NAME:\\\$MODULE_NAME
                echo '========= Remote deploy: session context (same user as mkdir) =========='
                whoami || true
                id || true
                pwd || true
                echo REMOTE_SCRIPT_DIR=${remotePath}
                echo '========= Remote deploy: ensure script directory (mkdir -p) =========='
                mkdir -p ${remotePath} || {
                    echo 'ERROR: mkdir -p failed for REMOTE_SCRIPT_DIR above.'
                    echo 'Fix: set servers[].remoteScriptPath or JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE=/home/${user}/scripts so REMOTE_SCRIPT_DIR is writable by the SSH user for this APP_SERVER.'
                    exit 1
                }
                echo '========= CD: remove cached Nexus deploy shell scripts (fresh wget next) =========='
                rm -f ${remotePath}/deploy_cc.sh ${remotePath}/cc_install.sh ${remotePath}/${scriptFileName} 2>/dev/null || true
                echo '========= CD remote: wget module_json_tar (full URL in Jenkins log above) =========='
                wget -nv -O ${remotePath}/${moduleJsonFile} '${moduleJsonUrl}' || { echo 'ERROR: wget module_json_tar failed — check Nexus URL, TLS, and anonymous/auth access.'; exit 1; }
                cd ${remotePath} || { echo 'ERROR: cd to REMOTE_SCRIPT_DIR failed.'; exit 1; }
                tar -xf ${moduleJsonFile} || { echo 'ERROR: tar extract module_json_tar failed — corrupt or wrong archive.'; exit 1; }
                echo '========= CD remote: wget deploy script (full URL in Jenkins log above) =========='
                wget -nv -O ${remotePath}/${scriptFileName} '${scriptNexusUrl}' || { echo 'ERROR: wget deploy script failed — check Nexus URL; script must exist before bash runs.'; exit 1; }
                # Nexus scripts may have CRLF — breaks shebang and yields bash: script: No such file or directory
                tr -d '\\015' < ${remotePath}/${scriptFileName} > ${remotePath}/.cd_script_nolf || { echo 'ERROR: could not strip CR from deploy script'; exit 1; }
                mv -f ${remotePath}/.cd_script_nolf ${remotePath}/${scriptFileName}
                chmod +x ${remotePath}/${scriptFileName} || { echo 'ERROR: chmod deploy script failed.'; exit 1; }
                test -s ${remotePath}/${scriptFileName} || { echo 'ERROR: deploy script missing or empty after wget.'; ls -la ${remotePath} 2>/dev/null || true; exit 1; }
                echo '========= CD remote: deploy script head (verify shebang, no ^M) =========='
                head -n 2 ${remotePath}/${scriptFileName} | cat -A || true
                ${remoteScriptExecution}
            "
        """
    } finally {
        try {
            sh "rm -f '${wsDir}/${passFile}' 2>/dev/null || rm -f '${passFile}' 2>/dev/null || true"
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    echo "Deployment on app server completed successfully."
}

