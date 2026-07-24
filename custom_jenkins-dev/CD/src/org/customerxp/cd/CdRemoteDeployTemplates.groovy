package org.customerxp.cd

/**
 * P3-5 / P3-6 / P4-5: Remote bash for {@code runDeployOnAppServer}. Dispatch is by registry {@link CdDeployKinds},
 * not by comparing Nexus script filenames — new kinds extend {@link #buildRemoteScriptExecution} plus a {@code *RemoteBlock} helper.
 * The basename of the script comes from the Nexus URL Jenkins resolved (env-driven names via {@code JENKINS_NEXUS_*_SCRIPT_NAME});
 * Remote blocks use an absolute script path under {@code remotePath} (from {@code CdRemoteScriptPath}:
 * {@code JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE} or per-server {@code remoteScriptPath}).
 * The shell interpreter defaults to {@code /bin/bash}; override via {@code bashExecutable} /
 * {@code JENKINS_CD_REMOTE_BASH} (full path avoids {@code bash} aliases after sourcing {@code ~/.bashrc}).
 * Escaping matches legacy Jenkins ssh/sshpass quoting.
 * Remote bash snippets are assembled from {@code '...'} string literals (often {@code List.join('\\n')}), not
 * {@code """..."""} GStrings: Jenkins workflow CPS Groovy rejects many {@code $} / escape sequences inside GStrings
 * even when plain Groovy accepts them.
 */
@SuppressWarnings(['unused'])
final class CdRemoteDeployTemplates {

    private static final String DEFAULT_REMOTE_BASH = '/bin/bash'

    /**
     * Absolute path to GNU bash on the app server — conservative allow-list (no spaces/metacharacters).
     */
    static String normalizeRemoteBashExe(String bashExecutable) {
        def raw = bashExecutable?.toString()?.trim()
        if (!raw) {
            return DEFAULT_REMOTE_BASH
        }
        // Letters, digits, underscore, dot, forward slash, hyphen — typical Unix paths only.
        if (!(raw ==~ /^[a-zA-Z0-9_.\\/+-]+$/)) {
            return DEFAULT_REMOTE_BASH
        }
        return raw
    }

    /**
     * @param deployKind from registry (preferred); when blank, {@link #inferDeployKindFromFilename} is used for legacy callers.
     * @param bashExecutable optional absolute path to bash on app servers (default {@code /bin/bash}); Jenkins passes {@code JENKINS_CD_REMOTE_BASH} via {@code runDeployOnAppServer}.
     * <p>No {@code Map} of {@code Closure}s: Jenkins CPS can mis-resolve static init closures vs private static methods
     * (pipeline log {@code CPS-method-mismatches} / {@code ccSetupRemoteBlock}).
     */
    static String buildRemoteScriptExecution(String remotePath, String scriptFileName, String deployKind = null, String bashExecutable = null) {
        def rp = remotePath?.toString()?.trim() ?: ''
        def bx = normalizeRemoteBashExe(bashExecutable)
        def sfRaw = scriptFileName?.toString()?.trim()
        def dk = normalizeDeployKind(deployKind)
        if (!dk && sfRaw) {
            dk = inferDeployKindFromFilename(sfRaw)
        }
        def sf = sfRaw ?: defaultBasenameForDeployKind(dk)
        if (!dk && sf) {
            dk = inferDeployKindFromFilename(sf)
        }
        if (dk == CdDeployKinds.CC_SETUP) {
            return ccSetupRemoteBlock(rp, sf, bx)
        }
        if (dk == CdDeployKinds.MODULE_DEPLOY) {
            return moduleDeployRemoteBlock(rp, sf, bx)
        }
        return defaultRemoteBlock(rp, sf, bx)
    }

    private static String normalizeDeployKind(String deployKind) {
        def s = deployKind?.toString()?.trim()?.toLowerCase()
        return s ?: null
    }

    /** Fallback when deployKind is not passed (e.g. ad-hoc jobs). */
    private static String inferDeployKindFromFilename(String scriptFileName) {
        def n = scriptFileName?.toString()?.trim()?.toLowerCase()
        if (!n) {
            return null
        }
        def ccToken = CdDeployKinds.CC_SETUP
        def mdToken = CdDeployKinds.MODULE_DEPLOY
        if (n.endsWith("${ccToken}.sh") || n.contains(ccToken)) {
            return CdDeployKinds.CC_SETUP
        }
        if (n.endsWith("${mdToken}.sh") || n.contains(mdToken)) {
            return CdDeployKinds.MODULE_DEPLOY
        }
        return null
    }

    /** When basename is missing: align with registry {@code deployKind} so CC steps do not pick module_deploy default. */
    private static String defaultBasenameForDeployKind(String dk) {
        if (dk == CdDeployKinds.CC_SETUP) {
            return "${CdDeployKinds.CC_SETUP}.sh"
        }
        return "${CdDeployKinds.MODULE_DEPLOY}.sh"
    }

