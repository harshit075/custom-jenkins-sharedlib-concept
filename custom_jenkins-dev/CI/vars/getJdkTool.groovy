// vars/getJdkTool.groovy

/**
 * Resolves the Jenkins JDK tool name for pipeline JDK labels ({@code jdk-8}, {@code jdk-11}, …).
 * The “standard” allowlist is {@code env.CX_JDK_VERSION_OPTIONS} (comma-separated), aligned with
 * {@code getJDKChoices}, {@code final-cipipeline.jenkinsfile}, and Jenkins JDK installations.
 * Labels in that list pass through unchanged; any other label passes through as-is after a warning (identity map).
 *
 * @param jdkVersion Pipeline JDK id (must match JDK installation name unless you intentionally use a custom label).
 * @return Name passed to Jenkins {@code tool type: 'jdk'} — equals {@code jdkVersion.trim()} after validation.
 */
def call(String jdkVersion) {
    if (!jdkVersion?.toString()?.trim()) {
        error('❌ getJdkTool: jdkVersion must be set.')
    }
    def cleanVersion = jdkVersion.toString().trim()

    List<String> standardJdkLabels = (env.CX_JDK_VERSION_OPTIONS ?: '')
        .split(',')
        .collect { it.trim() }
        .findAll { it }
    if (standardJdkLabels.isEmpty()) {
        error(
            '❌ getJdkTool: CX_JDK_VERSION_OPTIONS is not set or is empty. ' +
            'Set it in Jenkins (same as getJDKChoices / pipeline jdk_version options).'
        )
    }

    if (standardJdkLabels.contains(cleanVersion)) {
        return cleanVersion
    }

    echo "⚠️ WARNING: JDK version '${cleanVersion}' is not in CX_JDK_VERSION_OPTIONS (${standardJdkLabels.join(', ')}). Using '${cleanVersion}' directly as the Jenkins JDK tool name. Ensure a JDK tool with this exact name exists under Manage Jenkins → Tools → JDK Installations."
    return cleanVersion
}
