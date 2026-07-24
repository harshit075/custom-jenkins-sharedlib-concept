package org.customerxp.cd

import spock.lang.Specification

/**
 * Ensures CC remote block emits a minimal {@code .config} (underscore keys only)
 * while still exporting listen-port env vars for installers / expect.
 */
class CdRemoteDeployTemplatesSpec extends Specification {

    /** Placeholder remote dir — production uses {@code JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE}, never hardcoded in templates. */
    private static final String REMOTE_SCRIPT_DIR_FIXTURE = '/opt/example-cd-remote-scripts'

    def 'normalizeRemoteBashExe falls back for blank or unsafe paths'() {
        expect:
        CdRemoteDeployTemplates.normalizeRemoteBashExe(null) == '/bin/bash'
        CdRemoteDeployTemplates.normalizeRemoteBashExe('') == '/bin/bash'
        CdRemoteDeployTemplates.normalizeRemoteBashExe('  ') == '/bin/bash'
        CdRemoteDeployTemplates.normalizeRemoteBashExe('/tmp/evil bash') == '/bin/bash'
        CdRemoteDeployTemplates.normalizeRemoteBashExe('/usr/bin/bash') == '/usr/bin/bash'
    }

    def 'cc_setup remote block: minimal .config heredoc; exports RANDOM_PORT'() {
        when:
        String block = CdRemoteDeployTemplates.buildRemoteScriptExecution(
            REMOTE_SCRIPT_DIR_FIXTURE,
            'cc_setup.sh',
            CdDeployKinds.CC_SETUP,
            '/bin/bash'
        )

        then:
        block.contains('cat > .config <<EOF')
        block.contains('_VERSION=\\${JDK_VERSION}-\\${NEXUS_MODULE_VERSION}')
        block.contains('_RANDOM_PORT=')
        block.contains('_DB_TYPE=')
        block.contains('sed -i \'s/^_db_type=/_DB_TYPE=/\' .config')
        block.contains('export _DB_TYPE=')
        block.contains('ERROR_NEXUS_MODULE_VERSION_required_for_cc_dot_config_VERSION')
        block.contains('_DEP_BASE=\\${DEP_BASE_PATH}')
        !block.contains('_DEP_BASE=\\"')
        block.contains('export RANDOM_PORT=')
        block.contains('export CC_APP_PORT=')
        block.contains('/bin/bash ./cc_setup.sh \\${MODULE_NAME} \\${NEXUS_MODULE_VERSION} \\${JDK_VERSION}')
        block.contains('test -r ./cc_setup.sh')
        block.contains('CD DEBUG: .config')
        block.contains('***MASKED***')

        and: 'no duplicate / audit keys in .config heredoc body'
        def m = block =~ /cat > \.config <<EOF\n([\s\S]*?)\nEOF\n/
        m.find()
        String heredocBody = m.group(1)
        !heredocBody.contains('RANDOM_PORT=')
        !heredocBody.contains('dep_base_path')
        !heredocBody.contains('random_port')
        !heredocBody.contains('db_creds')
        !heredocBody.contains('app_server')
    }

    def 'module_deploy remote block: var.conf expands DB vars on app server not Jenkins agent'() {
        when:
        String block = CdRemoteDeployTemplates.buildRemoteScriptExecution(
            REMOTE_SCRIPT_DIR_FIXTURE,
            'module_deploy.sh',
            CdDeployKinds.MODULE_DEPLOY,
            '/usr/bin/bash'
        )

        then:
        block.contains('cat > var.conf <<VARCONF')
        block.contains('DB_TYPE=\\${DB_TYPE}')
        block.contains('DEP_BASE_PATH=\\${DEP_BASE_PATH}')
        block.contains('NEXUS_MODULE_VERSION')
        block.contains('UI tag=\'\\${MODULE_VERSION}\'')
        block.contains('Nexus path=\'\\${NEXUS_MODULE_VERSION}\'')
        !block.contains('(UI tag=')
        block.contains('CD DEBUG: var.conf')
        block.contains('/usr/bin/bash ./module_deploy.sh')

        and: 'no bare $DB_TYPE in var.conf heredoc (Jenkins agent would expand before ssh)'
        def m = block =~ /cat > var\.conf <<VARCONF\n([\s\S]*?)\nVARCONF\n/
        m.find()
        String heredocBody = m.group(1)
        heredocBody.contains('DB_TYPE=\\${DB_TYPE}')
        heredocBody.contains('dep_base_path=\\${DEP_BASE_PATH}')
        !heredocBody.contains('DB_TYPE=$DB_TYPE')
        !heredocBody.contains('DB_TYPE=${')
    }
}
