// vars/getDefaults.groovy

/**
 * Provides default values and configuration choices for Jenkins pipeline parameters.
 * This script handles dynamic loading of other shared library utilities (like getModuleChoices)
 * using the global 'loadSharedLibVarScript' DSL method. It ensures that pipelines have sensible defaults
 * even if specific environment variables are not configured.
 */

/**
 * Determines the default list of modules to be selected for a build.
 * If `JENKINS_DEFAULT_MODULES` is set, returns it (explicit pipeline configuration).
 * Otherwise loads module choices dynamically from `getModuleChoices.groovy`; any failure or empty result fails the step with an explicit error (no hardcoded module fallback).
 *
 * @return A comma-separated string of default module names (suitable for extendedChoice `value`).
 */
def getDefaultModules() {
    def explicit = env.JENKINS_DEFAULT_MODULES?.trim()
    if (explicit) {
        return explicit
    }

    def getModuleChoicesScript = loadSharedLibVarScript('getModuleChoices')
    if (!getModuleChoicesScript) {
        error("❌ getModuleChoices could not be loaded. Set JENKINS_DEFAULT_MODULES or fix the shared library.")
    }

    def dynamicallyLoadedModules
    try {
        dynamicallyLoadedModules = getModuleChoicesScript.call()
        echo "DEBUG: Dynamically loaded module choices: ${dynamicallyLoadedModules.join(', ')}"
    } catch (Exception e) {
        error("❌ Failed to execute getModuleChoices: ${e.message}")
    }

    if (!dynamicallyLoadedModules || dynamicallyLoadedModules.isEmpty()) {
        error("❌ getModuleChoices returned no modules. Set JENKINS_DEFAULT_MODULES or fix Git/module discovery.")
    }

    return dynamicallyLoadedModules.join(',')
}

/**
 * Determines the default list of JDK versions to be selected for a build.
 * If `JENKINS_DEFAULT_JDKS` is set, returns it (explicit pipeline configuration).
 * Otherwise loads JDK tool names from `getJDKChoices.groovy`; prioritizes jdk-17 / jdk-21 when present.
 * Any failure to load or empty result fails the step with an explicit error (no hardcoded JDK fallback).
 *
 * @return A comma-separated string of default JDK versions.
 */
def getDefaultJDKs() {
    def explicit = env.JENKINS_DEFAULT_JDKS?.trim()
    if (explicit) {
        return explicit
    }

    def getJDKChoicesScript = loadSharedLibVarScript('getJDKChoices')
    if (!getJDKChoicesScript) {
        error("❌ getJDKChoices could not be loaded. Set JENKINS_DEFAULT_JDKS or fix the shared library.")
    }

    def dynamicallyLoadedJDKs
    try {
        dynamicallyLoadedJDKs = getJDKChoicesScript.call()
        echo "DEBUG: Dynamically loaded JDK choices: ${dynamicallyLoadedJDKs.join(', ')}"
    } catch (Exception e) {
        error("❌ Failed to execute getJDKChoices: ${e.message}")
    }

    if (!dynamicallyLoadedJDKs || dynamicallyLoadedJDKs.isEmpty()) {
        error("❌ getJDKChoices returned no JDK tool names. Set JENKINS_DEFAULT_JDKS or update getJDKChoices.groovy.")
    }

    def preferredJDKs = dynamicallyLoadedJDKs.findAll { it.contains('17') || it.contains('21') }
    def finalJDKSelection = preferredJDKs.isEmpty() ? dynamicallyLoadedJDKs.join(',') : preferredJDKs.join(',')

    return finalJDKSelection
}

/**
 * Retrieves the default Git branch name.
 * Falls back to 'develop' if the `JENKINS_DEFAULT_BRANCH` environment variable is not set.
 *
 * @return The default branch name string.
 */
def getDefaultBranch() {
    return env.JENKINS_DEFAULT_BRANCH ?: 'develop'
}

/**
 * Retrieves the available build type choices.
 * Falls back to `['develop', 'release']` if `JENKINS_BUILD_TYPES` environment variable is not set or empty.
 *
 * @return A List of build type strings.
 */
def getBuildTypeChoices() {
    return env.JENKINS_BUILD_TYPES?.split(',')?.collect { it.trim() }?.findAll { it } ?: ['develop', 'release']
}

/**
 * Retrieves the regular expression filter for Git branches.
 * Falls back to `'refs/heads/(develop.*)'` if the `JENKINS_BRANCH_FILTER` environment variable is not set.
 *
 * @return The branch filter regex string.
 */
