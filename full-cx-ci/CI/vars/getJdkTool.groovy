// vars/getJdkTool.groovy
//
// EXACT REPLICA OF: CustomerXP getJdkTool.groovy
//
// PURPOSE:
// Maps a JDK label string (e.g., "jdk-17") to the Jenkins-configured
// JDK tool installation name.
//
// CustomerXP has multiple JDK tool installations configured in:
// Manage Jenkins → Global Tool Configuration → JDK installations
//
// ADAPTED FOR LOCAL JENKINS:
// Our Docker Jenkins uses the system Java — we return 'JAVA_HOME'
// placeholder and let the build use whatever Java is on PATH.
// The same method signature and return behavior is preserved.

def call(String jdkVersion) {
    if (!jdkVersion?.trim()) {
        echo "⚠️ getJdkTool: empty jdkVersion — returning default"
        return 'jdk-default'
    }

    def v = jdkVersion.trim().toLowerCase()

    // Map JDK label → Jenkins tool installation name
    // These must match names configured in Manage Jenkins → Tools → JDK
    // For local Docker Jenkins, we use 'jdk-default' (system Java)
    def toolMap = [
        'jdk-17'       : 'jdk-default',
        'jdk-21'       : 'jdk-default',
        'jdk-11'       : 'jdk-default',
        'jdk-8'        : 'jdk-default',
        'jdk17'        : 'jdk-default',
        'jdk21'        : 'jdk-default',
        'jdk11'        : 'jdk-default',
        'jdk8'         : 'jdk-default',
        'jdk-default'  : 'jdk-default',
    ]

    def toolName = toolMap[v] ?: 'jdk-default'
    return toolName
}

return this
