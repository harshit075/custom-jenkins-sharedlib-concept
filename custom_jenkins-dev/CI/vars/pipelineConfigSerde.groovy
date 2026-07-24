// vars/pipelineConfigSerde.groovy
// Serialize cxPipelineConfig() return map for env.CX_PIPELINE_CFG_JSON (Option A — single validation per run).

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

String pipelineConfigToJson(Map cfg) {
    if (cfg == null) {
        error '❌ pipelineConfigToJson: cfg map is null.'
    }
    JsonOutput.toJson(cfg)
}

/**
 * Rehydrates the map produced by {@code cxPipelineConfig}; returns null if empty/invalid.
 */
Map pipelineConfigFromJson(String json) {
    if (!json?.toString()?.trim()) {
        return null
    }
    def parsed = new JsonSlurper().parseText(json.toString().trim())
    if (!(parsed instanceof Map)) {
        return null
    }
    return new LinkedHashMap(parsed as Map)
}

return this