def getBranchFilter() {
    return env.JENKINS_BRANCH_FILTER ?: 'refs/heads/(develop.*)'
}

/**
 * Retrieves the suffix to append to the branch name for workspace naming or other purposes.
 * Falls back to `'develop'` if the `JENKINS_BRANCH_SUFFIX` environment variable is not set.
 *
 * @return The branch suffix string.
 */
def getBranchSuffix() {
    return env.JENKINS_BRANCH_SUFFIX ?: 'develop'
}

/**
 * Quartz cron for scheduled CI jobs (e.g. nightly SNAPSHOT pipeline).
 * Set {@code JENKINS_CRON_SCHEDULE} in Jenkins global properties (e.g. {@code 0 0 * * *}).
 */
def getCronSchedule() {
    def v = env.JENKINS_CRON_SCHEDULE?.toString()?.trim()
    if (!v) {
        error '❌ Set JENKINS_CRON_SCHEDULE in Jenkins global properties (Quartz cron expression).'
    }
    return v
}

/**
 * Comma-separated failure notification email recipients.
 * Set {@code JENKINS_FAILURE_EMAIL_RECIPIENTS} in Jenkins global properties.
 */
def getFailureEmailRecipients() {
    def v = env.JENKINS_FAILURE_EMAIL_RECIPIENTS?.toString()?.trim()
    if (!v) {
        error '❌ Set JENKINS_FAILURE_EMAIL_RECIPIENTS in Jenkins global properties.'
    }
    return v
}

/**
 * Optional email subject template; placeholders: {@code {JOB_NAME}}, {@code {BUILD_NUMBER}}, {@code {RESULT}}, {@code {BUILD_URL}}.
 */
def getFailureEmailSubjectTemplate() {
    return env.JENKINS_FAILURE_EMAIL_SUBJECT?.toString()?.trim() ?: ''
}

/**
 * Optional email body template; same placeholders as {@link #getFailureEmailSubjectTemplate}.
 */
def getFailureEmailBodyTemplate() {
    return env.JENKINS_FAILURE_EMAIL_BODY?.toString()?.trim() ?: ''
}

/**
 * GitLab branches API page size. Set {@code CX_GITLAB_BRANCH_PER_PAGE} (e.g. {@code 100}).
 */
int getGitlabBranchPerPage() {
    def v = env.CX_GITLAB_BRANCH_PER_PAGE?.toString()?.trim()
    if (!v) {
        error '❌ Set CX_GITLAB_BRANCH_PER_PAGE in Jenkins global properties (e.g. 100).'
    }
    return v.toInteger()
}

/**
 * Max GitLab branch API pages. Set {@code CX_GITLAB_BRANCH_MAX_PAGES} (e.g. {@code 50}).
 */
int getGitlabBranchMaxPages() {
    def v = env.CX_GITLAB_BRANCH_MAX_PAGES?.toString()?.trim()
    if (!v) {
        error '❌ Set CX_GITLAB_BRANCH_MAX_PAGES in Jenkins global properties (e.g. 50).'
    }
    return v.toInteger()
}

/**
 * Installer module ids built in the sequential lane with core (chain order).
 * Uses {@code CX_SEQUENTIAL_INSTALLER_MODULE_IDS}, else {@code CX_CREINSTALLER_MODULES}.
 */
List<String> getSequentialInstallerModuleIds() {
    def raw = (env.CX_SEQUENTIAL_INSTALLER_MODULE_IDS ?: env.CX_CREINSTALLER_MODULES ?: '').toString().trim()
    if (!raw) {
        error '❌ Set CX_SEQUENTIAL_INSTALLER_MODULE_IDS or CX_CREINSTALLER_MODULES in Jenkins global properties.'
    }
    return raw.split(',').collect { it.trim() }.findAll { it }
}

/**
 * Sonar HTML report link title. Set {@code JENKINS_SONAR_REPORT_NAME} or {@code CX_SONAR_REPORT_NAME}.
 */
String getSonarReportDisplayName() {
    def v = (env.JENKINS_SONAR_REPORT_NAME ?: env.CX_SONAR_REPORT_NAME ?: '').toString().trim()
    if (!v) {
        error '❌ Set JENKINS_SONAR_REPORT_NAME or CX_SONAR_REPORT_NAME in Jenkins global properties.'
    }
    return v
}

