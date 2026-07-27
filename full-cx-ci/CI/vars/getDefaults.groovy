// vars/getDefaults.groovy
//
// EXACT REPLICA OF: CustomerXP getDefaults.groovy
//
// PURPOSE:
// Central store for all default/fallback values used across the pipeline.
// Every method reads from Jenkins global env vars and returns a typed value.
// Fails with a clear error if a required var is missing.
//
// ADAPTED FOR LOCAL JENKINS:
// - Same method signatures as CustomerXP
// - Uses CX_* env var names matching our cxPipelineConfig
// - getRunSecurityTools() defaults to false locally (no SonarQube/Nexus)

def getDefaultModules() {
    def v = env.CX_CORE_MODULE_IDS?.trim()
    if (!v) {
        def choices = loadSharedLibVarScript('getModuleChoices')
        if (choices) {
            def result = choices.call()
            if (result) return result.join(',')
        }
        error "❌ getDefaults.getDefaultModules: CX_CORE_MODULE_IDS is not set and getModuleChoices returned empty."
    }
    return v
}

def getDefaultJDKs() {
    def v = env.CX_JDK_VERSION_DEFAULT?.trim() ?: env.CX_JDK_VERSION_OPTIONS?.trim()
    if (!v) error "❌ getDefaults.getDefaultJDKs: CX_JDK_VERSION_DEFAULT or CX_JDK_VERSION_OPTIONS must be set."
    def list = v.split(',').collect { it.trim() }.findAll { it }
    // Prefer JDK 17 or 21 if available (same logic as CustomerXP)
    def preferred = list.find { it.contains('17') || it.contains('21') }
    return preferred ? [preferred] : [list[0]]
}

def getDefaultBranch() {
    return env.CX_DEFAULT_BRANCH?.trim() ?: 'main'
}

def getBuildTypeChoices() {
    def v = env.CX_BUILD_TYPES?.trim()
    if (!v) return ['snapshot', 'release']
    return v.split(',').collect { it.trim() }.findAll { it }
}

def getBranchFilter() {
    return env.CX_BRANCH_FILTER?.trim() ?: 'refs/heads/(main.*|master.*)'
}

def getBranchSuffix() {
    return env.CX_BRANCH_SUFFIX?.trim() ?: 'main'
}

def getCronSchedule() {
    def v = env.JENKINS_CRON_SCHEDULE?.trim()
    if (!v) error "❌ getDefaults.getCronSchedule: JENKINS_CRON_SCHEDULE is not set."
    return v
}

def getFailureEmailRecipients() {
    def v = env.JENKINS_FAILURE_EMAIL_RECIPIENTS?.trim()
    if (!v) error "❌ getDefaults.getFailureEmailRecipients: JENKINS_FAILURE_EMAIL_RECIPIENTS is not set."
    return v
}

def getFailureEmailSubjectTemplate() {
    return env.JENKINS_FAILURE_EMAIL_SUBJECT?.trim() ?: ''
}

def getFailureEmailBodyTemplate() {
    return env.JENKINS_FAILURE_EMAIL_BODY?.trim() ?: ''
}

def getSequentialInstallerModuleIds() {
    def v = env.CX_SEQUENTIAL_INSTALLER_MODULE_IDS?.trim() ?: env.CX_CHAIN_INSTALLER_MODULES?.trim()
    if (!v) error "❌ getDefaults.getSequentialInstallerModuleIds: CX_SEQUENTIAL_INSTALLER_MODULE_IDS is not set."
    return v.split(',').collect { it.trim() }.findAll { it }
}

def getSonarReportDisplayName() {
    def v = env.JENKINS_SONAR_REPORT_NAME?.trim() ?: env.CX_SONAR_REPORT_NAME?.trim()
    if (!v) error "❌ getDefaults.getSonarReportDisplayName: JENKINS_SONAR_REPORT_NAME or CX_SONAR_REPORT_NAME is not set."
    return v
}

def getRunSecurityTools() {
    // Default FALSE locally — no SonarQube/Nexus/DefectDojo available
    // In production CustomerXP this defaults to TRUE
    def v = env.CX_RUN_SECURITY_TOOLS?.trim()?.toLowerCase()
    if (!v) return false   // LOCAL DEFAULT: off unless explicitly enabled
    return v in ['true', '1', 'yes']
}

def getDependencyTrackInstance() {
    return env.CX_DEPENDENCY_TRACK_INSTANCE?.trim() ?: 'Dependency-Track-Server'
}

def getDTUnstableCriticalThreshold() {
    return env.CX_DT_UNSTABLE_CRITICAL?.trim() ? env.CX_DT_UNSTABLE_CRITICAL.toInteger() : 1
}

def getDTUnstableHighThreshold() {
    return env.CX_DT_UNSTABLE_HIGH?.trim() ? env.CX_DT_UNSTABLE_HIGH.toInteger() : 5
}

def getDTUnstableMediumThreshold() {
    return env.CX_DT_UNSTABLE_MEDIUM?.trim() ? env.CX_DT_UNSTABLE_MEDIUM.toInteger() : 10
}

def getDTFailedCriticalThreshold() {
    return env.CX_DT_FAILED_CRITICAL?.trim() ? env.CX_DT_FAILED_CRITICAL.toInteger() : 5
}

def getDTFailedHighThreshold() {
    return env.CX_DT_FAILED_HIGH?.trim() ? env.CX_DT_FAILED_HIGH.toInteger() : 10
}

return this
