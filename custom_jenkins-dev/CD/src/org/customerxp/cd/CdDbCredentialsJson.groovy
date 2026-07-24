package org.customerxp.cd

import groovy.json.JsonSlurper

/**
 * Parses PER_MODULE_DB_JSON / DB_CREDENTIALS_INPUT credential payloads after UI normalization.
 * Point #2: single implementation (replaces duplicate {@code parseDbCredentialsJson} in vars).
 */
@SuppressWarnings(['unused'])
final class CdDbCredentialsJson {

    /**
     * @return CPS-serializable {@link Map} or list/scalar per JSON root (same behaviour as legacy vars helper).
     */
    static Object parseCredentialJson(String rawJson) {
        def normalizedJson = CdPipelineUiJson.normalizeCredentialJsonForParse(rawJson)
        def parsed = new JsonSlurper().parseText(normalizedJson)
        return CdJsonTrees.toSerializablePlain(parsed)
    }
}
