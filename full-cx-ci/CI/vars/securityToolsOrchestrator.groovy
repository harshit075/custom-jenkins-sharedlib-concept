// vars/securityToolsOrchestrator.groovy
//
// EXACT REPLICA OF: CustomerXP securityToolsOrchestrator.groovy
//
// PURPOSE:
// Orchestrates running all enabled security tools in sequence.
// Called by buildModulesWithJDK after the build completes.
// Reads securityToolsCatalog to decide which tools to run,
// then delegates to individual tool scripts (toolGitleaks, toolSonarqube, etc.)
//
// Two entry points (matching CustomerXP):
//   runBranchSecurityTooling(args)    — for snapshot/develop builds
//   runTagRebuildSecurityTooling(args) — for release tag builds

void runBranchSecurityTooling(Map args) {
    String moduleName    = args.moduleName
    String moduleBranch  = args.moduleBranch
    String jdkVersion    = args.jdkVersion
    String workspacePath = args.workspacePath
    String gitOrg        = args.gitOrg
    String namespace     = args.namespace ?: gitOrg
    boolean isCoreType   = args.isCoreType ?: false
    Map cfg              = args.cfg ?: [:]
    boolean runSecTools  = args.runSecurityTools ?: false

    if (!runSecTools) {
        echo "⏭️ securityToolsOrchestrator: security tools disabled — skipping all tools"
        return
    }

    echo "🔒 securityToolsOrchestrator: running security tools for ${moduleName} (${moduleBranch})"

    def secCat = loadSharedLibVarScript('securityToolsCatalog')

    // ── Tool 1: Gitleaks (secret scanning) ───────────────────────────────────
    if (!secCat || secCat.isToolEffective('gitleaks', runSecTools)) {
        def toolScript = loadSharedLibVarScript('toolGitleaks')
        if (toolScript) {
            try {
                toolScript.scanBranch(moduleName: moduleName, workspacePath: workspacePath)
            } catch (Throwable t) {
                echo "⚠️ Gitleaks scan failed (non-blocking): ${t.message}"
            }
        }
    }

    // ── Tool 2: SBOM + JaCoCo ─────────────────────────────────────────────────
    if (!secCat || secCat.isToolEffective('cyclonedx_sbom', runSecTools)) {
        def toolScript = loadSharedLibVarScript('toolSbomJacoco')
        if (toolScript) {
            try {
                toolScript.runBranch(moduleName: moduleName, workspacePath: workspacePath, jdkVersion: jdkVersion, cfg: cfg)
            } catch (Throwable t) {
                echo "⚠️ SBOM/JaCoCo tool failed (non-blocking): ${t.message}"
            }
        }
    }

    // ── Tool 3: SonarQube ────────────────────────────────────────────────────
    if (secCat ? secCat.isToolEffective('sonarqube', runSecTools) : cfg.sonarBaseUrl) {
        def toolScript = loadSharedLibVarScript('toolSonarqube')
        if (toolScript) {
            try {
                toolScript.runBranch(moduleName: moduleName, branchName: moduleBranch, workspacePath: workspacePath, cfg: cfg)
            } catch (Throwable t) {
                echo "⚠️ SonarQube scan failed (non-blocking): ${t.message}"
            }
        }
    }

    // ── Tool 4: Dependency-Track ──────────────────────────────────────────────
    def dtResult = [dtPublishOk: false, fullSbomPath: null]
    if (secCat ? secCat.isToolEffective('dependency_track', runSecTools) : false) {
        def toolScript = loadSharedLibVarScript('toolDependencyTrack')
        if (toolScript) {
            try {
                dtResult = toolScript.publishToDependencyTrackBranch([
                    moduleName: moduleName, moduleBranch: moduleBranch,
                    jdkVersion: jdkVersion, workspacePath: workspacePath,
                    gitOrg: gitOrg, namespace: namespace
                ]) ?: dtResult
            } catch (Throwable t) {
                echo "⚠️ Dependency-Track upload failed (non-blocking): ${t.message}"
            }
        }
    }

    // ── Tool 5: DefectDojo ────────────────────────────────────────────────────
    if (secCat ? secCat.isToolEffective('defectdojo', runSecTools) : false) {
        def toolScript = loadSharedLibVarScript('toolDefectdojo')
        if (toolScript) {
            try {
                toolScript.uploadBranch([
                    moduleName: moduleName, moduleBranch: moduleBranch,
                    fullSbomPath: dtResult.fullSbomPath
                ])
            } catch (Throwable t) {
                echo "⚠️ DefectDojo upload failed (non-blocking): ${t.message}"
            }
        }
    }

    echo "✅ securityToolsOrchestrator: completed for ${moduleName}"
}

void runTagRebuildSecurityTooling(Map args) {
    String moduleName   = args.moduleName
    String releaseTag   = args.releaseTag
    String moduleBranch = args.moduleBranch
    boolean runSecTools = args.runSecurityTools ?: false

    if (!runSecTools) {
        echo "⏭️ securityToolsOrchestrator [tag]: security tools disabled for release build"
        return
    }

    echo "🔒 securityToolsOrchestrator [tag]: running security tools for ${moduleName} tag=${releaseTag}"

    // For tag builds, run only Gitleaks and SBOM (not Sonar/DT/Dojo by default in CustomerXP)
    def toolGitleaks = loadSharedLibVarScript('toolGitleaks')
    if (toolGitleaks) {
        try {
            toolGitleaks.scanBranch(moduleName: moduleName, workspacePath: args.workspacePath)
        } catch (Throwable t) {
            echo "⚠️ Gitleaks scan failed (non-blocking): ${t.message}"
        }
    }

    echo "✅ securityToolsOrchestrator [tag]: completed for ${moduleName} tag=${releaseTag}"
}

return this
