// vars/cdDebug.groovy — optional CD pipeline debug lines (gated by JENKINS_CD_DEBUG).
import org.customerxp.cd.CdDebug

/**
 * Emit a debug line only when {@code JENKINS_CD_DEBUG} is on ({@code true}/{@code 1}/{@code yes}/{@code y}/{@code on}).
 * <ul>
 *   <li>{@code cdDebug 'message'}</li>
 *   <li>{@code cdDebug(message: '...')} or {@code cdDebug(msg: '...')}</li>
 * </ul>
 */
def call(String message) {
    if (!CdDebug.enabled(env)) {
        return
    }
    def m = message?.toString()?.trim()
    if (m) {
        echo "CD DEBUG: ${m}"
    }
}

def call(Map args = [:]) {
    if (!CdDebug.enabled(env)) {
        return
    }
    def m = (args.message ?: args.msg)?.toString()?.trim()
    if (m) {
        echo "CD DEBUG: ${m}"
    }
}
