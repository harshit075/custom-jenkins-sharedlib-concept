package org.customerxp.cd

/**
 * P3-3: Shared string normalization for JSON-ish values coming from Jenkins UI parameters
 * (escaped quotes, trailing commas, hidden placeholders).
 */
@SuppressWarnings(['unused'])
final class CdPipelineUiJson {

    /**
     * Structured JSON blobs (e.g. {@code PER_MODULE_DB_JSON}) — empty string means “no payload”.
     */
    static String normalizeStructuredParam(Object raw) {
        def normalizedJson = raw != null ? raw.toString().trim() : ''
        if (!normalizedJson || normalizedJson == 'SHARED_MODULE_NO_INPUT' || normalizedJson.startsWith('[HIDDEN]')) {
            return ''
        }
        if (normalizedJson.endsWith(',')) {
            normalizedJson = normalizedJson.substring(0, normalizedJson.length() - 1).trim()
        }
        if ((normalizedJson.startsWith('"') && normalizedJson.endsWith('"')) ||
            (normalizedJson.startsWith("'") && normalizedJson.endsWith("'"))) {
            normalizedJson = normalizedJson.substring(1, normalizedJson.length() - 1)
        }
        normalizedJson = normalizedJson.replace('\\"', '"')
        normalizedJson = normalizedJson.replace("\\'", "'")
        normalizedJson
    }

    /**
     * Credential JSON strings — always returns a non-empty JSON object string (min {@code \{\}}).
     */
    static String normalizeCredentialJsonForParse(Object raw) {
        def normalizedJson = normalizeStructuredParam(raw)
        if (!normalizedJson) {
            return '{}'
        }
        normalizedJson
    }

    /**
     * True when a job parameter value is an Active Choices placeholder, not a real DB setup or server name.
     * Used from {@code vars-cd/executeDeployPipeline} static helpers (must stay {@code @NonCPS}-safe / no workflow deps).
     */
    static boolean isPlaceholderAppOrDbChoice(String v) {
        if (v == null) {
            return true
        }
        def s = v.toString().trim()
        if (!s) {
            return true
        }
        if (s.startsWith('[DEBUG]')) {
            return true
        }
        if (s.startsWith('[ERROR]')) {
            return true
        }
        if (s.startsWith('[HIDDEN]')) {
            return true
        }
        if (s.startsWith('[N/A') || s.startsWith('— N/A') || s.toLowerCase().contains('not selected')) {
            return true
        }
        return false
    }
}
