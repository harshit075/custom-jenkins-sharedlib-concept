// vars/report.groovy
//
// HTML/CSS for the Jenkins "Sonar Report" page. Used by vars/publishSonarReport.groovy.

/**
 * Builds the full Sonar Report HTML document (inline CSS + summary + detail table body rows).
 *
 * @param args.jobName Jenkins job name (subtitle).
 * @param args.buildNumber Build number string.
 * @param args.hasData Whether to show the summary strip and hide the empty-state message.
 * @param args.summaryQgClass CSS class for summary quality gate cell.
 * @param args.summaryQgText Pass/Fail text for summary.
 * @param args.summaryCoverage Coverage summary cell text.
 * @param args.totalBugs Total bugs (summary row).
 * @param args.totalVuln Total vulnerabilities (summary row).
 * @param args.totalSmells Total code smells (summary row).
 * @param args.moduleCount Number of modules (summary row).
 * @param args.htmlRows Pre-rendered {@code <tr>...</tr>} rows for the main table.
 */
String renderSonarReport(Map args) {
    String jobName = args.jobName?.toString() ?: '?'
    String buildNumber = args.buildNumber?.toString() ?: '?'
    boolean hasData = args.containsKey('hasData') ? (args.hasData as boolean) : false
    String summaryQgClass = args.summaryQgClass?.toString() ?: 'summary-val-pass'
    String summaryQgText = args.summaryQgText?.toString() ?: '—'
    String summaryCoverage = args.summaryCoverage?.toString() ?: '—'
    int totalBugs = args.totalBugs != null ? (args.totalBugs as int) : 0
    int totalVuln = args.totalVuln != null ? (args.totalVuln as int) : 0
    int totalSmells = args.totalSmells != null ? (args.totalSmells as int) : 0
    int moduleCount = args.moduleCount != null ? (args.moduleCount as int) : 0
    String htmlRows = args.htmlRows?.toString() ?: ''

    return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Sonar Report</title>
  <style>
    * { box-sizing: border-box; }
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen-Sans, Ubuntu, sans-serif; margin: 0; padding: 24px; background: linear-gradient(180deg, #f0f4f8 0%, #e2e8f0 100%); min-height: 100vh; color: #334155; }
    .page-header { background: linear-gradient(135deg, #1e3a5f 0%, #2563eb 100%); color: white; padding: 24px 28px; border-radius: 12px; margin-bottom: 20px; box-shadow: 0 4px 14px rgba(30,58,95,0.25); }
    .page-header h1 { margin: 0 0 8px 0; font-size: 1.75rem; font-weight: 700; letter-spacing: -0.02em; }
    .page-header .subtitle { opacity: 0.95; font-size: 0.9rem; }
    .summary-table-wrap { background: white; border-radius: 12px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); overflow: hidden; border: 1px solid #e2e8f0; }
    .summary-table { width: 100%; border-collapse: collapse; }
    .summary-table th { text-align: left; padding: 12px 20px; font-size: 0.7rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: #64748b; background: #f8fafc; border-bottom: 1px solid #e2e8f0; width: 140px; }
    .summary-table td { padding: 12px 20px; font-size: 0.95rem; font-weight: 600; color: #1e293b; border-bottom: 1px solid #f1f5f9; }
    .summary-table tr:last-child td { border-bottom: none; }
    .summary-val-pass { color: #166534; }
    .summary-val-fail { color: #991b1b; }
    .summary-val-ok { color: #15803d; }
    .summary-val-warn { color: #b45309; }
    .report-card { background: white; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.08), 0 4px 12px rgba(0,0,0,0.04); overflow: hidden; }
    table { width: 100%; border-collapse: collapse; }
    thead { background: #1e293b; color: white; }
    th { padding: 14px 16px; text-align: left; font-size: 0.7rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em; }
    td { padding: 14px 16px; border-bottom: 1px solid #f1f5f9; font-size: 0.875rem; }
    tbody tr:hover { background: #f8fafc; }
    tbody tr:last-child td { border-bottom: none; }
    .module-cell { min-width: 160px; }
    .module-name { font-weight: 700; color: #1e293b; font-size: 0.95rem; }
    .branch-name { font-size: 0.75rem; color: #64748b; margin-top: 2px; display: inline-block; }
    .qg-cell { font-weight: 700; }
    .badge { display: inline-block; padding: 5px 12px; border-radius: 20px; font-size: 0.8rem; font-weight: 700; }
    .badge-pass { background: #dcfce7; color: #166534; }
    .badge-fail { background: #fee2e2; color: #991b1b; }
    .metric-cell { white-space: nowrap; }
    .metric-cell.metric-warn { color: #b45309; font-weight: 700; }
    .metric-cell.metric-ok { color: #15803d; font-weight: 600; }
    .metric-cell.ncloc { color: #475569; font-weight: 500; }
    .na { color: #94a3b8; }
    .progress { position: relative; height: 22px; background: #e2e8f0; border-radius: 11px; overflow: hidden; min-width: 70px; max-width: 100px; }
    .progress-bar { height: 100%; background: linear-gradient(90deg, #22c55e, #16a34a); border-radius: 11px; }
    .progress span { position: absolute; left: 0; right: 0; top: 0; bottom: 0; display: flex; align-items: center; justify-content: center; font-size: 0.7rem; font-weight: 700; color: #334155; text-shadow: 0 0 1px #fff; }
    .progress-dup .progress-bar-dup { background: linear-gradient(90deg, #f59e0b, #d97706); }
    .btn-link { display: inline-block; padding: 8px 14px; background: #2563eb; color: white !important; text-decoration: none; border-radius: 8px; font-size: 0.8rem; font-weight: 600; transition: background 0.2s; }
    .btn-link:hover { background: #1d4ed8; }
    .no-data { padding: 24px; text-align: center; color: #64748b; font-style: italic; }
    .legend { font-size: 0.7rem; color: #64748b; margin-top: 12px; padding: 8px 0; border-top: 1px solid #e2e8f0; }
  </style>
</head>
<body>
  <div class="page-header">
    <h1>📊 Sonar Report</h1>
    <p class="subtitle">Build: ${jobName} #${buildNumber} — Quality gate and metrics from SonarQube</p>
  </div>
  ${hasData ? """
  <div class="summary-table-wrap">
    <table class="summary-table">
      <tbody>
        <tr><th>Quality Gate</th><td class="${summaryQgClass}">${summaryQgText}</td></tr>
        <tr><th>Coverage</th><td>${summaryCoverage}</td></tr>
        <tr><th>Bugs</th><td class="${totalBugs > 0 ? 'summary-val-warn' : 'summary-val-ok'}">${totalBugs}</td></tr>
        <tr><th>Vulnerabilities</th><td class="${totalVuln > 0 ? 'summary-val-warn' : 'summary-val-ok'}">${totalVuln}</td></tr>
        <tr><th>Code Smells</th><td>${totalSmells}</td></tr>
        <tr><th>Modules</th><td>${moduleCount}</td></tr>
      </tbody>
    </table>
  </div>
  """ : ''}
  <div class="report-card">
    <table>
      <thead>
        <tr>
          <th>Module</th>
          <th>Quality Gate</th>
          <th>Coverage</th>
          <th>Bugs</th>
          <th>Vulnerabilities</th>
          <th>Code Smells</th>
          <th>Duplication</th>
          <th>NCLOC</th>
          <th>Link</th>
        </tr>
      </thead>
      <tbody>
        ${htmlRows}
      </tbody>
    </table>
    ${!hasData ? '<p class="no-data">No SonarQube data could be loaded. Ensure analysis has run and the project keys exist.</p>' : ''}
    <p class="legend">Pass = quality gate passed • Green = 0 issues • Orange = issues found • Click &quot;Open in SonarQube&quot; for full details.</p>
  </div>
</body>
</html>
"""
}
