// vars/debug.groovy
//
// M2 / SMG / Node debug helpers embedded in run_build.sh and run_build_from_tag.sh.
// Keeps buildModulesWithJDK focused on orchestration.
//
// Bash fragments honor {@code PIPELINE_DEBUG} (same value as {@code cfg.m2DebugExplicit} via {@code withEnv}).

/**
 * Optional Jenkins console line when pipeline verbose debug is on ({@code JENKINS_PIPELINE_DEBUG} / {@code JENKINS_M2_DEBUG} via {@code cxPipelineConfig}).
 */
void pipelineLog(boolean pipelineDebugEnabled, String message) {
    if (pipelineDebugEnabled) {
        echo message
    }
}

/**
 * Groovy-side M2 context (follows {@code cxPipelineConfig} effective M2 debug: {@code JENKINS_PIPELINE_DEBUG} or {@code JENKINS_M2_DEBUG}).
 */
void echoM2PipelineContext(String moduleName, String jdkVersion, String buildTypeSuffix, String releaseTag, boolean m2DebugEnabled = true) {
    if (!m2DebugEnabled) {
        return
    }
    def hasRelTag = (releaseTag?.toString()?.trim() ?: '') != ''
    echo "[M2-DEBUG] --- context (module=${moduleName}, jdk=${jdkVersion}) ---"
    echo "[M2-DEBUG] BUILD_TYPE from env=${env.BUILD_TYPE} => buildTypeSuffix=${buildTypeSuffix}"
    echo "[M2-DEBUG] hasReleaseTag=${hasRelTag}  (release-cipipeline sets RELEASE_TAG → skips first run_build.sh; .m2 verification lives in that script)"
    echo "[M2-DEBUG] .m2 local-repo verification in run_build.sh runs ONLY if BUILD_TYPE_SUFFIX=SNAPSHOT (current: ${buildTypeSuffix == 'SNAPSHOT' ? 'SNAPSHOT — phase will run when run_build executes' : "${buildTypeSuffix} — phase skipped (use BUILD_TYPE=snapshot in dev-ci to match)"})"
}

/**
 * After fbuild.sh, fail if Gradle wrote BUILD FAILED to buildall.txt (fbuild often still exits 0).
 */
String getSmgBuildLogGuard(String cxGitOrgSeg) {
    return """
                            _cx_fail_if_buildall_says_failed() {
                                local _log=""
                                local _try=""
                                if [ -n "\${CX_SMG_DEV:-}" ]; then
                                    _try="\${CX_SMG_DEV}/${cxGitOrgSeg}/\${TARGET_NAMESPACE}/\${SMG_MODULE}/buildall.txt"
                                    [ -f "\${_try}" ] && _log="\${_try}"
                                fi
                                if [ -z "\${_log}" ] && [ -f ./buildall.txt ]; then
                                    _log="./buildall.txt"
                                fi
                                [ -n "\${_log}" ] || return 0
                                local _last
                                _last=\$(grep -E '^BUILD (SUCCESSFUL|FAILED)[[:space:]]' "\${_log}" 2>/dev/null | tail -n 1 || true)
                                [ -n "\${_last}" ] || return 0
                                if echo "\${_last}" | grep -q '^BUILD FAILED'; then
                                    echo "❌ Last Gradle aggregate in \${_log}: \${_last} — failing pipeline (SMG/fbuild may have exited 0)."
                                    if [ "\${PIPELINE_DEBUG:-false}" = "true" ]; then
                                        tail -n 120 "\${_log}" || true
                                    fi
                                    exit 1
                                fi
                            }
                            _cx_fail_if_buildall_says_failed
"""
}

/**
 * Optional .m2 snapshot listing (before compilation) when M2_DEBUG is enabled.
 */
String getM2SnapshotPrepBash() {
    return '''
                            SNAP_TS=$(mktemp)
                            touch "$SNAP_TS"
                            M2_DIR="${MAVEN_CONFIG:-${HOME}/.m2}/repository"
                            M2_PRE=""
                            if [ "${M2_DEBUG}" != "false" ] && [ -d "${M2_DIR}" ]; then
                                M2_PRE=$(mktemp)
                                find "${M2_DIR}" -type f -path "*/${SMG_MODULE}/*" -printf '%P\t%T@\t%s\n' 2>/dev/null | sort > "${M2_PRE}" || true
                            fi
'''
}

/**
 * Node version check used when package.json exists (includes [NODE] DEBUG lines).
 */
