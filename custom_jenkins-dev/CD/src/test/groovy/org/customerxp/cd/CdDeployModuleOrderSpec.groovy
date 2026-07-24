package org.customerxp.cd

import spock.lang.Specification

/**
 * Deploy ordering for {@code executeDeployPipeline}: topological {@code dependsOn} among selected ids
 * + registry tie-breaks (CPS-safe insertion sort in {@link CdDeployModuleOrder}).
 */
class CdDeployModuleOrderSpec extends Specification {

    private static Map minimalRegistry(List<Map> modules) {
        [modules: modules]
    }

    def 'two SHARED_DB modules without cc: both in-degree 0; tie-break by module id'() {
        given:
        def reg = minimalRegistry([
            [id: 'efmapp', deployKind: 'module_deploy', dbRole: 'SHARED_DB', dependsOn: ['cc']],
            [id: 'cmq', deployKind: 'module_deploy', dbRole: 'SHARED_DB', dependsOn: ['cc']],
        ])
        when:
        def order = CdDeployModuleOrder.sortForDeploy(['efmapp', 'cmq'], reg)
        then:
        order == ['cmq', 'efmapp']
    }

    def 'cmq and hes4j without cc: stable tie-break (CPS-safe two-module sort)'() {
        given:
        def reg = minimalRegistry([
            [id: 'cmq', deployKind: 'module_deploy', dbRole: 'SHARED_DB', dependsOn: ['cc']],
            [id: 'hes4j', deployKind: 'module_deploy', dbRole: 'SHARED_DB', dependsOn: ['cc']],
        ])
        when:
        def order = CdDeployModuleOrder.sortForDeploy(['hes4j', 'cmq'], reg)
        then:
        order instanceof List
        order.size() == 2
        order == ['cmq', 'hes4j']
    }

    def 'cmq depends on cc when both selected: cc runs first'() {
        given:
        def reg = minimalRegistry([
            [id: 'cc', deployKind: 'cc_setup', deployOrder: 0],
            [id: 'cmq', deployKind: 'module_deploy', dependsOn: ['cc']],
        ])
        when:
        def order = CdDeployModuleOrder.sortForDeploy(['cmq', 'cc'], reg)
        then:
        order == ['cc', 'cmq']
    }

    def 'cycle among selected modules throws IllegalStateException'() {
        given:
        def reg = minimalRegistry([
            [id: 'a', deployKind: 'module_deploy', dependsOn: ['b']],
            [id: 'b', deployKind: 'module_deploy', dependsOn: ['a']],
        ])
        when:
        CdDeployModuleOrder.sortForDeploy(['a', 'b'], reg)
        then:
        def e = thrown(IllegalStateException)
        e.message.contains('cyclic')
    }
}
