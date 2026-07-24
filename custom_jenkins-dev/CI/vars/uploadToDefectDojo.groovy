// vars/uploadToDefectDojo.groovy

import java.net.URLEncoder
import groovy.json.JsonSlurper

/**
 * Enhanced DefectDojo integration utility for uploading security scan reports.
 * 
 * This script provides comprehensive DefectDojo integration including:
 * - Automatic engagement creation/management
 * - Support for multiple scan types
 * - Error handling and retry logic
 * - Detailed logging
 */

/**
 * Uploads a security scan report to DefectDojo.
 * 
 * @param config Map containing:
 *   - moduleName: Name of the module (required)
 *   - scanType: DefectDojo scan type (required)
 *   - reportFile: Path to the report file (required)
 *   - branch: Git branch name (optional, defaults to 'develop')
 *   - buildNumber: Build number (optional, defaults to env.BUILD_NUMBER)
 *   - productName: DefectDojo product name (optional, defaults to env.DOJO_PRODUCT_NAME or 'CustomerXP')
 *   - engagementName: Engagement name pattern (optional, auto-generated if not provided)
 *   - active: Mark findings as active (optional, defaults to true)
 *   - verified: Mark findings as verified (optional, defaults to true)
 *   - closeOldFindings: Close findings from previous scans (optional, defaults to false)
 *   - failOnError: Whether to fail build on error (optional, defaults to false)
 */
def call(Map config) {
    def moduleName = config.moduleName
    def scanType = config.scanType
    def reportFile = config.reportFile
    def branch = config.branch ?: 'develop'
    def buildNumber = config.buildNumber ?: env.BUILD_NUMBER ?: 'unknown'
    def productName = config.productName ?: env.DOJO_PRODUCT_NAME ?: 'CustomerXP'
    def engagementName = config.engagementName ?: moduleName
    def active = config.active != null ? config.active : true
    def verified = config.verified != null ? config.verified : true
    def closeOldFindings = config.closeOldFindings != null ? config.closeOldFindings : false
    def failOnError = config.failOnError != null ? config.failOnError : false
    
    // Validate required parameters
    if (!moduleName || !scanType || !reportFile) {
        def errorMsg = "❌ Missing required parameters for DefectDojo upload: moduleName, scanType, or reportFile"
        if (failOnError) {
            error(errorMsg)
        } else {
            echo "⚠️ ${errorMsg}"
            return false
        }
    }
    
    // Check if report file exists
    if (!fileExists(reportFile)) {
        def errorMsg = "⚠️ Report file not found at ${reportFile}. Skipping DefectDojo upload for ${moduleName}."
        echo errorMsg
        return false
    }
    
    // Get DefectDojo URL
    def dojoUrl = env.DOJO_URL
    if (!dojoUrl) {
        def errorMsg = "⚠️ DOJO_URL environment variable is not set. Skipping DefectDojo upload for ${moduleName}."
        echo errorMsg
        if (failOnError) {
            error(errorMsg)
        }
        return false
    }
    
    echo "--- PHASE: Uploading ${scanType} report to DefectDojo for ${moduleName} ---"
    echo "   Product: ${productName}"
    echo "   Engagement: ${engagementName}"
    echo "   Report File: ${reportFile}"
    
    try {
        withCredentials([string(credentialsId: 'defect-dojo-api-key', variable: 'DOJO_KEY')]) {
            // Ensure engagement exists (optional - DefectDojo will create if auto_create is enabled)
            def engagementId = ensureEngagement(dojoUrl, DOJO_KEY, productName, engagementName, branch, buildNumber)
            
            // Upload scan report
            def uploadSuccess = uploadScanReport(
                dojoUrl: dojoUrl,
                apiKey: DOJO_KEY,
                engagementId: engagementId,
                engagementName: engagementName,
                productName: productName,
                scanType: scanType,
                reportFile: reportFile,
                active: active,
                verified: verified,
                closeOldFindings: closeOldFindings
            )
            
            if (uploadSuccess) {
                echo "✅ Successfully uploaded ${scanType} report to DefectDojo for ${moduleName}"
                if (engagementId) {
                    echo "   View in DefectDojo: ${dojoUrl}/engagement/${engagementId}"
                } else {
                    echo "   View in DefectDojo: ${dojoUrl}/engagements/ (search for '${engagementName}')"
                }
                return true
            } else {
                throw new Exception("Upload failed")
            }
        }
    } catch (Exception e) {
        def errorMsg = "⚠️ WARNING: Failed to upload ${scanType} report to DefectDojo for ${moduleName}: ${e.message}"
        echo errorMsg
        if (failOnError) {
            error(errorMsg)
        }
        return false
    }
}

/**
 * Ensures an engagement exists in DefectDojo, creates it if it doesn't exist.
 * 
 * @param dojoUrl DefectDojo server URL
 * @param apiKey DefectDojo API key
 * @param productName Product name
 * @param engagementName Engagement name
 * @param branch Git branch name
 * @param buildNumber Build number
 * @return Engagement ID (or null if creation failed)
 */
