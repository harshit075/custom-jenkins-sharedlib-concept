package org.customerxp.cd

/**
 * CD pipeline debug logging switch.
 * Enable with {@code JENKINS_CD_DEBUG}: {@code true}, {@code 1}, {@code yes}, {@code y}, {@code on}.
 */
final class CdDebug {

    static boolean enabled(def env) {
        def v = env?.JENKINS_CD_DEBUG
        if (v == null) {
            return false
        }
        def s = v.toString().trim().toLowerCase()
        return s in ['true', '1', 'yes', 'y', 'on']
    }
}
