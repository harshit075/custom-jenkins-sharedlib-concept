package org.customerxp.cd

/**
 * Canonical {@code deployKind} strings in {@code module-registry.json} (must match {@code module-registry.schema.json}).
 * These name <strong>deployment behavior</strong> (ordering, remote wrapper, env shape), not the Nexus script filename.
 * Actual script file names are configured via Jenkins env (e.g. {@code JENKINS_NEXUS_CC_SETUP_SCRIPT_NAME},
 * {@code JENKINS_NEXUS_MODULE_DEPLOY_SCRIPT_NAME}) when composing URLs in {@link CdNexusEnvUrls} and {@code getCDDefaults}.
 */
@SuppressWarnings(['unused'])
final class CdDeployKinds {

    static final String CC_SETUP = 'cc_setup'
    static final String MODULE_DEPLOY = 'module_deploy'

    private CdDeployKinds() {}
}