String getValidateNodeVersionBash(String cxGitOrgSeg) {
    return """
                            validate_node_version() {
                                if [ "\${PIPELINE_DEBUG:-false}" = "true" ]; then
                                    echo "[NODE] DEBUG (after SMG): \$(command -v node 2>/dev/null || echo '(node not in PATH)')"
                                    echo "[NODE] DEBUG (after SMG): \$(node -v 2>/dev/null || echo '(node -v failed)')"
                                fi
                                expected=""
                                pin_source=""
                                if [ -f "./bin/deps.cfg" ]; then
                                    expected=\$(grep -Ei '^[[:space:]]*(export[[:space:]]+)?NODE(JS)?_VERSION[[:space:]]*=' "./bin/deps.cfg" 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d " \\\"'" | tr -d '\r' || true)
                                    if [ -n "\${expected}" ]; then pin_source="./bin/deps.cfg"; fi
                                fi
                                if [ -z "\${expected}" ] && [ -n "\${CX_SMG_BASE:-}" ] && [ -f "\${CX_SMG_BASE}/bin/deps.cfg" ]; then
                                    expected=\$(grep -Ei '^[[:space:]]*(export[[:space:]]+)?NODE(JS)?_VERSION[[:space:]]*=' "\${CX_SMG_BASE}/bin/deps.cfg" 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d " \\\"'" | tr -d '\r' || true)
                                    if [ -n "\${expected}" ]; then pin_source="\${CX_SMG_BASE}/bin/deps.cfg"; fi
                                fi
                                if [ -z "\${expected}" ] && [ -n "\${CX_SMG_DEV:-}" ]; then
                                    _SMG_DEPS="\${CX_SMG_DEV}/${cxGitOrgSeg}/\${TARGET_NAMESPACE}/\${SMG_MODULE}/bin/deps.cfg"
                                    if [ -f "\${_SMG_DEPS}" ]; then
                                        expected=\$(grep -Ei '^[[:space:]]*(export[[:space:]]+)?NODE(JS)?_VERSION[[:space:]]*=' "\${_SMG_DEPS}" 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d " \\\"'" | tr -d '\r' || true)
                                        if [ -n "\${expected}" ]; then pin_source="\${_SMG_DEPS}"; fi
                                    fi
                                fi
                                expected="\${expected#v}"
                                if ! command -v node >/dev/null 2>&1; then
                                    echo "❌ [NODE] node is not on PATH after smgco/gws. Frontend work requires Node from SMG — ensure CX_SMG_BASE (\${CX_SMG_BASE:-unset}) exposes Node via cx_smg_main.sh / smgco for Jenkins non-login shells."
                                    return 1
                                fi
                                actual=\$(node -v 2>/dev/null | head -n 1 || true)
                                actual="\${actual#v}"
                                node_path=\$(command -v node 2>/dev/null || true)
                                if [ -n "\${expected}" ]; then
                                    if [ "\${actual}" != "\${expected}" ]; then
                                        echo "❌ [NODE] node version mismatch. expected=\${expected} actual=\${actual} (pin from \${pin_source})"
                                        return 1
                                    fi
                                    if [ "\${PIPELINE_DEBUG:-false}" = "true" ]; then
                                        echo "✅ [NODE] node version OK: \${actual} (deps=\${pin_source})"
                                    fi
                                    return 0
                                fi
                                if [ "\${PIPELINE_DEBUG:-false}" = "true" ]; then
                                    echo "✅ [NODE] SMG mode: v\${actual} at \${node_path} — no NODE_VERSION in module or SMG deps.cfg; trusting SMG PATH"
                                fi
                                return 0
                            }
"""
}

/**
 * Verbose local Maven repo inspection after compilation/Sonar (M2_DEBUG).
 */
String getM2RepositoryVerificationBash() {
    return '''
                            if [ "${M2_DEBUG}" != "false" ]; then
                            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                            echo "[M2-DEBUG] BUILD_TYPE_SUFFIX=${BUILD_TYPE_SUFFIX:-}"
                            echo "[M2-DEBUG] M2_DEBUG env (from Jenkins)=${M2_DEBUG:-}"
                            echo "[M2-DEBUG] JAVA_HOME=${JAVA_HOME:-}  java=$(command -v java 2>/dev/null || echo missing)"
                            echo "[M2-DEBUG] Local Maven repo dir will be: ${M2_DIR}"
                                echo "[M2-DEBUG] Gate: M2_DEBUG!=false — running .m2 verification (BUILD_TYPE_SUFFIX=${BUILD_TYPE_SUFFIX:-})."
                                echo "--- PHASE: .m2 Verification (${BUILD_TYPE_SUFFIX:-UNKNOWN} builds) ---"
                                echo "User: $(whoami 2>/dev/null || true)"
                                echo "HOME: ${HOME:-}"
                                echo "M2_HOME: ${M2_HOME:-}"
                                echo "MAVEN_CONFIG: ${MAVEN_CONFIG:-}"
                                command -v mvn >/dev/null 2>&1 && mvn -v || echo "mvn not found"
                                command -v gradle >/dev/null 2>&1 && gradle -v || echo "gradle not found"
                                echo "Local Maven repository: ${M2_DIR}"
                                if [ -d "${M2_DIR}" ]; then
                                    echo "Recent updates in ${M2_DIR} (since build start, first 50 files):"
                                    find "${M2_DIR}" -type f -newer "$SNAP_TS" 2>/dev/null | head -n 50 || true
                                    echo "Recent directories in ${M2_DIR} (since build start, first 30):"
                                    find "${M2_DIR}" -type d -newer "$SNAP_TS" 2>/dev/null | head -n 30 || true
                                    echo "Changed files with timestamps (since build start, first 50):"
                                    find "${M2_DIR}" -type f -newer "$SNAP_TS" -print0 2>/dev/null | head -z -n 50 | xargs -0 -I {} sh -c 'stat -c "%y %s %n" "{}" 2>/dev/null || true' || true
                                    if [ -n "${M2_PRE}" ] && [ -s "${M2_PRE}" ]; then
                                        M2_POST=$(mktemp)
                                        find "${M2_DIR}" -type f -path "*/${SMG_MODULE}/*" -printf '%P\t%T@\t%s\n' 2>/dev/null | sort > "${M2_POST}" || true
                                        echo "Module-scoped .m2 changes (path contains /${SMG_MODULE}/, first 50):"
                                        awk -F '\t' 'NR==FNR { oldT[$1]=$2; oldS[$1]=$3; next } { p=$1; t=$2; s=$3; if (!(p in oldT)) { print "NEW\t"p"\t"t"\t"s } else if (oldT[p]!=t || oldS[p]!=s) { print "UPDATED\t"p"\t"oldT[p]"\t"oldS[p]"\t"t"\t"s } }' "${M2_PRE}" "${M2_POST}" | head -n 50 || true
                                        rm -f "${M2_POST}" "${M2_PRE}" || true
                                    fi
                                else
                                    echo "⚠️ Local Maven repository dir not found: ${M2_DIR}"
                                fi
                            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                            fi
'''
}
