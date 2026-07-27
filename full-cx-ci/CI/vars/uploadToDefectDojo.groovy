// vars/uploadToDefectDojo.groovy
//
// EXACT REPLICA OF: CustomerXP uploadToDefectDojo.groovy

import groovy.json.JsonSlurper

def call(Map config) {
    String moduleName      = config.moduleName ?: ''
    String scanType        = config.scanType ?: 'Dependency Track Finding Packaging Format (FPF) Export'
    String reportFile      = config.reportFile ?: ''
    String branch          = config.branch ?: 'main'
    String buildNumber     = config.buildNumber ?: env.BUILD_NUMBER ?: '0'
    String productName     = config.productName ?: env.DOJO_PRODUCT_NAME ?: 'CustomerXP'
    String engagementName  = config.engagementName ?: moduleName
    boolean failOnError    = config.containsKey('failOnError') ? (config.failOnError as boolean) : false
    String dojoUrl         = env.DOJO_URL?.trim() ?: ''

    if (!dojoUrl) {
        echo "⚠️ uploadToDefectDojo: DOJO_URL not set — skipping upload for ${moduleName}"
        return
    }
    if (!reportFile?.trim() || !fileExists(reportFile)) {
        echo "⚠️ uploadToDefectDojo: report file not found: ${reportFile}"
        if (failOnError) error "❌ uploadToDefectDojo: report file missing: ${reportFile}"
        return
    }

    echo "🛡️ uploadToDefectDojo: uploading ${moduleName} findings to DefectDojo"
    echo "   Product: ${productName}, Engagement: ${engagementName}, Branch: ${branch}"
    echo "   Report: ${reportFile}"

    withCredentials([string(credentialsId: 'defect-dojo-api-key', variable: 'DOJO_KEY')]) {
        def engagementId = ensureEngagement(dojoUrl, DOJO_KEY, productName, engagementName, branch, buildNumber)
        if (!engagementId) {
            echo "⚠️ uploadToDefectDojo: could not get/create engagement — skipping"
            return
        }
        def success = uploadScanReport([
            dojoUrl: dojoUrl, apiKey: DOJO_KEY, engagementId: engagementId,
            scanType: scanType, reportFile: reportFile, productName: productName,
            active: true, verified: true, closeOldFindings: false
        ])
        if (success) {
            echo "✅ uploadToDefectDojo: upload successful for ${moduleName}"
        } else {
            echo "⚠️ uploadToDefectDojo: upload failed for ${moduleName}"
            if (failOnError) error "❌ uploadToDefectDojo: upload failed"
        }
    }
}

def ensureEngagement(String dojoUrl, String apiKey, String productName, String engName, String branch, String buildNumber) {
    try {
        def productId = getOrCreateProduct(dojoUrl, apiKey, productName)
        if (!productId) return null
        def existingId = getEngagement(dojoUrl, apiKey, productId as int, engName)
        if (existingId) return existingId
        def today = new Date().format('yyyy-MM-dd')
        def conn = new URL("${dojoUrl}/api/v2/engagements/").openConnection()
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', "Token ${apiKey}")
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.doOutput = true
        def body = """{"name":"${engName}","product":${productId},"target_start":"${today}","target_end":"${today}","engagement_type":"CI/CD","status":"In Progress","branch_tag":"${branch}","build_id":"${buildNumber}"}"""
        conn.outputStream.write(body.bytes)
        def code = conn.responseCode
        if (code in [200, 201]) {
            def resp = new JsonSlurper().parseText(conn.inputStream.text)
            return resp.id
        }
    } catch (Throwable t) {
        echo "⚠️ uploadToDefectDojo.ensureEngagement: ${t.message}"
    }
    return null
}

def getOrCreateProduct(String dojoUrl, String apiKey, String productName) {
    try {
        def encoded = URLEncoder.encode(productName, 'UTF-8')
        def conn = new URL("${dojoUrl}/api/v2/products/?name=${encoded}").openConnection()
        conn.setRequestProperty('Authorization', "Token ${apiKey}")
        if (conn.responseCode == 200) {
            def resp = new JsonSlurper().parseText(conn.inputStream.text)
            if (resp.count > 0) return resp.results[0].id
        }
        def createConn = new URL("${dojoUrl}/api/v2/products/").openConnection()
        createConn.requestMethod = 'POST'
        createConn.setRequestProperty('Authorization', "Token ${apiKey}")
        createConn.setRequestProperty('Content-Type', 'application/json')
        createConn.doOutput = true
        def body = """{"name":"${productName}","description":"CustomerXP Application Modules","prod_type":1}"""
        createConn.outputStream.write(body.bytes)
        if (createConn.responseCode in [200, 201]) {
            return new JsonSlurper().parseText(createConn.inputStream.text).id
        }
    } catch (Throwable t) {
        echo "⚠️ uploadToDefectDojo.getOrCreateProduct: ${t.message}"
    }
    return null
}

def getEngagement(String dojoUrl, String apiKey, int productId, String engName) {
    try {
        def encoded = URLEncoder.encode(engName, 'UTF-8')
        def conn = new URL("${dojoUrl}/api/v2/engagements/?product=${productId}&name=${encoded}").openConnection()
        conn.setRequestProperty('Authorization', "Token ${apiKey}")
        if (conn.responseCode == 200) {
            def resp = new JsonSlurper().parseText(conn.inputStream.text)
            if (resp.count > 0) return resp.results[0].id
        }
    } catch (Throwable t) {
        echo "⚠️ uploadToDefectDojo.getEngagement: ${t.message}"
    }
    return null
}

def uploadScanReport(Map config) {
    try {
        def boundary = "----FormBoundary${UUID.randomUUID().toString().replaceAll('-', '')}"
        def conn = new URL("${config.dojoUrl}/api/v2/import-scan/").openConnection()
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', "Token ${config.apiKey}")
        conn.setRequestProperty('Content-Type', "multipart/form-data; boundary=${boundary}")
        conn.doOutput = true
        def os = conn.outputStream
        def addField = { name, value ->
            os.write("--${boundary}\r\nContent-Disposition: form-data; name=\"${name}\"\r\n\r\n${value}\r\n".bytes)
        }
        addField('scan_type', config.scanType)
        addField('product_name', config.productName)
        addField('engagement', config.engagementId.toString())
        addField('active', config.active.toString())
        addField('verified', config.verified.toString())
        addField('close_old_findings', config.closeOldFindings.toString())
        def fileBytes = new File(config.reportFile).bytes
        os.write("--${boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"report\"\r\nContent-Type: application/octet-stream\r\n\r\n".bytes)
        os.write(fileBytes)
        os.write("\r\n--${boundary}--\r\n".bytes)
        os.flush()
        return conn.responseCode in [200, 201]
    } catch (Throwable t) {
        echo "⚠️ uploadToDefectDojo.uploadScanReport: ${t.message}"
        return false
    }
}

return this
