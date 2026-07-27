// vars/toolSbomJacoco.groovy
//
// EXACT REPLICA OF: CustomerXP toolSbomJacoco.groovy
//
// PURPOSE:
// Handles CycloneDX SBOM generation and JaCoCo coverage reporting.
// For local Jenkins: simulates SBOM generation since no Maven/Gradle available.

Object securityToolsCommonScript() { loadSharedLibVarScript('securityToolsCommon') }

void runBranch(Map args) {
    String moduleName    = args.moduleName
    String workspacePath = args.workspacePath ?: '.'
    String jdkVersion    = args.jdkVersion ?: 'jdk-17'
    Map cfg              = args.cfg ?: [:]

    echo "📋 toolSbomJacoco: generating SBOM for ${moduleName}..."
    dir(workspacePath) {
        // Check if a real bom.xml/bom.json already exists from Maven CycloneDX plugin
        def realSbom = sh(
            script: 'find . -name "bom.xml" -o -name "bom.json" 2>/dev/null | head -1',
            returnStdout: true
        ).trim()

        if (realSbom) {
            echo "✅ SBOM found: ${realSbom}"
        } else {
            // Simulate — generate a minimal CycloneDX SBOM JSON
            echo "   [SIMULATED] No real SBOM found — generating minimal CycloneDX JSON"
            def sbomContent = """{
  "bomFormat": "CycloneDX",
  "specVersion": "1.4",
  "serialNumber": "urn:uuid:${UUID.randomUUID()}",
  "version": 1,
  "metadata": {
    "timestamp": "${new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")}",
    "component": {
      "type": "library",
      "name": "${moduleName}",
      "version": "1.0-SNAPSHOT"
    }
  },
  "components": []
}"""
            writeFile file: 'bom.json', text: sbomContent
            echo "✅ SBOM generated: bom.json (simulated CycloneDX 1.4)"
        }

        // JaCoCo coverage report
        echo "📊 toolSbomJacoco: checking JaCoCo coverage..."
        def jacocoReport = sh(
            script: 'find . -name "index.html" -path "*/jacoco/*" 2>/dev/null | head -1',
            returnStdout: true
        ).trim()

        if (jacocoReport) {
            echo "✅ JaCoCo report found: ${jacocoReport}"
            try {
                publishHTML(target: [
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: jacocoReport.replace('/index.html', ''),
                    reportFiles: 'index.html',
                    reportName: "JaCoCo - ${moduleName}"
                ])
            } catch (Throwable t) {
                echo "⚠️ Could not publish JaCoCo HTML: ${t.message}"
            }
        } else {
            echo "   No JaCoCo report found (no Maven/Gradle compile step ran)"
            echo "   Coverage: 72.4% (simulated)"
        }
    }
}

return this
