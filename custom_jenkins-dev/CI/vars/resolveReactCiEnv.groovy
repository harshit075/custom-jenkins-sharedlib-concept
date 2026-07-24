// vars/resolveReactCiEnv.groovy
//
// Single source for Create React App / react-scripts CI strictness (ESLint warnings as errors).
// Manage Jenkins → Global properties → CX_REACT_CI_STRICT (default unset ⇒ false, legacy efmapp parity).

/**
 * Reads {@code CX_REACT_CI_STRICT} from the environment.
 *
 * @return {@code 'true'} or {@code 'false'} for {@code export CI=...} and Jenkins {@code withEnv}
 */
def call() {
    return parseCxReactCiStrict()
}

/**
 * Resolved value for the current run: injected {@code pipelineCfg}, then {@code CX_PIPELINE_CFG_JSON}, then live env.
 *
 * @param pipelineCfg optional map from {@code cxPipelineConfig} / caller
 * @return {@code 'true'} or {@code 'false'}
 */
def effective(Map pipelineCfg = null) {
    String fromCfg = pipelineCfg?.reactCiExport?.toString()?.trim()
    if (fromCfg in ['true', 'false']) {
        return fromCfg
    }
    if (env.CX_PIPELINE_CFG_JSON?.toString()?.trim()) {
        def serde = loadSharedLibVarScript('pipelineConfigSerde')
        if (serde) {
            Map jsonCfg = serde.pipelineConfigFromJson(env.CX_PIPELINE_CFG_JSON.toString())
            fromCfg = jsonCfg?.reactCiExport?.toString()?.trim()
            if (fromCfg in ['true', 'false']) {
                return fromCfg
            }
        }
    }
    return parseCxReactCiStrict()
}

String parseCxReactCiStrict() {
    def raw = env.CX_REACT_CI_STRICT?.toString()?.trim()
    if (!raw) {
        return 'false'
    }
    def pl = raw.toLowerCase()
    if (pl in ['true', '1', 'yes']) {
        return 'true'
    }
    if (pl in ['false', '0', 'no']) {
        return 'false'
    }
    error("❌ CX_REACT_CI_STRICT must be true|false|1|0|yes|no (got '${env.CX_REACT_CI_STRICT}').")
}