    private static String defaultRemoteBlock(String remotePath, String scriptFileName, String bashExe) {
        [
            'set +x',
            '                set -e',
            '                cd ' + remotePath + ' && ' + bashExe + ' ' + remotePath + '/' + scriptFileName,
        ].join('\n')
    }

    /**
     * @param scriptFileName basename from Nexus URL (matches Jenkins env script name); fallback {@code cc_setup.sh}
     */
    private static String ccSetupRemoteBlock(String remotePath, String scriptFileName, String bashExe) {
        def sf = scriptFileName?.toString()?.trim() ?: (CdDeployKinds.CC_SETUP + '.sh')
        def invoke = '                ' + bashExe + ' ./' + sf + ' \\${MODULE_NAME} \\${NEXUS_MODULE_VERSION} \\${JDK_VERSION}'
        [
            'cd ' + remotePath,
            '                set +x',
            '                set -e',
            '                # No double-quote characters in this block — pasted inside runDeployOnAppServer ssh on the Jenkins agent.',
            '                : \\${CC_PORT?ERROR_CC_PORT_required_for_cc_deployment}',
            '                : \\${DB_PASSWORD?ERROR_DB_PASSWORD_required_for_cc_deployment}',
            '                : \\${NEXUS_MODULE_VERSION?ERROR_NEXUS_MODULE_VERSION_required_for_cc_dot_config_VERSION}',
            '                test -r ./' + sf + ' || { echo ERROR_deploy_script_missing_after_wget; ls -la .; exit 1; }',
            '                # Minimal .config: underscore-prefixed keys only (matches hand-maintained cc_setup / deploy_cc contract).',
            '                cat > .config <<EOF',
            '_VERSION=\\${JDK_VERSION}-\\${NEXUS_MODULE_VERSION}',
            '_DEP_BASE=\\${DEP_BASE_PATH}',
            '_CERTS=\\$HOME/certs',
            '_RANDOM_PORT=\\${CC_PORT}',
            '_DB_TYPE=\\${DB_TYPE}',
            '_db_sid=\\${DB_SID}',
            '_db_ip=\\${DB_IP}',
            '_db_user=\\${DB_USER}',
            '_db_port=\\${DB_PORT}',
            '_root_password=\\${DB_PASSWORD}',
            '_jks_password=cxps123',
            '_2fa_enable=false',
            '_2fa_otp_gen_url=',
            '_2fa_otp_ver_url=',
            '_2fa_client_id=',
            '_2fa_client_secret=',
            'EOF',
            '                # cc_install reads _DB_TYPE (case-sensitive). Normalize stray _db_type= lines without editing Nexus scripts.',
            '                sed -i \'s/^_db_type=/_DB_TYPE=/\' .config 2>/dev/null || true',
            '                # Ensure subprocess sees _DB_TYPE even if .config from another layer only defines _db_type.',
            '                export _DB_TYPE=\\${DB_TYPE}',
            '                case \\${JENKINS_CD_DEBUG:-} in',
            '                  1|true|TRUE|yes|YES|y|Y|on|ON)',
            '                  echo \'========= CD DEBUG: .config (after write; _root_password / _jks_password masked) ==========\'',
            '                  sed -e \'s/^_root_password=.*/_root_password=***MASKED***/\' -e \'s/^_jks_password=.*/_jks_password=***MASKED***/\' .config 2>/dev/null || echo \'CD DEBUG: .config missing or unreadable\'',
            '                  echo \'========= end CD DEBUG .config ==========\'',
            '                  ;;',
            '                esac',
            '                # Single-quoted message only — double-quotes break Jenkins ssh wrapping on the agent.',
            '                echo \'CD pipeline: CC listen port in .config as _RANDOM_PORT; exported as RANDOM_PORT / CC_*_PORT:\' \\${CC_PORT}',
            '                # Installers / expect often read RANDOM_PORT from the environment; keep exports even though .config is minimal.',
            '                # No double-quotes here — Jenkins wraps this block in ssh on the agent.',
            '                export RANDOM_PORT=\\${CC_PORT}',
            '                export CC_APP_PORT=\\${CC_PORT}',
            '                export CC_LISTEN_PORT=\\${CC_PORT}',
            '                export CC_HTTP_PORT=\\${CC_PORT}',
            '                # Invoke cc_setup.sh with argv: arg2 is NEXUS_MODULE_VERSION (no leading v) so deploy_cc installer paths match Nexus (see module_deploy).',
            invoke,
        ].join('\n')
    }

