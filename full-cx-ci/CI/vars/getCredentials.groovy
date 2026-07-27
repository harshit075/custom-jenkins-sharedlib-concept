// vars/getCredentials.groovy
//
// EXACT REPLICA OF: CustomerXP getCredentials.groovy
//
// PURPOSE:
// Central store for resolving Jenkins credential IDs.
// Never returns actual secrets — only credential IDs (strings).
// The actual secret injection happens via withCredentials() in the build scripts.

def getGitCredentials() {
    def v = env.CX_GIT_HTTP_CREDENTIALS_ID?.trim()
    if (!v) error "❌ getCredentials.getGitCredentials: CX_GIT_HTTP_CREDENTIALS_ID is not set."
    return v
}

def getGitSSHCredentials() {
    def v = env.CX_GIT_SSH_CREDENTIALS_ID?.trim()
    if (!v) {
        echo "⚠️ getCredentials: CX_GIT_SSH_CREDENTIALS_ID not set — falling back to HTTP credentials"
        return getGitCredentials()
    }
    return v
}

def getBuildCredentials() {
    def v = env.CX_BUILD_CREDENTIALS_ID?.trim() ?: env.CX_GIT_HTTP_CREDENTIALS_ID?.trim()
    if (!v) error "❌ getCredentials.getBuildCredentials: CX_BUILD_CREDENTIALS_ID or CX_GIT_HTTP_CREDENTIALS_ID must be set."
    return v
}

def getSlackCredentials() {
    def v = env.CX_SLACK_CREDENTIALS_ID?.trim()
    if (!v) {
        echo "⚠️ getCredentials: CX_SLACK_CREDENTIALS_ID not set — Slack notifications disabled"
        return null
    }
    return v
}

def getSlackChannel() {
    def v = env.CX_SLACK_CHANNEL?.trim()
    if (!v) {
        echo "⚠️ getCredentials: CX_SLACK_CHANNEL not set — defaulting to #general"
        return '#general'
    }
    return v
}

return this