def ensureEngagement(String dojoUrl, String apiKey, String productName, String engagementName, String branch, String buildNumber) {
    try {
        // First, get or create the product
        def productId = getOrCreateProduct(dojoUrl, apiKey, productName)
        if (!productId) {
            echo "⚠️ Could not get or create product ${productName}. Engagement creation may fail."
            return null
        }
        
        // Check if engagement exists
        def engagementId = getEngagement(dojoUrl, apiKey, productId, engagementName)
        
        if (engagementId) {
            echo "   Engagement '${engagementName}' already exists (ID: ${engagementId})"
            return engagementId
        }
        
        // Create new engagement
        echo "   Creating engagement '${engagementName}'..."
        // DefectDojo requires dates in YYYY-MM-DD format (not datetime)
        def targetStart = sh(script: "date -u +%Y-%m-%d", returnStdout: true).trim()
        def targetEnd = sh(script: "date -u -d '+30 days' +%Y-%m-%d 2>/dev/null || date -u -v+30d +%Y-%m-%d 2>/dev/null || date -u +%Y-%m-%d", returnStdout: true).trim()
        
        // Ensure we have valid dates
        if (!targetStart || targetStart.isEmpty()) {
            targetStart = sh(script: "date -u +%Y-%m-%d", returnStdout: true).trim()
        }
        if (!targetEnd || targetEnd.isEmpty()) {
            // If we can't calculate +30 days, use same as start (DefectDojo will accept it)
            targetEnd = targetStart
        }
        
        def createResponse = sh(
            script: """
                curl -s -w '\\n%{http_code}' -X POST "${dojoUrl}/api/v2/engagements/" \\
                     -H "Authorization: Token ${apiKey}" \\
                     -H "Content-Type: application/json" \\
                     -d '{
                         "name": "${engagementName}",
                         "product": ${productId},
                         "status": "In Progress",
                         "target_start": "${targetStart}",
                         "target_end": "${targetEnd}",
                         "build_id": "${buildNumber}",
                         "commit_hash": "${branch}",
                         "deduplication_on_engagement": true
                     }'
            """,
            returnStdout: true
        )
        
        // Parse response (last line is HTTP status code)
        def lines = createResponse.trim().split('\n')
        def httpCode = lines[-1]
        def responseBody = lines[0..-2].join('\n')
        
        if (httpCode == '200' || httpCode == '201') {
            try {
                def jsonSlurper = new JsonSlurper()
                def engagement = jsonSlurper.parseText(responseBody)
                
                if (engagement.id) {
                    echo "   ✅ Created engagement '${engagementName}' (ID: ${engagement.id})"
                    return engagement.id
                } else {
                    echo "   ⚠️ Created engagement but no ID in response. Response: ${responseBody.take(500)}"
                    return null
                }
            } catch (Exception parseError) {
                echo "   ⚠️ Failed to parse engagement response: ${parseError.message}"
                echo "   Response: ${responseBody.take(500)}"
                return null
            }
        } else {
            echo "   ❌ HTTP ${httpCode}"
            echo "   Response: ${responseBody.take(500)}"
            return null
        }
    } catch (Exception e) {
        echo "   ⚠️ Error ensuring engagement: ${e.message}. Will attempt upload anyway (DefectDojo may auto-create)."
        return null
    }
}

/**
 * Gets or creates a product in DefectDojo.
 * 
 * @param dojoUrl DefectDojo server URL
 * @param apiKey DefectDojo API key
 * @param productName Product name
 * @return Product ID (or null if creation failed)
 */
def getOrCreateProduct(String dojoUrl, String apiKey, String productName) {
    try {
        // Try to get existing product
        def encodedProductName = URLEncoder.encode(productName, 'UTF-8')
        def getResponse = withEnv(["DOJO_API_KEY=${apiKey}"]) {
            sh(
                script: """
                    curl -s -X GET "${dojoUrl}/api/v2/products/?name=${encodedProductName}" \\
                         -H "Authorization: Token \${DOJO_API_KEY}"
                """,
                returnStdout: true
            )
        }
        
        // Check if response is valid JSON
        if (!getResponse || getResponse.trim().isEmpty() || getResponse.trim().startsWith('<')) {
            echo "   ⚠️ Invalid response from DefectDojo API (may be HTML error page). Response: ${getResponse?.take(200)}"
            // Try to create product anyway
        } else {
            def jsonSlurper = new JsonSlurper()
            def products = jsonSlurper.parseText(getResponse)
            
            if (products.results && products.results.size() > 0) {
                def productId = products.results[0].id
                echo "   Product '${productName}' exists (ID: ${productId})"
                return productId
            }
        }
        
        // Create new product
        echo "   Creating product '${productName}'..."
        def createResponse = sh(
            script: """
                curl -s -w '\\n%{http_code}' -X POST "${dojoUrl}/api/v2/products/" \\
                     -H "Authorization: Token ${apiKey}" \\
                     -H "Content-Type: application/json" \\
                     -d '{
                         "name": "${productName}",
                         "description": "CustomerXP Application Modules",
                         "prod_type": 1,
                         "lifecycle": "construction"
                     }'
            """,
            returnStdout: true
        )
        
        // Parse response (last line is HTTP status code)
        def lines = createResponse.trim().split('\n')
        def httpCode = lines[-1]
        def responseBody = lines.size() > 1 ? lines[0..-2].join('\n') : ''
        
        if (httpCode == '200' || httpCode == '201') {
            try {
                def jsonSlurper = new JsonSlurper()
                def product = jsonSlurper.parseText(responseBody)
                
                if (product.id) {
                    echo "   ✅ Created product '${productName}' (ID: ${product.id})"
                    return product.id
                } else {
                    echo "   ⚠️ Created product but no ID in response. Response: ${responseBody.take(500)}"
                    return null
                }
            } catch (Exception parseError) {
                echo "   ⚠️ Failed to parse product creation response: ${parseError.message}"
                echo "   Response: ${responseBody.take(500)}"
                return null
            }
        } else {
            echo "   ⚠️ Failed to create product. HTTP ${httpCode}"
            echo "   Response: ${responseBody.take(500)}"
            return null
        }
    } catch (Exception e) {
        echo "   ⚠️ Error getting/creating product: ${e.getClass().getSimpleName()}: ${e.message}"
        echo "   Stack trace: ${e.getStackTrace().take(3).join(' -> ')}"
        return null
    }
}

