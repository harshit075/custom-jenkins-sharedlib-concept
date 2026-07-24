// vars/resolveCdEnv.groovy — P2-1: single env resolution for CD pipeline + job UI (pipeline env + Manage Jenkins globals).
import jenkins.model.Jenkins
import hudson.slaves.EnvironmentVariablesNodeProperty

/**
 * Resolve an env var from {@code pipelineEnv} (when non-null), then Jenkins global properties, then {@code System.getenv}.
 */
String call(String name, Object pipelineEnv = null) {
    if (pipelineEnv != null) {
        try {
            def map = pipelineEnv.getEnvironment()
            if (map != null) {
                def v = map.get(name)
                if (v != null && v.toString().trim()) {
                    return v.toString().trim()
                }
            }
        } catch (Throwable ignored) { }
        try {
            def v2 = pipelineEnv[name]
            if (v2 != null && v2.toString().trim()) {
                return v2.toString().trim()
            }
        } catch (Throwable ignored2) { }
    }
    def v = System.getenv(name)?.toString()?.trim()
    if (v) return v
    try {
        def props = Jenkins.get().getGlobalNodeProperties().getAll(EnvironmentVariablesNodeProperty.class)
        for (p in props) {
            def ev = p.getEnvVars().get(name, '')?.toString()?.trim()
            if (ev) return ev
        }
    } catch (Throwable ignored) { }
    return ''
}
