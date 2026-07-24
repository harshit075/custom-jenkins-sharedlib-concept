package org.customerxp.cd

/**
 * CD job workspace root normalization. CC DB context file path is only
 * {@code JENKINS_CD_CC_DB_CONTEXT_FILE} — see {@code manageDbContext.getContextFilePath}.
 */
@SuppressWarnings(['unused'])
final class CdDbContextPaths {

    static final String CONTEXT_FILE_NAME = 'cc_db_context.json'

    private CdDbContextPaths() {}

    /** Trim and strip trailing slashes/backslashes. */
    static String normalizeRepoRoot(String repoRoot) {
        def s = repoRoot?.toString()?.trim() ?: ''
        if (!s) {
            return ''
        }
        s.replaceAll(/[\/\\]+$/, '')
    }
}