    private static String moduleDeployRemoteBlock(String remotePath, String scriptFileName, String bashExe) {
        // This string is pasted inside runDeployOnAppServer's: ssh user@host " ... "
        // Any raw ASCII 34 (") here closes the agent's ssh argument early → corrupt script / unexpected EOF / mismatched }.
        // Use no double-quotes in the emitted remote shell. Lines use \\${VAR} so the Jenkins agent ssh "..." does not expand
        // DB_TYPE etc. before the remote session; values expand on the app server after source ~/automation_variables.env.
        def invoke = '                ' + bashExe + ' ./' + scriptFileName +
            ' \\${MODULE_NAME} \\${NEXUS_MODULE_VERSION} \\${JDK_VERSION} \\$INSTANCES'
        [
            'cd ' + remotePath,
            '                set +x',
            '                set -e',
            '                : \\${MODULE_NAME:?MODULE_NAME_required}',
            '                : \\${MODULE_VERSION:?MODULE_VERSION_required}',
            '                : \\${JDK_VERSION:?JDK_VERSION_required}',
            '                : \\${NEXUS_PACKAGE_BASE_URL:?NEXUS_PACKAGE_BASE_URL_required}',
            '                NEXUS_BASE=\\$NEXUS_PACKAGE_BASE_URL',
            '                : \\${NEXUS_MODULE_VERSION:?NEXUS_MODULE_VERSION_required_for_module_tgz_pre_download}',
            '                MODULE=\\${MODULE_NAME}-\\${JDK_VERSION}-\\${NEXUS_MODULE_VERSION}',
            '                TGZ_URL=\\$NEXUS_BASE/\\${MODULE_NAME}/\\${JDK_VERSION}-\\${NEXUS_MODULE_VERSION}/\\${MODULE}.tgz',
            '                TGZ_HOME=\\$HOME/\\$MODULE.tgz',
            '                # Single-quoted message: parentheses in an unquoted echo break Jenkins ssh wrapping on the agent.',
            '                echo \'Pre-downloading module tgz — UI tag=\'\\${MODULE_VERSION}\' Nexus path=\'\\${NEXUS_MODULE_VERSION}\': \'\\$TGZ_URL\' to \'\\$TGZ_HOME',
            '                if [ -n \\${NEXUS_PACKAGE_USER:-} ]; then wget -q --timeout=120 --tries=3 --user=\\$NEXUS_PACKAGE_USER --password=\\$NEXUS_PACKAGE_PASSWORD -O \\$TGZ_HOME \\$TGZ_URL; else wget -q --timeout=120 --tries=3 -O \\$TGZ_HOME \\$TGZ_URL; fi || { echo wget_failed; exit 1; }',
            '                test -s \\$TGZ_HOME || { echo downloaded_tgz_missing_or_empty; exit 1; }',
            '                cp -f \\$TGZ_HOME ' + remotePath + '/\\$MODULE.tgz 2>/dev/null || true',
            '                ls -la \\$TGZ_HOME ' + remotePath + '/\\$MODULE.tgz 2>/dev/null || ls -la \\$TGZ_HOME',
            '                cat > var.conf <<VARCONF',
            '# Configuration file for module deploy script (generated by CD pipeline)',
            'DEP_BASE_PATH=\\${DEP_BASE_PATH}',
            'dep_base_path=\\${DEP_BASE_PATH}',
            'db_creds=\\${DB_USER}:\\${DB_PASSWORD}',
            'app_server_name=\\${APP_SERVER_NAME}',
            'app_server_ip=\\${APP_SERVER_IP}',
            'app_server_user=\\${APP_SERVER_USER}',
            'app_server_ssh_port=\\${APP_SERVER_SSH_PORT}',
            'DB_TYPE=\\${DB_TYPE}',
            'DB_SID=\\${DB_SID}',
            'DB_IP=\\${DB_IP}',
            'DB_PORT=\\${DB_PORT}',
            'DB_USER=\\${DB_USER}',
            'DB_PASSWORD=\\${DB_PASSWORD}',
            'VARCONF',
            '                case \\${JENKINS_CD_DEBUG:-} in',
            '                  1|true|TRUE|yes|YES|y|Y|on|ON)',
            '                  echo \'========= CD DEBUG: var.conf (after write; DB_PASSWORD / db_creds masked) ==========\'',
            '                  sed -e \'s/^DB_PASSWORD=.*/DB_PASSWORD=***MASKED***/\' -e \'s/^db_creds=.*/db_creds=***MASKED***/\' var.conf 2>/dev/null || echo \'CD DEBUG: var.conf missing or unreadable\'',
            '                  echo \'========= end CD DEBUG var.conf ==========\'',
            '                  ;;',
            '                esac',
            '                : \\${MODULE_INSTANCE_COUNT:?MODULE_INSTANCE_COUNT_required}',
            '                INSTANCES=\\${MODULE_INSTANCE_COUNT}',
            invoke,
        ].join('\n')
    }
}
