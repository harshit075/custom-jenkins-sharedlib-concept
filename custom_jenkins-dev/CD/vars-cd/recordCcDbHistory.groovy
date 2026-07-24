// vars/recordCcDbHistory.groovy
import org.customerxp.cd.CdCcDbHistory

/**
 * Appends a CC DB {@code setupId} to {@code JENKINS_CD_CC_DB_HISTORY_FILE} (Option A — recent installs dropdown).
 * Writes only when path is allowed by {@code JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX} when that prefix is set.
 *
 * @param args.historyPath optional override; default {@code env.JENKINS_CD_CC_DB_HISTORY_FILE}
 * @param args.setupId required setup name (matches {@code JENKINS_CD_DB_CONFIG_FILE})
 * @param args.maxEntries default {@link CdCcDbHistory#DEFAULT_MAX_ENTRIES}
 */
def call(Map args = [:]) {
    def path = (args.historyPath ?: env.JENKINS_CD_CC_DB_HISTORY_FILE)?.toString()?.trim() ?: ''
    def setupId = args.setupId?.toString()?.trim() ?: ''
    if (!path || !setupId) {
        return
    }
    def allowed = env.JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX?.toString()?.trim() ?: ''
    if (allowed?.trim() && !CdCcDbHistory.isPathUnderAllowedPrefix(path, allowed)) {
        echo "WARN: recordCcDbHistory skipped — path not under JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX (same rule as persistDbConfig)."
        return
    }
    def max = (args.maxEntries != null) ? (args.maxEntries as int) : CdCcDbHistory.DEFAULT_MAX_ENTRIES
    def entry = [
        setupId      : setupId,
        ts           : System.currentTimeMillis(),
        jobName      : env.JOB_NAME?.toString(),
        buildNumber  : env.BUILD_NUMBER?.toString(),
        buildUrl     : env.BUILD_URL?.toString(),
        displayLine  : "${setupId} | ${env.JOB_NAME ?: 'job'} #${env.BUILD_NUMBER ?: '?'}"
    ]
    CdCcDbHistory.prependDedupe(path, entry, max, allowed)
    echo "✅ CC DB history recorded: setupId=${setupId} → ${path}"
}
