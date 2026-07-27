// vars/runSecurityToolsParams.groovy
//
// EXACT REPLICA OF: CustomerXP runSecurityToolsParams.groovy
//
// PURPOSE:
// Resolves the job-level security tools flag.
// null means "inherit from global" (default true).
// Explicit false disables security tools for that run.

boolean resolveJobRunSecurityTools(def jobFlag) {
    jobFlag == null ? true : (jobFlag as boolean)
}

return this
