// vars/toolSbomJacoco.groovy
// CycloneDX / JaCoCo: bash fragments for run_build.sh + Jenkins HTML reports.

Object securityToolsCommonScript() {
    loadSharedLibVarScript('securityToolsCommon')
}

boolean isGradleOnlyModule(String moduleName) {
    securityToolsCommonScript().isGradleOnlyModule(moduleName)
}

/**
 * @param sbomEnabled  {@code securityToolsCatalog} key {@code cyclonedx_sbom}
 * @param jacocoEnabled {@code securityToolsCatalog} key {@code jacoco}
 */
String getCompilationBlock(boolean sbomEnabled, boolean jacocoEnabled, String moduleName) {
    if (!sbomEnabled && !jacocoEnabled) {
        return """
                            echo "--- PHASE: Compilation (CycloneDX SBOM and JaCoCo both off in catalog) ---"
                            if echo ",\${CX_GRADLE_ONLY_MODULES:-}," | grep -Fq ",\${SMG_MODULE},"; then
                                ./gradlew clean test install || exit 1
                            else
                                fbuild.sh || exit 1
                            fi
                        """
    }
    List titleParts = []
    if (sbomEnabled) {
        titleParts << 'CycloneDX SBOM'
    }
    if (jacocoEnabled) {
        titleParts << 'JaCoCo'
    }
    String phaseTitle = titleParts.join(' + ')

    String gradleOnlyOneLiner
    if (sbomEnabled && jacocoEnabled) {
        gradleOnlyOneLiner = './gradlew clean test jacocoTestReport jacocoMergeReport combinedTestReport install cyclonedxBom || exit 1'
    } else if (sbomEnabled && !jacocoEnabled) {
        gradleOnlyOneLiner = './gradlew clean test install cyclonedxBom || exit 1'
    } else {
        gradleOnlyOneLiner = './gradlew clean test jacocoTestReport jacocoMergeReport combinedTestReport install || exit 1'
    }

    String gradleExtra = ''
    if (sbomEnabled || jacocoEnabled) {
        gradleExtra += """
                                if [ -f "gradlew" ] || command -v gradle &> /dev/null; then
                                    echo "Optional Gradle steps (catalog: sbom=${sbomEnabled}, jacoco=${jacocoEnabled})..."
                        """
        if (sbomEnabled) {
            gradleExtra += """
                                    if [ -f "gradlew" ]; then
                                        ./gradlew cyclonedxBom || echo "⚠️ Gradle cyclonedxBom failed, trying Maven..."
                                    else
                                        gradle cyclonedxBom || echo "⚠️ Gradle cyclonedxBom failed, trying Maven..."
                                    fi
                        """
        }
        if (jacocoEnabled) {
            gradleExtra += """
                                    if [ -f "gradlew" ]; then
                                        ./gradlew jacocoTestReport || echo "⚠️ Gradle jacocoTestReport failed or skipped"
                                    else
                                        gradle jacocoTestReport || echo "⚠️ Gradle jacocoTestReport failed or skipped"
                                    fi
                        """
        }
        gradleExtra += """
                                fi
                        """
    }

    String mavenSbom = sbomEnabled ? """
                                if [ ! -f "\${SMG_MODULE}_bom.json" ] && [ ! -f "bom.json" ] && [ ! -f "build/reports/bom.xml" ] && [ ! -f "target/bom.xml" ]; then
                                    echo "Generating SBOM using Maven CycloneDX plugin..."
                                    mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.0:makeAggregateBom -Dcyclonedx.skip=false -Dcyclonedx.outputFormat=xml -Dcyclonedx.outputName=bom || echo "⚠️ Maven SBOM generation failed"
                                fi
                        """ : ''

    String mavenJacoco = jacocoEnabled ? """
                                if [ -f "pom.xml" ] && [ ! -f "build/reports/jacoco/test/html/index.html" ]; then
                                    mvn jacoco:report || echo "⚠️ Maven jacoco:report failed or skipped"
                                fi
                        """ : ''

    return """
                            echo "--- PHASE: Compilation (${phaseTitle}) ---"
                            if echo ",\${CX_GRADLE_ONLY_MODULES:-}," | grep -Fq ",\${SMG_MODULE},"; then
                                ${gradleOnlyOneLiner}
                            else
                                fbuild.sh || exit 1
                        ${gradleExtra}${mavenSbom}${mavenJacoco}
                            fi
                        """
}

