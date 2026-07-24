/**
 * Sends pipeline failure email using Jenkins globals (no hardcoded recipients or copy).
 *
 * Required: JENKINS_FAILURE_EMAIL_RECIPIENTS
 * Optional: JENKINS_FAILURE_EMAIL_SUBJECT, JENKINS_FAILURE_EMAIL_BODY
 *           Placeholders: {JOB_NAME}, {BUILD_NUMBER}, {RESULT}, {BUILD_URL}
 */
def call(Map args = [:]) {
    def getDefaultsScript = loadSharedLibVarScript('getDefaults')
    if (!getDefaultsScript) {
        error '❌ sendCiFailureEmail: getDefaults could not be loaded from the shared library.'
    }

    def recipients = (args.recipients ?: getDefaultsScript.getFailureEmailRecipients())
        .toString()
        .split(/[,;\s]+/)
        .collect { it.trim() }
        .findAll { it }
        .join(',')

    if (!recipients) {
        echo '⚠️ sendCiFailureEmail: JENKINS_FAILURE_EMAIL_RECIPIENTS resolved empty — skipping.'
        return
    }

    def jobName = args.jobName ?: env.JOB_NAME ?: ''
    def buildNumber = args.buildNumber ?: env.BUILD_NUMBER ?: ''
    def buildUrl = args.buildUrl ?: env.BUILD_URL ?: ''
    def result = args.result ?: currentBuild.currentResult ?: ''

    def applyTemplate = { String template ->
        if (!template?.trim()) {
            return ''
        }
        return template
            .replace('{JOB_NAME}', jobName)
            .replace('{BUILD_NUMBER}', buildNumber)
            .replace('{RESULT}', result)
            .replace('{BUILD_URL}', buildUrl)
    }

    def subjectTemplate = args.subject?.toString()?.trim() ?: getDefaultsScript.getFailureEmailSubjectTemplate()
    def bodyTemplate = args.body?.toString()?.trim() ?: getDefaultsScript.getFailureEmailBodyTemplate()

    def subject = applyTemplate(subjectTemplate)
    if (!subject) {
        subject = "${jobName} #${buildNumber} ${result}".trim()
    }

    def body = applyTemplate(bodyTemplate)
    if (!body) {
        body = """Job: ${jobName}
Build: #${buildNumber}
Result: ${result}
Console: ${buildUrl}
"""
    }

    try {
        emailext(
            to: recipients,
            subject: subject,
            body: body,
            mimeType: 'text/plain'
        )
        echo "✅ Failure notification email sent to: ${recipients}"
    } catch (Throwable t) {
        try {
            mail(
                to: recipients,
                subject: subject,
                body: body
            )
            echo "✅ Failure notification sent via mail() to: ${recipients}"
        } catch (Throwable t2) {
            echo "⚠️ sendCiFailureEmail: could not send email (configure SMTP / Email Extension): ${t.message}; mail(): ${t2.message}"
        }
    }
}

return this
