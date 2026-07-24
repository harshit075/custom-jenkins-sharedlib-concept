package org.customerxp.cd

import spock.lang.Specification

class CdNexusEnvUrlsSpec extends Specification {

    def 'nexusArtifactVersionTag strips one leading v for Nexus paths'() {
        expect:
        CdNexusEnvUrls.nexusArtifactVersionTag(input) == expected

        where:
        input                    | expected
        'vClari5.Neo.5'          | 'Clari5.Neo.5'
        'v4955_200TPS_BaseLine'  | '4955_200TPS_BaseLine'
        'vlh-0.1'                | 'lh-0.1'
        'Clari5.Neo.5'           | 'Clari5.Neo.5'
        '4.10-SNAPSHOT'          | '4.10-SNAPSHOT'
        ''                       | ''
        null                     | ''
    }
}