/** Tag-build compile fragment; {@code useFbuildPublishAll} true only from {@code buildModulesWithJDKReleaseTag} (release job). */
String getCompilationBlockTag(boolean tagBuildSecurityTools, boolean sbomEnabled, boolean jacocoEnabled, String releaseBranchName, String tagName, String moduleName, boolean useFbuildPublishAll = false) {
    String fbuildCompileCmd = useFbuildPublishAll ? 'fbuild.sh publish --all' : 'fbuild.sh'
    if (!tagBuildSecurityTools || (!sbomEnabled && !jacocoEnabled)) {
        return """
                                    echo "--- PHASE: Compilation (release branch ${releaseBranchName}, tag ${tagName}; CycloneDX/JaCoCo off or tag security tools off) ---"
                                    if echo ",\${CX_GRADLE_ONLY_MODULES:-}," | grep -Fq ",\${SMG_MODULE},"; then
                                        ./gradlew clean test install || exit 1
                                    else
                                        ${fbuildCompileCmd} || exit 1
                                    fi
                            """
    }
    List titleParts = []
    if (sbomEnabled) {
        titleParts << 'CycloneDX SBOM'
    }
    if (jacocoEnabled) {
        titleParts << 'JaCoCo'
    }
    String phaseTitle = titleParts.join(' + ')
    String gradleOnlyOneLiner
    if (sbomEnabled && jacocoEnabled) {
        gradleOnlyOneLiner = './gradlew clean test jacocoTestReport jacocoMergeReport combinedTestReport install cyclonedxBom || exit 1'
    } else if (sbomEnabled && !jacocoEnabled) {
        gradleOnlyOneLiner = './gradlew clean test install cyclonedxBom || exit 1'
    } else {
        gradleOnlyOneLiner = './gradlew clean test jacocoTestReport jacocoMergeReport combinedTestReport install || exit 1'
    }
    String gradleExtra = ''
    if (sbomEnabled || jacocoEnabled) {
        gradleExtra += """
                                        if [ -f "gradlew" ] || command -v gradle &> /dev/null; then
                                            echo "Optional Gradle steps (release tag; sbom=${sbomEnabled}, jacoco=${jacocoEnabled})..."
                            """
        if (sbomEnabled) {
            gradleExtra += """
                                            if [ -f "gradlew" ]; then
                                                ./gradlew cyclonedxBom || echo "⚠️ Gradle cyclonedxBom failed, trying Maven..."
                                            else
                                                gradle cyclonedxBom || echo "⚠️ Gradle cyclonedxBom failed, trying Maven..."
                                            fi
                            """
        }
        if (jacocoEnabled) {
            gradleExtra += """
                                            if [ -f "gradlew" ]; then
                                                ./gradlew jacocoTestReport || echo "⚠️ Gradle jacocoTestReport failed or skipped"
                                            else
                                                gradle jacocoTestReport || echo "⚠️ Gradle jacocoTestReport failed or skipped"
                                            fi
                            """
        }
        gradleExtra += """
                                        fi
                            """
    }
    String mavenSbom = sbomEnabled ? """
                                        if [ ! -f "build/reports/bom.xml" ] && [ ! -f "target/bom.xml" ]; then
                                            mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.0:makeAggregateBom -Dcyclonedx.skip=false -Dcyclonedx.outputFormat=xml -Dcyclonedx.outputName=bom || echo "⚠️ Maven SBOM generation failed"
                                        fi
                            """ : ''
    String mavenJacoco = jacocoEnabled ? """
                                        if [ -f "pom.xml" ] && [ ! -f "build/reports/jacoco/test/html/index.html" ]; then
                                            mvn jacoco:report || echo "⚠️ Maven jacoco:report failed or skipped"
                                        fi
                            """ : ''
    return """
                                    echo "--- PHASE: Compilation (${phaseTitle}) (release branch ${releaseBranchName}, tag ${tagName}) ---"
                                    if echo ",\${CX_GRADLE_ONLY_MODULES:-}," | grep -Fq ",\${SMG_MODULE},"; then
                                        ${gradleOnlyOneLiner}
                                    else
                                        ${fbuildCompileCmd} || exit 1
                        ${gradleExtra}${mavenSbom}${mavenJacoco}
                                    fi
                            """
}

