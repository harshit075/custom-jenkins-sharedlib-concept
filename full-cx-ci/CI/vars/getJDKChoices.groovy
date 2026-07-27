// vars/getJDKChoices.groovy
//
// EXACT REPLICA OF: CustomerXP getJDKChoices.groovy
//
// PURPOSE:
// Returns the list of available JDK tool names for pipeline parameter dropdowns.
// Used by registerNightlyCiJobParameters and the Jenkinsfile parameter registration.

def call() {
    def v = env.CX_JDK_VERSION_OPTIONS?.trim()
    if (!v) error "❌ getJDKChoices: CX_JDK_VERSION_OPTIONS is not set."
    def list = v.split(',').collect { it.trim() }.findAll { it }
    if (!list) error "❌ getJDKChoices: CX_JDK_VERSION_OPTIONS is empty after parsing."
    return list
}

return this
