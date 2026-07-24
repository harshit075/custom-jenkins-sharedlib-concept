package org.customerxp.cd

/**
 * Resolves another Jenkins shared-library global var by name from the current pipeline script ({@code this}).
 * Point #3: single implementation replacing duplicated {@code loadSharedLibVarScript} helpers in vars-cd.
 */
@SuppressWarnings(['unused'])
final class CdSharedLibVarLookup {

    /**
     * @param script current vars script instance ({@code this} inside {@code call(Map)})
     * @param scriptName global var name without {@code .groovy}, e.g. {@code prepareCDEnvFile}
     * @return the resolved step script object, or {@code null} if missing
     */
    static Object resolve(Object script, String scriptName) {
        if (script == null || !(scriptName?.toString()?.trim())) {
            return null
        }
        def n = scriptName.toString().trim()
        try {
            return script.getProperty(n)
        } catch (MissingPropertyException e) {
            return null
        }
    }
}
