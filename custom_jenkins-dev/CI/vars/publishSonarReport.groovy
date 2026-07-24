// vars/publishSonarReport.groovy

/**
 * Publishes a single "Sonar Report" page on the Jenkins build with all SonarQube metrics
 * and quality gate pass/fail for every scanned module.
 *
 * Fetches data from SonarQube Web API (measures + quality gate status), builds one HTML report,
 * and publishes it via publishHTML so users see it as "Sonar Report" on the build page.
 *
 * @param modules List of maps with keys: moduleName (String), branchName (String).
 *               Project key from {@code vars/sonarProjectKey.groovy} (same as {@code getSonarCommand}).
 * @param sonarHostUrl (optional) SonarQube base URL. If omitted, JENKINS_SONAR_URL or SONAR_HOST_URL is required.
 *               API calls use the same auth as {@code toolSonarqube.waitForQualityGateViaApi}: HTTP Basic {@code curl -u "\$SONAR_TOKEN:"} (user tokens; Bearer is not used).
 * @param credentialsId (optional) Secret text credential for Sonar. If omitted, JENKINS_SONAR_TOKEN_CREDENTIAL_ID is required.
 * @param reportName (optional) Display name for the report link. Defaults to 'Sonar Report'.
 * @param failOnError (optional) If true, fail build when API/report generation fails. Defaults to false.
 *
 * <p>Metrics requested from SonarQube {@code /api/measures/component} come from env {@code CX_SONAR_REPORT_METRICS}
 * (comma-separated Sonar metric keys). If unset, defaults to {@code coverage,bugs,vulnerabilities,code_smells,duplicated_lines_density,ncloc}.
 */
