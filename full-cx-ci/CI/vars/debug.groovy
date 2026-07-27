// vars/debug.groovy
//
// EXACT REPLICA OF: CustomerXP debug.groovy
//
// PURPOSE:
// All debug/verbose logging goes through this script.
// Methods return bash script fragments OR log Groovy-side messages.
// Controlled by CX_PIPELINE_DEBUG env var (true/false).
//
// CustomerXP uses these bash fragments inside run_build.sh to:
// - Guard against silent Gradle build failures (SMG-specific)
// - Snapshot .m2 repository state before/after for diff
// - Validate Node.js version matches project requirements

/**
 * Log a message to Jenkins console only when debug is enabled.
 * Groovy-side logging (not bash).
 */
def pipelineLog(boolean pipelineDebugEnabled, String message) {
    if (pipelineDebugEnabled) {
        echo "🐛 [DEBUG] ${message}"
    }
}

/**
 * Log the M2/build context — module, JDK, build type, snapshot gate.
 * Useful for tracing parallel builds.
 */
def echoM2PipelineContext(String moduleName, String jdkVersion, String buildTypeSuffix, String releaseTag = null, boolean m2DebugEnabled = true) {
    if (!m2DebugEnabled) return
    echo "⚙️ Build context:"
    echo "   Module:         ${moduleName}"
    echo "   JDK:            ${jdkVersion}"
    echo "   BuildTypeSuffix:${buildTypeSuffix}"
    echo "   HasReleaseTag:  ${releaseTag ? 'YES (' + releaseTag + ')' : 'NO (snapshot)'}"
    echo "   SNAPSHOT gate:  ${buildTypeSuffix?.contains('SNAPSHOT') ? 'active' : 'inactive'}"
}

/**
 * Returns a bash function _cx_fail_if_buildall_says_failed.
 * CustomerXP's Gradle fbuild.sh can exit 0 even on failure (logs BUILD FAILED).
 * This guard reads buildall.txt and explicitly fails the shell if it finds 'BUILD FAILED'.
 * ADAPTED: simplified for non-SMG builds — still included for structural completeness.
 */
String getSmgBuildLogGuard(String cxGitOrgSeg = '') {
    return """
# ─── SMG Build Log Guard (CustomerXP pattern) ─────────────────────────────
_cx_fail_if_buildall_says_failed() {
    local logfile="\${1:-buildall.txt}"
    if [ -f "\$logfile" ]; then
        if grep -q 'BUILD FAILED' "\$logfile"; then
            echo "❌ BUILD FAILED detected in \$logfile"
            if [ "\${PIPELINE_DEBUG:-false}" = "true" ]; then
                echo "--- tail of \$logfile ---"
                tail -50 "\$logfile" || true
            fi
            exit 1
        fi
    fi
}
# ─────────────────────────────────────────────────────────────────────────
"""
}

/**
 * Returns bash that snapshots the .m2/repository file list before compilation.
 * CustomerXP uses this to diff what Maven downloaded during the build.
 * Adapted: uses standard paths available in Docker Jenkins.
 */
String getM2SnapshotPrepBash() {
    return """
# ─── M2 Snapshot (pre-build) ───────────────────────────────────────────────
if [ "\${M2_DEBUG:-false}" != "false" ]; then
    CX_M2_SNAPSHOT_FILE=\$(mktemp /tmp/m2_snapshot_XXXXXX.txt)
    export CX_M2_SNAPSHOT_FILE
    M2_REPO="\${HOME}/.m2/repository"
    if [ -d "\$M2_REPO" ]; then
        find "\$M2_REPO" -type f -printf "%T@ %s %p\\n" 2>/dev/null | sort > "\$CX_M2_SNAPSHOT_FILE" || true
        echo "[M2-DEBUG] Snapshot taken: \$(wc -l < \$CX_M2_SNAPSHOT_FILE) files"
    fi
fi
# ─────────────────────────────────────────────────────────────────────────
"""
}

/**
 * Returns bash that validates node version matches what the project expects.
 * CustomerXP reads expected version from bin/deps.cfg.
 * Adapted: checks if node is available and logs version.
 */
String getValidateNodeVersionBash(String cxGitOrgSeg = '') {
    return """
# ─── Node Version Validation ──────────────────────────────────────────────
validate_node_version() {
    if command -v node &>/dev/null; then
        ACTUAL_NODE=\$(node -v 2>/dev/null || echo 'unknown')
        echo "[NODE] Node.js version: \$ACTUAL_NODE"
        if [ "\${PIPELINE_DEBUG:-false}" = "true" ]; then
            echo "[NODE] DEBUG: node at \$(which node 2>/dev/null || echo 'not found')"
            echo "[NODE] DEBUG: npm at \$(which npm 2>/dev/null || echo 'not found')"
        fi
    else
        echo "[NODE] Node.js not available on this agent"
    fi
}
validate_node_version
# ─────────────────────────────────────────────────────────────────────────
"""
}

/**
 * Returns bash that diffs .m2 repository state post-build.
 * Shows which artifacts were newly downloaded or updated.
 */
String getM2RepositoryVerificationBash() {
    return """
# ─── M2 Repository Diff (post-build) ─────────────────────────────────────
if [ "\${M2_DEBUG:-false}" != "false" ] && [ -n "\${CX_M2_SNAPSHOT_FILE:-}" ] && [ -f "\$CX_M2_SNAPSHOT_FILE" ]; then
    echo "--- M2 Repository Changes During Build ---"
    M2_REPO="\${HOME}/.m2/repository"
    if [ -d "\$M2_REPO" ]; then
        CX_M2_POST_FILE=\$(mktemp /tmp/m2_post_XXXXXX.txt)
        find "\$M2_REPO" -type f -printf "%T@ %s %p\\n" 2>/dev/null | sort > "\$CX_M2_POST_FILE" || true
        echo "NEW artifacts downloaded:"
        comm -13 "\$CX_M2_SNAPSHOT_FILE" "\$CX_M2_POST_FILE" | awk '{print "  NEW:", \$3}' | head -20 || true
        echo "UPDATED artifacts:"
        comm -23 "\$CX_M2_SNAPSHOT_FILE" "\$CX_M2_POST_FILE" | awk '{print "  UPDATED:", \$3}' | head -20 || true
        rm -f "\$CX_M2_POST_FILE"
    fi
    rm -f "\$CX_M2_SNAPSHOT_FILE"
fi
# ─────────────────────────────────────────────────────────────────────────
"""
}

return this
