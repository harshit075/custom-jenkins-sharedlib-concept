// vars/getJDKChoices.groovy

/**
 * Returns JDK tool names for pipeline defaults (e.g. {@code getDefaults.getDefaultJDKs()}).
 * Values come from {@code env.CX_JDK_VERSION_OPTIONS} (comma-separated), same as
 * {@code final-cipipeline.jenkinsfile} / {@code release-cipipeline.jenkinsfile} parameter {@code jdk_version}.
 * Names must match Jenkins → Manage Jenkins → Tools → JDK installations.
 *
 * @return A List of strings (e.g. "jdk-17", "jdk-21").
 */
def call() {
    List<String> jdks = (env.CX_JDK_VERSION_OPTIONS ?: '')
        .split(',')
        .collect { it.trim() }
        .findAll { it }
    if (jdks.isEmpty()) {
        error(
            '❌ getJDKChoices: CX_JDK_VERSION_OPTIONS is not set or is empty. ' +
            'Set it in Jenkins global or job environment (comma-separated JDK tool names, e.g. jdk-21,jdk-17,jdk-11,jdk-8).'
        )
    }
    return jdks
}
