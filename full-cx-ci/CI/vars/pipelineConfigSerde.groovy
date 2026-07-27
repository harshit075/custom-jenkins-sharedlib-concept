// vars/pipelineConfigSerde.groovy
//
// EXACT REPLICA OF: CustomerXP pipelineConfigSerde.groovy
//
// PURPOSE:
// Serializes/deserializes the cxPipelineConfig Map to/from a JSON string.
// This solves a key problem in parallel builds:
//
// PROBLEM: When executeChainBuild runs modules in parallel (one per JDK),
// each parallel branch is a separate Groovy closure. If each closure
// calls cxPipelineConfig() independently, it validates env vars N times
// (once per JDK per module) — slow and noisy.
//
// SOLUTION: Validate ONCE in stage('Validate global configuration'),
// serialize the result to env.CX_PIPELINE_CFG_JSON, then each parallel
// cell deserializes it instead of calling cxPipelineConfig() again.
// This is "Option A" in the CustomerXP codebase comments.
//
// USAGE:
//   // In Validate stage:
//   def cfg = cxPipelineConfig(true)
//   env.CX_PIPELINE_CFG_JSON = pipelineConfigSerde.pipelineConfigToJson(cfg)
//
//   // In each parallel build cell (buildModulesWithJDK):
//   def cfg = pipelineConfigSerde.pipelineConfigFromJson(env.CX_PIPELINE_CFG_JSON)
//   if (cfg == null) { cfg = cxPipelineConfig(true) } // fallback

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

def pipelineConfigToJson(Map cfg) {
    if (cfg == null) return null
    try {
        // Serialize — convert Lists to plain Lists (JsonOutput handles them)
        return JsonOutput.toJson(cfg)
    } catch (Throwable t) {
        echo "⚠️ pipelineConfigSerde.pipelineConfigToJson: serialization failed: ${t.message}"
        return null
    }
}

def pipelineConfigFromJson(String json) {
    if (!json?.trim()) return null
    try {
        def parsed = new JsonSlurperClassic().parseText(json)
        // Rebuild typed fields
        def cfg = new LinkedHashMap(parsed)
        // Restore List fields that may have been deserialized as LazyMap arrays
        ['chainOrder', 'coreModuleIds', 'installerModuleIds', 'jdkOptions'].each { key ->
            if (cfg[key] != null) {
                cfg[key] = cfg[key].collect { it.toString() }
            }
        }
        return cfg
    } catch (Throwable t) {
        echo "⚠️ pipelineConfigSerde.pipelineConfigFromJson: deserialization failed: ${t.message}"
        return null
    }
}

return this
