// vars/getCredentials.groovy

/**
 * A collection of utility functions to retrieve various credential IDs and configuration values
 * from the Jenkins environment. This centralizes credential lookup and provides consistent
 * error handling and default values.
 */

/**
 * Retrieves the Jenkins credential ID for Git repository access (username/password).
 *
 * This function expects the `JENKINS_GIT_CREDENTIALS` environment variable to be set
 * in the Jenkins job configuration.
 *
 * @return The Git credential ID string.
 * @throws FlowInterruptedException if `JENKINS_GIT_CREDENTIALS` is not configured.
 */
def getGitCredentials() {
    // Explicitly check and provide a clear error message if the credential ID is missing.
    if (!env.JENKINS_GIT_CREDENTIALS) {
        error("❌ Required environment variable JENKINS_GIT_CREDENTIALS is not configured. Please set it in your Jenkins job.")
    }
    return env.JENKINS_GIT_CREDENTIALS
}

/**
 * Retrieves the Jenkins credential ID for Git repository SSH access.
 *
 * This function first checks for `JENKINS_GIT_SSH_CREDENTIALS`. If not found,
 * it falls back to the ID provided by `getGitCredentials()`.
 *
 * @return The Git SSH credential ID string.
 * @throws FlowInterruptedException if neither `JENKINS_GIT_SSH_CREDENTIALS` nor `JENKINS_GIT_CREDENTIALS` are configured.
 */
def getGitSSHCredentials() {
    // If JENKINS_GIT_SSH_CREDENTIALS is not set, try to use the default Git credentials.
    // getGitCredentials() will throw an error if that's also not set.
    return env.JENKINS_GIT_SSH_CREDENTIALS ?: getGitCredentials()
}

/**
 * Retrieves the Jenkins credential ID for general build operations.
 *
 * This function prioritizes `JENKINS_BUILD_CREDENTIALS`. If not found,
 * it falls back to `JENKINS_GIT_CREDENTIALS`.
 *
 * @return The build credential ID string.
 * @throws FlowInterruptedException if neither `JENKINS_BUILD_CREDENTIALS` nor `JENKINS_GIT_CREDENTIALS` are configured.
 */
def getBuildCredentials() {
    // Provide a clear error path if both primary and fallback credential IDs are missing.
    if (!env.JENKINS_BUILD_CREDENTIALS && !env.JENKINS_GIT_CREDENTIALS) {
        error("❌ Neither JENKINS_BUILD_CREDENTIALS nor JENKINS_GIT_CREDENTIALS are configured. One is required for build operations.")
    }
    return env.JENKINS_BUILD_CREDENTIALS ?: env.JENKINS_GIT_CREDENTIALS
}

/**
 * Retrieves the Jenkins credential ID for Slack notifications.
 *
 * This function expects the `JENKINS_SLACK_CREDENTIALS` environment variable to be set.
 * If not configured, it logs a warning and returns `null`, allowing notifications to be skipped gracefully.
 *
 * @return The Slack credential ID string, or `null` if not configured.
 */
def getSlackCredentials() {
    if (!env.JENKINS_SLACK_CREDENTIALS) {
        echo "⚠️ WARNING: JENKINS_SLACK_CREDENTIALS not configured. Slack notifications will be skipped."
        return null
    }
    return env.JENKINS_SLACK_CREDENTIALS
}

/**
 * Retrieves the Slack channel name for notifications.
 *
 * This function checks for `JENKINS_SLACK_CHANNEL`. If not configured,
 * it logs a warning and returns `"#general"` as a default.
 *
 * @return The Slack channel name string.
 */
def getSlackChannel() {
    if (!env.JENKINS_SLACK_CHANNEL) {
        echo "⚠️ WARNING: JENKINS_SLACK_CHANNEL not configured. Using default channel '#general'."
        return '#general'
    }
    return env.JENKINS_SLACK_CHANNEL
}