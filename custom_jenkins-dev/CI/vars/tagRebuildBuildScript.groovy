// vars/tagRebuildBuildScript.groovy
//
/**
 * Generates the bash script written to {@code run_build_from_tag.sh} by {@code buildModulesWithJDKReleaseTag}.
 *
 * Required {@code Map} keys:
 * {@code preserveJdkBeforeBashrc}, {@code cxSmgBase}, {@code cxSmgDevResolved}, {@code javaSetupBlock},
 * {@code cxGitOrg}, {@code cxGitOrgSeg}, {@code dbg}, {@code compilationBlockTag}, {@code sonarBlockTag},
 * {@code isCoreType}, {@code moduleName}, {@code namespace}, {@code nexusBaseUrl}, {@code releaseBranchName},
 * {@code tagName}, {@code nexusPathDirForTag}, {@code nexusUploadPrefix}, {@code ccInstallerBranchSuffix}, {@code packInstallerCmd},
 * {@code nexusUser}, {@code nexusPassword},
 * {@code reactCiExport} — {@code 'true'} or {@code 'false'} from {@code CX_REACT_CI_STRICT} via {@code resolveReactCiEnv}.
 */
String render(Map m) {
    def dbg = m.dbg
    def cxGitOrgSeg = m.cxGitOrgSeg
    def cxGitOrg = m.cxGitOrg
    def preserveJdkBeforeBashrc = m.preserveJdkBeforeBashrc
    def cxSmgBase = m.cxSmgBase
    def cxSmgDevResolved = m.cxSmgDevResolved
    def javaSetupBlock = m.javaSetupBlock
    def compilationBlockTag = m.compilationBlockTag
    def sonarBlockTag = m.sonarBlockTag
    boolean isCoreType = m.isCoreType as boolean
    def moduleName = m.moduleName
    def namespace = m.namespace
    def nexusBaseUrl = m.nexusBaseUrl
    def releaseBranchName = m.releaseBranchName
    def tagName = m.tagName
    def nexusPathDirForTag = m.nexusPathDirForTag
    def nexusUploadPrefix = m.nexusUploadPrefix
    def ccInstallerBranchSuffix = m.ccInstallerBranchSuffix
    def packInstallerCmd = m.packInstallerCmd
    def nexusUser = m.nexusUser
    def nexusPassword = m.nexusPassword
    def reactCiExport = (m.reactCiExport?.toString()?.trim() in ['true', 'false']) ? m.reactCiExport.toString().trim() : 'false'

    return """#!/bin/bash
                                    ${preserveJdkBeforeBashrc}
                                    export CX_SMG_BASE="${cxSmgBase}"
                                    export PATH="\${CX_SMG_BASE}/bin:\${PATH}"
                                    _BRC="\${HOME}/.bashrc"
                                    touch "\${_BRC}" || true
                                    if ! grep -q '^export CX_SMG_DEV=' "\${_BRC}" 2>/dev/null; then
                                        echo '# customerxp-jenkins CX_SMG_DEV' >> "\${_BRC}"
                                        echo "export CX_SMG_DEV='${cxSmgDevResolved}'" >> "\${_BRC}"
                                    fi
                                    ${javaSetupBlock}
                                    export CI=${reactCiExport}
                                    export CX_SMG_NPM_CI=true
                                    _cx_verbose() { [ "\${PIPELINE_DEBUG:-false}" = "true" ]; }
                                    . "\${CX_SMG_BASE}/bin/cx_smg_main.sh"
                                    export CX_SMG_DEV="${cxSmgDevResolved}"
                                    if _cx_verbose; then echo "[SMG FINAL] CX_SMG_DEV=\${CX_SMG_DEV} CX_SMG_BASE=\${CX_SMG_BASE}"; fi

                                    _SMG_NS=\$(printf '%s' "\${TARGET_NAMESPACE}" | tr '/' ':')
                                    if _cx_verbose; then echo "[tag build] smg ${cxGitOrg}:\${_SMG_NS}:\${SMG_MODULE} (TARGET_NAMESPACE=\${TARGET_NAMESPACE})"; fi
                                    smg "${cxGitOrg}:\${_SMG_NS}:\${SMG_MODULE}"
                                    # Same as branch build: smgco selects the ref (branch or tag) in the SMG tree; tag flow must not skip this.
                                    if _cx_verbose; then echo "[tag build] smgco release branch \${SMG_BRANCH_NAME}"; fi
                                    smgco "\${SMG_BRANCH_NAME}"
                                    if [ -d "${cxGitOrg}/\${TARGET_NAMESPACE}/\${SMG_MODULE}" ]; then
                                        cd "${cxGitOrg}/\${TARGET_NAMESPACE}/\${SMG_MODULE}" || exit 1
                                        git fetch origin refs/heads/${releaseBranchName}:refs/remotes/origin/${releaseBranchName} 2>/dev/null || true
                                        git checkout -f refs/remotes/origin/${releaseBranchName} || exit 1
                                        if _cx_verbose; then echo "✅ On release branch ${releaseBranchName} in module dir (tag ${tagName} points to this commit)"; fi
                                    fi
                                    gws -f
                                    if echo ",\${CX_CREINSTALLER_MODULES:-}," | grep -Fq ",\${SMG_MODULE}," && [ "\${CX_SMG_NPM_CI:-}" = "true" ] && [ -n "\${CX_SMG_DEV:-}" ]; then
                                        _CC_ANG="\${CX_SMG_DEV}/${cxGitOrgSeg}/\${TARGET_NAMESPACE}/\${SMG_MODULE}/angular"
                                        if [ -d "\${_CC_ANG}/node_modules" ]; then
                                            if _cx_verbose; then echo "[cc] CX_SMG_NPM_CI: removing stale node_modules at \${_CC_ANG} (clean npm install)"; fi
                                            rm -rf "\${_CC_ANG}/node_modules"
                                        fi
                                    fi
                                    ${dbg.getValidateNodeVersionBash(cxGitOrgSeg)}
                                    if find . -maxdepth 3 -name package.json -print -quit 2>/dev/null | grep -q .; then
                                        validate_node_version || exit 1
                                    else
                                        if _cx_verbose; then echo "[NODE] package.json not found; skipping node version validation."; fi
                                    fi
                                    ${dbg.getM2SnapshotPrepBash()}
                                    ${compilationBlockTag}
                                    ${dbg.getSmgBuildLogGuard(cxGitOrgSeg)}
                                    ${sonarBlockTag}
                                    ${dbg.getM2RepositoryVerificationBash()}
                                    if [ "${isCoreType}" = "false" ]; then
                                        echo "--- PHASE: Installer Packing & Upload (release branch ${releaseBranchName}, tag ${tagName}) ---"
                                        if _cx_verbose; then
                                            echo "Packaging context:"
                                            echo "  module=${moduleName}"
                                            echo "  branch=${releaseBranchName}"
                                            echo "  namespace=${namespace}"
                                            echo "  nexusBaseUrl=${nexusBaseUrl}"
                                            echo "  nexusPathDir=${nexusPathDirForTag}"
                                            echo "Tip: set JENKINS_CD_BASH_X=true to run this script under bash -x"
                                            echo "Tools:"
                                            command -v creinstaller.sh >/dev/null 2>&1 && echo "  creinstaller.sh: \$(command -v creinstaller.sh)" || echo "  creinstaller.sh: not found in PATH"
                                            command -v bash >/dev/null 2>&1 && echo "  bash: \$(command -v bash)" || true
                                            command -v curl >/dev/null 2>&1 && echo "  curl: \$(command -v curl)" || true
                                            echo "Workspace snapshot (top):"
                                            ls -la . || true
                                        fi

                                        if echo ",\${CX_CREINSTALLER_MODULES:-}," | grep -Fq ",\${SMG_MODULE},"; then
                                            if _cx_verbose; then echo "[cc] Packaging: creinstaller.sh (PATH includes \${PWD}/bin — same directory as fbuild)"; fi
                                            _CC_CUR=\$(git branch --show-current 2>/dev/null || true)
                                            if [ -z "\${_CC_CUR}" ]; then
                                                _CC_BR="jenkins-cc-installer-${ccInstallerBranchSuffix}-\$\$"
                                                git checkout -b "\${_CC_BR}" || { echo "❌ [cc] git checkout -b \${_CC_BR} failed"; exit 1; }
                                                if _cx_verbose; then echo "[cc] Detached HEAD → created branch \${_CC_BR} for creinstaller/git symbolic-ref consumers"; fi
                                            fi
                                            rm -f cc_bom.json buildall.txt 2>/dev/null || true
                                            rm -rf .scannerwork 2>/dev/null || true
                                            PATH="\${PWD}/bin:\${PATH}" creinstaller.sh || exit 1
                                        else
                                            if _cx_verbose; then echo "Packaging: pack-module.sh"; fi
                                            ${packInstallerCmd} || echo "⚠️ pack-module.sh failed (will still attempt to locate artifacts)"
                                        fi
                                        if _cx_verbose; then echo "Nexus upload is mandatory for installer modules (HTTP 2xx required)."; fi

                                        if _cx_verbose; then
                                            echo "Workspace: \$(pwd)"
                                            echo "Git status (first 200 lines):"
                                            git status --porcelain | head -n 200 || true
                                        fi

                                        INST_FILE=""
                                        if echo ",\${CX_CREINSTALLER_MODULES:-}," | grep -Fq ",\${SMG_MODULE},"; then
                                            CC_VER_FOR_FILE="\${NEXUS_PATH_DIR#jdk\${CLEAN_JDK}-}"
                                            EXPECTED_CC_INSTALLER="cc-platform-jdk\${CLEAN_JDK}-\${CC_VER_FOR_FILE}-installer.bash"
                                            if _cx_verbose; then echo "[cc] Expecting installer: \${EXPECTED_CC_INSTALLER}"; fi
                                            INST_FILE=\$(find . -maxdepth 4 -type f -name "\${EXPECTED_CC_INSTALLER}" -not -path "./.git/*" 2>/dev/null | head -n 1)
                                            if [ -z "\${INST_FILE}" ]; then
                                                INST_FILE=\$(find . -maxdepth 4 -type f -name "cc-platform-jdk\${CLEAN_JDK}*installer.bash" -not -path "./.git/*" 2>/dev/null | head -n 1)
                                                if [ -n "\${INST_FILE}" ] && [ "\$(basename "\${INST_FILE}")" != "\${EXPECTED_CC_INSTALLER}" ]; then
                                                    if _cx_verbose; then
                                                    echo "[cc] Normalizing installer name for Nexus:"
                                                    echo "  from: \$(basename "\${INST_FILE}")"
                                                    echo "  to:   \${EXPECTED_CC_INSTALLER}"
                                                    fi
                                                    cp -f "\${INST_FILE}" "\${EXPECTED_CC_INSTALLER}"
                                                    INST_FILE="./\${EXPECTED_CC_INSTALLER}"
                                                fi
                                            fi
                                        fi
                                        if echo ",\${CX_CREINSTALLER_MODULES:-}," | grep -Fq ",\${SMG_MODULE}," && [ -z "\${INST_FILE}" ]; then
                                            echo "❌ [cc] Installer not produced. Expected a file like:"
                                            echo "   \${EXPECTED_CC_INSTALLER:-cc-platform-jdk\${CLEAN_JDK}*-installer.bash}"
                                            echo "Candidate .bash artifacts (first 50):"
                                            find . -maxdepth 4 -type f -name "*.bash" -not -path "./.git/*" 2>/dev/null | head -n 50 || true
                                            exit 1
                                        fi
                                        if [ -z "\${INST_FILE}" ]; then
                                            EXPECTED_TGZ="\${SMG_MODULE}-\${NEXUS_PATH_DIR}.tgz"
                                            if _cx_verbose; then echo "Expecting module archive: \${EXPECTED_TGZ}"; fi
                                            INST_FILE=\$(find . -maxdepth 4 -type f -name "\${EXPECTED_TGZ}" -not -path "./.git/*" 2>/dev/null | head -n 1)
                                        fi
                                        if [ -z "\${INST_FILE}" ]; then
                                            INST_FILE=\$(find . -maxdepth 1 -type f \\( -name "*.tgz" -o -name "*.bash" \\) 2>/dev/null | head -n 1)
                                        fi
                                        if [ -z "\${INST_FILE}" ]; then
                                            if _cx_verbose; then
                                            echo "No installer artifact found at workspace root. Searching deeper (maxdepth=4)..."
                                            echo "Candidate artifacts (first 50):"
                                            find . -maxdepth 4 -type f \\( -name "*.tgz" -o -name "*.bash" \\) -not -path "./.git/*" 2>/dev/null | head -n 50 || true
                                            fi
                                            INST_FILE=\$(find . -maxdepth 4 -type f \\( -name "*.tgz" -o -name "*.bash" \\) -not -path "./.git/*" 2>/dev/null | head -n 1)
                                        fi
                                        if [ -n "\${INST_FILE}" ]; then
                                            if ! echo ",\${CX_CREINSTALLER_MODULES:-}," | grep -Fq ",\${SMG_MODULE},"; then
                                                EXPECTED_TGZ="\${SMG_MODULE}-\${NEXUS_PATH_DIR}.tgz"
                                                if [ -n "\${EXPECTED_TGZ}" ] && [ "\$(basename "\${INST_FILE}")" != "\${EXPECTED_TGZ}" ] && [[ "\${INST_FILE}" == *.tgz ]]; then
                                                    if _cx_verbose; then
                                                    echo "Normalizing artifact name for Nexus:"
                                                    echo "  from: \$(basename "\${INST_FILE}")"
                                                    echo "  to:   \${EXPECTED_TGZ}"
                                                    fi
                                                    cp -f "\${INST_FILE}" "\${EXPECTED_TGZ}"
                                                    INST_FILE="./\${EXPECTED_TGZ}"
                                                fi
                                            fi
                                            if _cx_verbose; then echo "Selected artifact: \${INST_FILE}"; fi
                                            UPLOAD_URL="${nexusUploadPrefix}\$(basename \${INST_FILE})"
                                            NEXUS_BODY=\$(mktemp)
                                            if _cx_verbose; then
                                            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                                            echo "Nexus upload — debug (release branch ${releaseBranchName})"
                                            echo "  Module:     ${moduleName}"
                                            echo "  Local file: \${INST_FILE}"
                                            echo "  Size:       \$(stat -c '%s' "\${INST_FILE}" 2>/dev/null || echo unknown) bytes"
                                            echo "  PUT URL:    \${UPLOAD_URL}"
                                            echo "  curl:       \$(curl --version 2>/dev/null | head -1)"
                                            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                                            fi
                                            echo "Uploading (large artifacts may take several minutes)..."
                                            set +e
                                            HTTP_CODE=\$(curl -sS --http1.1 -u "${nexusUser}:${nexusPassword}" --upload-file "\${INST_FILE}" "\${UPLOAD_URL}" -o "\$NEXUS_BODY" -w '%{http_code}')
                                            CURL_EXIT=\$?
                                            set -e
                                            echo ""
                                            echo "Nexus result: HTTP status=\${HTTP_CODE}  curl_exit=\${CURL_EXIT}"
                                            if _cx_verbose && [ -s "\$NEXUS_BODY" ]; then
                                                echo "Nexus response body (first 4096 bytes):"
                                                head -c 4096 "\$NEXUS_BODY" | cat -v 2>/dev/null || head -c 4096 "\$NEXUS_BODY"
                                                echo ""
                                            fi
                                            rm -f "\$NEXUS_BODY"
                                            case "\${HTTP_CODE}" in
                                              2??) echo "✅ Nexus upload succeeded (HTTP \${HTTP_CODE})" ;;
                                              *)
                                                echo "❌ Nexus upload failed."
                                                case "\${HTTP_CODE}" in
                                                  000) echo "  → No HTTP status — connection/TLS/DNS/firewall or curl error (see curl exit \${CURL_EXIT}, https://curl.se/libcurl/c/libcurl-errors.html)." ;;
                                                  400) echo "  → HTTP 400 Bad Request — often nginx/proxy body limits (client_max_body_size), wrong repo path, or Nexus raw-repo policy; try HTTP/1.1 (already used) and check nginx error.log." ;;
                                                  401|403) echo "  → HTTP \${HTTP_CODE} — Nexus credentials or deploy role (content selector / privilege)." ;;
                                                  413) echo "  → HTTP 413 — request body too large; raise nginx client_max_body_size (and Nexus if applicable)." ;;
                                                  502|503|504) echo "  → HTTP \${HTTP_CODE} — gateway/proxy or Nexus upstream timeout." ;;
                                                  *) echo "  → Unexpected status \${HTTP_CODE}; check Nexus and nginx access/error logs for this request." ;;
                                                esac
                                                exit 1
                                                ;;
                                            esac
                                        else
                                            echo "❌ Installer file not found for ${moduleName}!"
                                            echo "Expected: *.tgz or *.bash under workspace root or within maxdepth=4."
                                            exit 1
                                        fi
                                    fi
                                """
}
