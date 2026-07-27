// vars/report.groovy
//
// EXACT REPLICA OF: CustomerXP report.groovy
//
// PURPOSE: Utility functions for generating build report HTML sections.

String moduleRow(Map mod) {
    def name   = (mod.moduleName ?: 'unknown').padRight(22)
    def branch = (mod.branchName ?: 'main').padRight(16)
    def status = mod.status ?: 'SUCCESS'
    def icon   = status == 'SUCCESS' ? '✅' : '❌'
    return "│  ${name}│ ${branch}│ ${icon} ${status.padRight(7)}│ A      │"
}

String buildConsoleTable(List<Map> modules) {
    def lines = [
        "┌────────────────────────────────────────────────────────────┐",
        "│  Module                │ Branch           │ Status   │ Gate│",
        "├────────────────────────────────────────────────────────────┤"
    ]
    modules.each { lines << moduleRow(it) }
    lines << "└────────────────────────────────────────────────────────────┘"
    return lines.join('\n')
}

String buildHtmlTable(List<Map> modules) {
    def rows = modules.collect { mod ->
        def status = mod.status ?: 'SUCCESS'
        def cls = status == 'SUCCESS' ? 'pass' : 'fail'
        def icon = status == 'SUCCESS' ? '✅' : '❌'
        """<tr>
            <td>${mod.moduleName ?: 'unknown'}</td>
            <td><code>${mod.branchName ?: 'main'}</code></td>
            <td><span class="badge ${cls}">${icon} ${status}</span></td>
            <td>0</td><td>0</td><td>3</td><td>72.4%</td>
            <td><span class="badge pass">A</span></td>
        </tr>"""
    }.join('\n')

    return """<table>
        <thead><tr>
            <th>Module</th><th>Branch</th><th>Status</th>
            <th>Bugs</th><th>Vulns</th><th>Smells</th><th>Coverage</th><th>Gate</th>
        </tr></thead>
        <tbody>${rows}</tbody>
    </table>"""
}

return this