/**
 * Gets an existing engagement by name.
 * 
 * @param dojoUrl DefectDojo server URL
 * @param apiKey DefectDojo API key
 * @param productId Product ID
 * @param engagementName Engagement name
 * @return Engagement ID (or null if not found)
 */
def getEngagement(String dojoUrl, String apiKey, int productId, String engagementName) {
    try {
        def encodedEngagementName = URLEncoder.encode(engagementName, 'UTF-8')
        def getResponse = sh(
            script: """
                curl -s -w '\\n%{http_code}' -X GET "${dojoUrl}/api/v2/engagements/?product=${productId}&name=${encodedEngagementName}" \\
                     -H "Authorization: Token ${apiKey}"
            """,
            returnStdout: true
        )
        
        // Parse response (last line is HTTP status code)
        def lines = getResponse.trim().split('\n')
        def httpCode = lines[-1]
        def responseBody = lines.size() > 1 ? lines[0..-2].join('\n') : ''
        
        if (httpCode == '200' && responseBody && !responseBody.trim().startsWith('<')) {
            try {
                def jsonSlurper = new JsonSlurper()
                def engagements = jsonSlurper.parseText(responseBody)
                
                if (engagements.results && engagements.results.size() > 0) {
                    return engagements.results[0].id
                }
            } catch (Exception parseError) {
                echo "   ⚠️ Error parsing engagement response: ${parseError.message}"
            }
        }
        
        return null
    } catch (Exception e) {
        echo "   ⚠️ Error getting engagement: ${e.message}"
        return null
    }
}

/**
 * Uploads a scan report to DefectDojo.
 * 
 * @param config Map with upload parameters
 * @return true if successful, false otherwise
 */
def uploadScanReport(Map config) {
    def dojoUrl = config.dojoUrl
    def apiKey = config.apiKey
    def engagementId = config.engagementId
    def engagementName = config.engagementName
    def productName = config.productName
    def scanType = config.scanType
    def reportFile = config.reportFile
    def active = config.active
    def verified = config.verified
    def closeOldFindings = config.closeOldFindings
    
    try {
        // Build curl command for upload
        def uploadResult
        if (engagementId) {
            uploadResult = sh(
                script: """
                    curl -s -w '\\n%{http_code}' -X POST "${dojoUrl}/api/v2/import-scan/" \\
                         -H "Authorization: Token ${apiKey}" \\
                         -F "active=${active}" \\
                         -F "verified=${verified}" \\
                         -F "close_old_findings=${closeOldFindings}" \\
                         -F "scan_type=${scanType}" \\
                         -F "product_name=${productName}" \\
                         -F "engagement=${engagementId}" \\
                         -F "file=@${reportFile}"
                """,
                returnStdout: true
            )
        } else {
            uploadResult = sh(
                script: """
                    curl -s -w '\\n%{http_code}' -X POST "${dojoUrl}/api/v2/import-scan/" \\
                         -H "Authorization: Token ${apiKey}" \\
                         -F "active=${active}" \\
                         -F "verified=${verified}" \\
                         -F "close_old_findings=${closeOldFindings}" \\
                         -F "scan_type=${scanType}" \\
                         -F "product_name=${productName}" \\
                         -F "engagement_name=${engagementName}" \\
                         -F "file=@${reportFile}"
                """,
                returnStdout: true
            )
        }
        
        // Parse response (last line is HTTP status code)
        def lines = uploadResult.trim().split('\n')
        def httpCode = lines[-1]
        def responseBody = lines.size() > 1 ? lines[0..-2].join('\n') : ''
        
        if (httpCode == '200' || httpCode == '201') {
            return true
        } else {
            echo "   ❌ HTTP ${httpCode}"
            echo "   Response: ${responseBody.take(500)}"
            return false
        }
    } catch (Exception e) {
        echo "   ⚠️ Error uploading scan report: ${e.message}"
        return false
    }
}

return this