void publishBranchTestReportsAndJacoco(Map args) {
    String moduleName = args.moduleName?.toString()
    boolean masterOn = args.containsKey('masterSecurityToolsOn') ? (args.masterSecurityToolsOn as boolean) : true
    boolean jacocoCatalogOn = args.containsKey('jacocoCatalogEnabled') ? (args.jacocoCatalogEnabled as boolean) : masterOn
    def com = securityToolsCommonScript()

    if (com.isGradleOnlyModule(moduleName)) {
        try {
            echo "--- PHASE: Publishing Test Results & Coverage Reports for ${moduleName} ---"
            def combinedTestHtmlPath = 'jars/build/reports/tests/combined/index.html'
            if (fileExists(combinedTestHtmlPath)) {
                publishHTML(target: [
                    reportDir  : 'jars/build/reports/tests/combined',
                    reportFiles: 'index.html',
                    reportName : "Test Results - ${moduleName}",
                    keepAll    : true
                ])
                echo "✅ Published combined test report for ${moduleName}"
            } else {
                echo "⚠️ Combined test report not found at ${combinedTestHtmlPath}"
            }
            if (jacocoCatalogOn) {
                def jacocoHtmlPath = 'jars/build/reports/jacoco/html/index.html'
                if (fileExists(jacocoHtmlPath)) {
                    publishHTML(target: [
                        reportDir  : 'jars/build/reports/jacoco/html',
                        reportFiles: 'index.html',
                        reportName : "JaCoCo Coverage - ${moduleName}",
                        keepAll    : true
                    ])
                    echo "✅ Published JaCoCo HTML coverage report for ${moduleName}"
                } else {
                    echo "⚠️ JaCoCo HTML report not found at ${jacocoHtmlPath}"
                }
            } else {
                echo '⏭️ Skipping JaCoCo HTML report (catalog key jacoco is false).'
            }
        } catch (Exception e) {
            echo "⚠️ Report publishing failed (build already succeeded; not failing pipeline): ${e.message}"
        }
    } else if (jacocoCatalogOn) {
        try {
            def jacocoDir = null
            if (fileExists('build/reports/jacoco/test/html/index.html')) {
                jacocoDir = 'build/reports/jacoco/test/html'
            } else if (fileExists('target/site/jacoco/index.html')) {
                jacocoDir = 'target/site/jacoco'
            }
            if (jacocoDir) {
                publishHTML(target: [
                    allowMissing         : false,
                    alwaysLinkToLastBuild: false,
                    keepAll              : true,
                    reportDir            : jacocoDir,
                    reportFiles          : 'index.html',
                    reportName           : "Coverage Report - ${moduleName}",
                    reportTitles         : "JaCoCo Coverage for ${moduleName}"
                ])
                echo "✅ Published JaCoCo HTML report from ${jacocoDir}"
            }
        } catch (Exception e) {
            echo "⚠️ Failed to publish JaCoCo report: ${e.message}"
        }
    }
}

void publishTagRebuildTestReportsAndJacoco(Map args) {
    String moduleName = args.moduleName?.toString()
    String releaseBranchName = args.releaseBranchName?.toString()
    String tagName = args.tagName?.toString()
    boolean tagBuildSecurityTools = args.tagBuildSecurityTools as boolean
    def com = securityToolsCommonScript()

    if (!com.isGradleOnlyModule(moduleName)) {
        return
    }
    try {
        echo "--- PHASE: Publishing Test Results & Coverage Reports (${releaseBranchName}, tag ${tagName}) ---"
        try {
            junit allowEmptyResults: true, testResultsPattern: 'jars/build/reports/**/TEST-*.xml'
            echo "✅ Published JUnit test results for ${moduleName} (${releaseBranchName})"
        } catch (Exception e) {
            echo "⚠️ Could not publish JUnit reports: ${e.message}"
        }
        def combinedTestHtmlPath = 'jars/build/reports/tests/combined/index.html'
        if (fileExists(combinedTestHtmlPath)) {
            publishHTML([
                reportDir  : 'jars/build/reports/tests/combined',
                reportFiles: 'index.html',
                reportName : "Test Results - ${moduleName} (${releaseBranchName} / ${tagName})",
                keepAll    : true
            ])
            echo "✅ Published combined test report for ${moduleName} (${releaseBranchName})"
        }
        if (tagBuildSecurityTools) {
            def jacocoHtmlPath = 'jars/build/reports/jacoco/html/index.html'
            if (fileExists(jacocoHtmlPath)) {
                publishHTML([
                    reportDir  : 'jars/build/reports/jacoco/html',
                    reportFiles: 'index.html',
                    reportName : "JaCoCo Coverage - ${moduleName} (${releaseBranchName} / ${tagName})",
                    keepAll    : true
                ])
                echo "✅ Published JaCoCo HTML coverage report for ${moduleName} (${releaseBranchName})"
            }
        } else {
            echo '⏭️ Skipping JaCoCo report for tag rebuild (security tools disabled).'
        }
    } catch (Exception e) {
        echo "⚠️ Report publishing failed for tag rebuild (not failing pipeline): ${e.message}"
    }
}

return this
