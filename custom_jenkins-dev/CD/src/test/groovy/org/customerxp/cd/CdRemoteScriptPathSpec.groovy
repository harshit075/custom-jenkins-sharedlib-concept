package org.customerxp.cd

import spock.lang.Specification

class CdRemoteScriptPathSpec extends Specification {

    private static Map server(String user, Map extra = [:]) {
        [name: 'Test Server', host: '10.0.0.1', user: user] + extra
    }

    def 'per-server remoteScriptPath wins over template'() {
        when:
        def r = CdRemoteScriptPath.resolve(
            server('kldev', [remoteScriptPath: '/opt/cd/scripts']),
            [JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE: '/home/${user}/other']
        )

        then:
        r.path == '/opt/cd/scripts'
        r.source == 'servers.json remoteScriptPath'
    }

    def 'JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE substitutes user'() {
        when:
        def r = CdRemoteScriptPath.resolve(
            server('clari5'),
            [JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE: '/home/${user}/scripts']
        )

        then:
        r.path == '/home/clari5/scripts'
        r.source == 'JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE'
    }

    def 'fails when template env is missing'() {
        when:
        CdRemoteScriptPath.resolve(server('kldev'), [:])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE')
    }

    def 'fails when template set but server has no user'() {
        when:
        CdRemoteScriptPath.resolve(
            [name: 'NoUser', host: '10.0.0.2'],
            [JENKINS_CD_REMOTE_SCRIPT_PATH_TEMPLATE: '/home/${user}/scripts']
        )

        then:
        thrown(IllegalArgumentException)
    }

    def 'normalizePath strips trailing slashes'() {
        expect:
        CdRemoteScriptPath.normalizePath('/home/kldev/scripts/') == '/home/kldev/scripts'
    }

    def 'applyTemplate substitutes user'() {
        expect:
        CdRemoteScriptPath.applyTemplate('/data/${user}/cd-scripts', 'deploysvc') == '/data/deploysvc/cd-scripts'
    }
}