/**
 * Global config switch for security tools (and JaCoCo).
 * When false, pipelines skip: SonarQube, SBOM, Dependency-Track, DefectDojo, and JaCoCo (report generation + publishing).
 * Set in Jenkins global config (e.g. Global properties / Environment variables): JENKINS_RUN_SECURITY_TOOLS = true|false
 * Values treated as enabled: true, 1, yes (case-insensitive). Anything else (false, 0, no, empty) = disabled.
 * When unset, defaults to true (security tools and JaCoCo run).
 *
 * @return true if security tools (and JaCoCo) should run, false to skip them globally.
 */
def getRunSecurityTools() {
    def v = env.JENKINS_RUN_SECURITY_TOOLS?.toString()?.trim()?.toLowerCase()
    if (v == null || v.isEmpty()) return true
    return v in ['true', '1', 'yes']
}

/**
 * Retrieves the Dependency-Track instance name configured in Jenkins.
 * Falls back to `'Dependency-Track-Server'` if `JENKINS_DEPENDENCY_TRACK_INSTANCE` is not set.
 *
 * @return The Dependency-Track instance name string.
 */
def getDependencyTrackInstance() {
    return env.JENKINS_DEPENDENCY_TRACK_INSTANCE ?: 'Dependency-Track-Server'
}

/**
 * Retrieves the Dependency-Track quality gate threshold for unstable builds (critical vulnerabilities).
 * Falls back to `1` if `JENKINS_DT_UNSTABLE_CRITICAL` is not set.
 *
 * @return The threshold as an integer.
 */
def getDTUnstableCriticalThreshold() {
    return env.JENKINS_DT_UNSTABLE_CRITICAL?.toInteger() ?: 1
}

/**
 * Retrieves the Dependency-Track quality gate threshold for unstable builds (high vulnerabilities).
 * Falls back to `5` if `JENKINS_DT_UNSTABLE_HIGH` is not set.
 *
 * @return The threshold as an integer.
 */
def getDTUnstableHighThreshold() {
    return env.JENKINS_DT_UNSTABLE_HIGH?.toInteger() ?: 5
}

/**
 * Retrieves the Dependency-Track quality gate threshold for unstable builds (medium vulnerabilities).
 * Falls back to `10` if `JENKINS_DT_UNSTABLE_MEDIUM` is not set.
 *
 * @return The threshold as an integer.
 */
def getDTUnstableMediumThreshold() {
    return env.JENKINS_DT_UNSTABLE_MEDIUM?.toInteger() ?: 10
}

/**
 * Retrieves the Dependency-Track quality gate threshold for failed builds (critical vulnerabilities).
 * Falls back to `5` if `JENKINS_DT_FAILED_CRITICAL` is not set.
 *
 * @return The threshold as an integer.
 */
def getDTFailedCriticalThreshold() {
    return env.JENKINS_DT_FAILED_CRITICAL?.toInteger() ?: 5
}

/**
 * Retrieves the Dependency-Track quality gate threshold for failed builds (high vulnerabilities).
 * Falls back to `10` if `JENKINS_DT_FAILED_HIGH` is not set.
 *
 * @return The threshold as an integer.
 */
def getDTFailedHighThreshold() {
    return env.JENKINS_DT_FAILED_HIGH?.toInteger() ?: 10
}

/**
 * Constructs the Git repository URL used for discovery purposes (e.g., in parameters).
 * It uses the first module from the default module list to form the URL.
 * Requires `JENKINS_GIT_URL`, `JENKINS_GIT_ORG`, and `JENKINS_GIT_PROJECT` environment variables.
 *
 * @return The discovery Git repository URL string.
 * @throws FlowInterruptedException if `getDefaultModules()` fails to return a module,
 *                                  or if necessary environment variables are missing.
 */
def getDiscoveryRepoURL() {
    if (!env.JENKINS_GIT_URL || !env.JENKINS_GIT_ORG?.toString()?.trim() || !env.JENKINS_GIT_PROJECT) {
        error("❌ Required environment variables (JENKINS_GIT_URL, JENKINS_GIT_ORG, JENKINS_GIT_PROJECT) for getDiscoveryRepoURL are not set.")
    }

    def defaultModulesString = getDefaultModules()
    def firstModule = defaultModulesString.split(',').first()?.trim()

    if (!firstModule || firstModule.isEmpty()) {
        error("❌ Cannot determine discovery repository URL: getDefaultModules() returned no valid module for URL construction.")
    }
    return "${env.JENKINS_GIT_URL}/${env.JENKINS_GIT_ORG}/${env.JENKINS_GIT_PROJECT}/${firstModule}.git"
}

return this