def call(Map params) {
    def modules = params.modules ?: []
    def reportName = params.reportName ?: 'Sonar Report'
    def failOnError = params.failOnError != null ? params.failOnError : false

    // Respect global config switch for security tools (Sonar is part of that)
    def getDefaultsScript = loadSharedLibVarScript('getDefaults')
    if (getDefaultsScript != null && !getDefaultsScript.getRunSecurityTools()) {
        echo "⏭️ publishSonarReport: Skipping (security tools disabled via JENKINS_RUN_SECURITY_TOOLS)."
        return
    }
    def secCat = loadSharedLibVarScript('securityToolsCatalog')
    if (secCat != null && !secCat.isToolEnabled('jenkins_sonar_html_report')) {
        echo '⏭️ publishSonarReport: Skipping (disabled in vars/securityToolsCatalog.groovy — jenkins_sonar_html_report).'
        return
    }

    def sonarHostUrl = params.sonarHostUrl?.toString()?.trim() ?: env.JENKINS_SONAR_URL?.toString()?.trim() ?: env.SONAR_HOST_URL?.toString()?.trim()
    def credentialsId = params.credentialsId?.toString()?.trim() ?: env.JENKINS_SONAR_TOKEN_CREDENTIAL_ID?.toString()?.trim()
    if (!sonarHostUrl) {
        error("❌ publishSonarReport: set sonarHostUrl parameter or global env JENKINS_SONAR_URL / SONAR_HOST_URL (see vars/cxPipelineConfig.groovy).")
    }
    if (!credentialsId) {
        error("❌ publishSonarReport: set credentialsId parameter or JENKINS_SONAR_TOKEN_CREDENTIAL_ID env.")
    }

    if (sonarHostUrl.endsWith('/')) {
        sonarHostUrl = sonarHostUrl.substring(0, sonarHostUrl.length() - 1)
    }

    if (modules.isEmpty()) {
        echo "⚠️ publishSonarReport: No modules provided. Skipping Sonar Report."
        return
    }

    def sonarProjectKeyScript = loadSharedLibVarScript('sonarProjectKey')
    if (!sonarProjectKeyScript) {
        error('❌ sonarProjectKey could not be loaded from the shared library.')
    }
    def branchKeyMsg = '❌ publishSonarReport: module branchName must be set for each module.'

    def projectKeys = []
    modules.each { m ->
        def name = m.moduleName ?: m.get('moduleName')
        def branch = m.branchName ?: m.get('branchName') ?: 'develop'
        if (!name) return
        def key = sonarProjectKeyScript.getSonarProjectKey(name.toString(), branch?.toString(), branchKeyMsg)
        projectKeys << [ projectKey: key, moduleName: name, branchName: branch ]
    }

    if (projectKeys.isEmpty()) {
        echo "⚠️ publishSonarReport: No valid module entries. Skipping Sonar Report."
        return
    }

    echo "--- PHASE: Publishing Sonar Report (${projectKeys.size()} module(s)) ---"

    def defaultSonarMetricKeys = 'coverage,bugs,vulnerabilities,code_smells,duplicated_lines_density,ncloc'
    List<String> sonarMetricKeysList = (env.CX_SONAR_REPORT_METRICS ?: defaultSonarMetricKeys)
        .split(',')
        .collect { it.trim() }
        .findAll { it }
    if (sonarMetricKeysList.isEmpty()) {
        sonarMetricKeysList = defaultSonarMetricKeys.split(',').collect { it.trim() }
    }
    def metricKeysForApi = sonarMetricKeysList.join(',')

    def reportDir = "sonar-report-${env.BUILD_NUMBER ?: 'report'}"
    def reportFile = "sonar-report.html"

    try {
        def rowDataList = []

        dir(reportDir) {
            withCredentials([string(credentialsId: credentialsId, variable: 'SONAR_TOKEN')]) {
                for (def entry in projectKeys) {
                    def key = entry.projectKey
                    def moduleName = entry.moduleName
                    def branchName = entry.branchName
                    def qgPass = null
                    def qgStatusHtml = '—'
                    def coverage = '—'
                    def bugs = '—'
                    def vulnerabilities = '—'
                    def codeSmells = '—'
                    def duplication = '—'
                    def ncloc = '—'
                    def keyEnc = java.net.URLEncoder.encode(key, 'UTF-8')

                    try {
                        def qgResp = sh(
                            script: """#!/bin/bash
set +e
curl -s -S -w '\\n%{http_code}' -u "\${SONAR_TOKEN}:" "${sonarHostUrl}/api/qualitygates/project_status?projectKey=${keyEnc}"
""",
                            returnStdout: true
                        ).trim()
                        def qgParts = qgResp.split('\n')
                        def qgCode = qgParts.length > 1 ? qgParts[qgParts.length - 1] : '000'
                        if (qgCode == '200' && qgParts.length >= 1) {
                            def qgBody = qgParts.length > 1 ? qgParts[0..qgParts.length - 2].join('\n') : ''
                            def qgJson = new groovy.json.JsonSlurper().parseText(qgBody)
                            if (qgJson?.projectStatus?.status == 'OK') {
                                qgPass = true
                                qgStatusHtml = '<span class="badge badge-pass">Pass</span>'
                            } else {
                                qgPass = false
                                qgStatusHtml = '<span class="badge badge-fail">Fail</span>'
                            }
                        } else {
                            def prev = qgParts.length > 1 ? qgParts[0..qgParts.length - 2].join('\n') : qgResp
                            echo "⚠️ publishSonarReport: quality gate API HTTP ${qgCode} for projectKey=${key}. Response preview: ${prev.take(240)}"
                        }
                    } catch (Exception e) {
                        echo "⚠️ Could not fetch quality gate for ${key}: ${e.message}"
                    }

                    try {
                        def measuresResp = sh(
                            script: """#!/bin/bash
set +e
curl -s -S -w '\\n%{http_code}' -u "\${SONAR_TOKEN}:" "${sonarHostUrl}/api/measures/component?component=${keyEnc}&metricKeys=${metricKeysForApi}"
""",
                            returnStdout: true
                        ).trim()
                        def mParts = measuresResp.split('\n')
                        def mCode = mParts.length > 1 ? mParts[mParts.length - 1] : '000'
                        if (mCode == '200' && mParts.length >= 1) {
                            def mBody = mParts.length > 1 ? mParts[0..mParts.length - 2].join('\n') : ''
                            def mJson = new groovy.json.JsonSlurper().parseText(mBody)
                            def component = mJson?.component
                            if (component?.measures) {
                                component.measures.each { me ->
                                    def val = me.value != null ? me.value : (me.period?.value != null ? me.period.value : '—')
                                    switch (me.metric) {
                                        case 'coverage': coverage = val; break
                                        case 'bugs': bugs = val; break
                                        case 'vulnerabilities': vulnerabilities = val; break
                                        case 'code_smells': codeSmells = val; break
                                        case 'duplicated_lines_density': duplication = val; break
                                        case 'ncloc': ncloc = val; break
                                    }
                                }
                            }
                        } else {
                            def prevM = mParts.length > 1 ? mParts[0..mParts.length - 2].join('\n') : measuresResp
                            echo "⚠️ publishSonarReport: measures API HTTP ${mCode} for component=${key}. Response preview: ${prevM.take(240)}"
                        }
                    } catch (Exception e) {
                        echo "⚠️ Could not fetch measures for ${key}: ${e.message}"
                    }

                    rowDataList << [
                        moduleName: moduleName,
                        branchName: branchName,
                        key: key,
                        keyEnc: keyEnc,
                        qgPass: qgPass,
                        qgStatusHtml: qgStatusHtml,
                        coverage: coverage,
                        bugs: bugs,
                        vulnerabilities: vulnerabilities,
                        codeSmells: codeSmells,
                        duplication: duplication,
                        ncloc: ncloc
                    ]
                }
            }

            def hasData = rowDataList.any { it.qgPass != null || it.coverage != '—' }

            // Build summary for Jenkins build description (shown on job/main page)
            def summaryParts = []
            rowDataList.each { row ->
                def parts = []
                if (row.qgPass != null) parts << (row.qgPass ? 'Pass' : 'Fail')
                if (row.coverage != '—') parts << "Coverage ${row.coverage}%"
                if (row.bugs != '—') parts << "Bugs ${row.bugs}"
                if (row.vulnerabilities != '—') parts << "Vuln ${row.vulnerabilities}"
                if (row.codeSmells != '—') parts << "Smells ${row.codeSmells}"
                if (parts) summaryParts << "${row.moduleName}: ${parts.join(' | ')}"
            }
            if (summaryParts) {
                def desc = summaryParts.join(' &nbsp; • &nbsp; ')
                if (desc.length() > 350) desc = desc.substring(0, 347) + '…'
                currentBuild.description = "<div style='font-size:12px;line-height:1.4;'><strong>Sonar</strong> ${desc}</div>"
                echo "✅ Build description set with Sonar summary (visible on job page)."
            }

            def toFloatSafe = { v ->
                if (v == null || v == '—') return null
                try {
                    return (v instanceof Number) ? v.toFloat() : Float.parseFloat(v.toString().replaceAll('[^0-9.]', ''))
                } catch (Exception e) { return null }
            }
            def toIntSafe = { v ->
                if (v == null || v == '—') return 0
                try {
                    return (v instanceof Number) ? v.intValue() : Integer.parseInt(v.toString().replaceAll('[^0-9]', ''))
                } catch (Exception e) { return 0 }
            }
            def htmlRows = rowDataList.collect { row ->
                def covPct = toFloatSafe(row.coverage)
                def covBar = covPct != null ? "<div class='progress'><div class='progress-bar' style='width:${Math.min(100, covPct)}%'></div><span>${row.coverage}%</span></div>" : "<span class='na'>${row.coverage}</span>"
                def dupPct = toFloatSafe(row.duplication)
                def dupBar = dupPct != null ? "<div class='progress progress-dup'><div class='progress-bar progress-bar-dup' style='width:${Math.min(100, dupPct)}%'></div><span>${row.duplication}%</span></div>" : "<span class='na'>${row.duplication}</span>"
                def bugsCls = toIntSafe(row.bugs) > 0 ? 'metric-warn' : 'metric-ok'
                def vulnCls = toIntSafe(row.vulnerabilities) > 0 ? 'metric-warn' : 'metric-ok'
                def linkUrl = "${sonarHostUrl}/dashboard?id=${row.keyEnc}"
                def hrefAttr = linkUrl.replace('&', '&amp;')
                """
                <tr>
                  <td class="module-cell"><span class="module-name">${row.moduleName}</span><br/><span class="branch-name">${row.branchName}</span></td>
                  <td class="qg-cell">${row.qgStatusHtml}</td>
                  <td class="metric-cell">${covBar}</td>
                  <td class="metric-cell ${bugsCls}">${row.bugs}</td>
                  <td class="metric-cell ${vulnCls}">${row.vulnerabilities}</td>
                  <td class="metric-cell">${row.codeSmells}</td>
                  <td class="metric-cell">${dupBar}</td>
                  <td class="metric-cell ncloc">${row.ncloc}</td>
                  <td class="link-cell"><a href="${hrefAttr}" target="_blank" rel="noopener noreferrer" class="btn-link">Open in SonarQube ↗</a></td>
                </tr>
                """
            }.join('')

            // Summary stats for the structured summary table
            def totalBugs = rowDataList.sum { toIntSafe(it.bugs) } ?: 0
            def totalVuln = rowDataList.sum { toIntSafe(it.vulnerabilities) } ?: 0
            def totalSmells = rowDataList.sum { toIntSafe(it.codeSmells) } ?: 0
            def anyFail = rowDataList.any { it.qgPass == false }
            def summaryQgText = anyFail ? 'Fail' : 'Pass'
            def summaryQgClass = anyFail ? 'summary-val-fail' : 'summary-val-pass'
            def covList = rowDataList.findAll { it.coverage != '—' }.collect { it.coverage }
            def summaryCoverage = covList ? (covList.size() == 1 ? "${covList[0]}%" : covList.collect { "${it}%" }.join(', ')) : '—'

            def reportTemplate = loadSharedLibVarScript('report')
            if (!reportTemplate) {
                error('❌ report could not be loaded from the shared library (vars/report.groovy).')
            }
            def html = reportTemplate.renderSonarReport([
                jobName         : env.JOB_NAME ?: '?',
                buildNumber     : env.BUILD_NUMBER?.toString() ?: '?',
                hasData         : hasData,
                summaryQgClass  : summaryQgClass,
                summaryQgText   : summaryQgText,
                summaryCoverage : summaryCoverage,
                totalBugs       : totalBugs,
                totalVuln       : totalVuln,
                totalSmells     : totalSmells,
                moduleCount     : rowDataList.size(),
                htmlRows        : htmlRows
            ])
            writeFile file: reportFile, text: html
        }

        publishHTML([
            reportDir: reportDir,
            reportFiles: reportFile,
            reportName: reportName,
            keepAll: true
        ])
        echo "✅ Sonar Report published. View it on the build page as \"${reportName}\"."
    } catch (Exception e) {
        echo "⚠️ Failed to publish Sonar Report: ${e.message}"
        if (failOnError) {
            throw e
        }
    }
}
// END of publishSonarReport.groovy - do not duplicate this file
