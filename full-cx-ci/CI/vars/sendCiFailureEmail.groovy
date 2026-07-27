// vars/sendCiFailureEmail.groovy
//
// EXACT REPLICA OF: CustomerXP sendCiFailureEmail.groovy

def call(Map args = [:]) {
    def getDefaultsScript = loadSharedLibVarScript('getDefaults')
    def recipients = args.recipients?.trim() ?: ''
    if (!recipients && getDefaultsScript) {
        try { recipients = getDefaultsScript.getFailureEmailRecipients() } catch (Throwable t) { recipients = '' }
    }
    if (!recipients) { echo "⚠️ sendCiFailureEmail: no recipients configured — skipping"; return }

    def jobName     = env.JOB_NAME ?: 'unknown-job'
    def buildNumber = env.BUILD_NUMBER ?: '0'
    def result      = currentBuild.result ?: 'FAILURE'
    def buildUrl    = env.BUILD_URL ?: ''

    def subjectTpl = args.subject ?: (getDefaultsScript ? getDefaultsScript.getFailureEmailSubjectTemplate() : '')
    def bodyTpl    = args.body ?: (getDefaultsScript ? getDefaultsScript.getFailureEmailBodyTemplate() : '')

    def subject = (subjectTpl ?: "CI ${result}: {JOB_NAME} #{BUILD_NUMBER}")
        .replace('{JOB_NAME}', jobName).replace('{BUILD_NUMBER}', buildNumber)
        .replace('{RESULT}', result).replace('{BUILD_URL}', buildUrl)

    def body = (bodyTpl ?: "Build {RESULT} for {JOB_NAME} #{BUILD_NUMBER}\n\nURL: {BUILD_URL}")
        .replace('{JOB_NAME}', jobName).replace('{BUILD_NUMBER}', buildNumber)
        .replace('{RESULT}', result).replace('{BUILD_URL}', buildUrl)

    echo "📧 sendCiFailureEmail: sending to ${recipients}"
    try {
        emailext(to: recipients, subject: subject, body: body)
    } catch (Throwable t) {
        try {
            mail(to: recipients, subject: subject, body: body)
        } catch (Throwable t2) {
            echo "⚠️ sendCiFailureEmail: both emailext and mail failed: ${t2.message}"
        }
    }
}

return this
