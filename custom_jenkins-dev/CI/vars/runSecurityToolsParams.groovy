// vars/runSecurityToolsParams.groovy
//
// Single place for job-level runSecurityTools default when callers pass null / omit the flag.

/**
 * Resolves the job-level security-tools toggle from orchestrators ({@code executeChainBuild}, etc.)
 * and {@code buildModulesWithJDK}. {@code null} means "use legacy default" → {@code true}.
 *
 * @param jobFlag boxed boolean from Jenkins params or {@code null}
 * @return primitive {@code boolean} suitable for combining with {@code getRunSecurityTools()}
 */
boolean resolveJobRunSecurityTools(def jobFlag) {
    jobFlag == null ? true : (jobFlag as boolean)
}

return this
