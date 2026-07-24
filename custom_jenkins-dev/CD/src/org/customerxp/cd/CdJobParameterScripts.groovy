package org.customerxp.cd

/**
 * P2-2: SecureGroovyScript bodies for CD job parameters — kept out of cd-pipeline.jenkinsfile so the
 * shared library can compile and test them; Active Choice parameters still receive plain strings.
 * P2-3: Active Choice scripts cannot load shared-library {@code src/} classes — helpers are inlined below.
 * P3-3: DB normalization closures mirror {@link org.customerxp.cd.CdDbConfigNormalize}; change both when updating.
 * <p><b>PER_MODULE_DB_JSON HTML:</b> Active Choices renders {@code j:out getChoicesAsString()}. If the Groovy body
 * throws, the plugin catches the error and substitutes an empty {@link java.util.Map}, whose {@code toString()} is
 * literally {@code {}} — the symptom users see as only {@code {}} on the form (no grey “No per-module…” line).
 * Search the controller log for {@code [CD DEBUG PER_MODULE_DB_JSON]} after opening Build with Parameters.
 * P5-4: The per-module DB table lists registry PRIMARY_DB/CUSTOM_DB ids that are <b>also checked</b> in {@code MODULE_SELECTED}
 * (script is re-evaluated when that parameter changes; {@code MODULE_SELECTED,JDK_VERSION} in referencedParameters).
 * With {@code sandbox:true}, Uno-Choice also
 * runs HTML through {@code SafeHtmlExtendedMarkupFormatter} (no {@code <script>}, no {@code <button>}); this widget
 * uses {@code <input type="button">}, inline {@code onclick}, and Base64 in hidden {@code <textarea>}s (not huge
 * {@code data-*}) so it survives both modes.
 */
@SuppressWarnings('unused')
class CdJobParameterScripts {

    private static String sqLit(String v) {
        if (v == null) return "''"
        def e = v.toString().replace('\\', '\\\\').replace("'", "\\'")
        "'" + e + "'"
    }

    /**
     * Groovy closures pasted into every SecureGroovyScript body.
     * P3-3: DB normalize closures stay aligned with {@link org.customerxp.cd.CdDbConfigNormalize}.
     */
    private static String activeChoiceRuntimeHelpers() {
        return '''def __cdSelectedModulesLower = { Object moduleSelected ->
    if (moduleSelected instanceof Collection) {
        return moduleSelected.collect { it?.toString()?.trim()?.toLowerCase() }.findAll { it }
    }
    if (moduleSelected != null && moduleSelected.getClass().isArray()) {
        return moduleSelected.collect { it?.toString()?.trim()?.toLowerCase() }.findAll { it }
    }
    if (moduleSelected != null && moduleSelected.toString().trim()) {
        return moduleSelected.toString().tokenize(',').collect { it.trim().toLowerCase() }.findAll { it }
    }
    return []
}
def __cdFirstGlobalEnvVars = {
    def globalEnv = [:]
    try {
        def instance = Jenkins.getInstanceOrNull()
        if (instance) {
            def props = instance.getGlobalNodeProperties()
            def envNodes = props.getAll(EnvironmentVariablesNodeProperty.class)
            if (envNodes && envNodes.size() > 0) {
                globalEnv = envNodes[0].getEnvVars()
            }
        }
    } catch (Exception ignored) { }
    globalEnv
}
def __cdEnvLookupAppServer = { String key ->
    def env = null
    try {
        def j = Jenkins.getInstance()
        if (j) {
            def list = j.getGlobalNodeProperties().getAll(EnvironmentVariablesNodeProperty.class)
            if (list && !list.isEmpty()) env = list.get(0).getEnvVars()
        }
    } catch (Exception ignored) { }
    if (env != null && env.get(key)) return env.get(key).toString().trim()
    return System.getenv(key)?.toString()?.trim()
}
def __cdDbConfigPath = { Map globalEnv ->
    def path = globalEnv?.get('JENKINS_CD_DB_CONFIG_FILE')?.toString()?.trim()
    if (path) return path
    return System.getenv('JENKINS_CD_DB_CONFIG_FILE')?.toString()?.trim() ?: null
}
def __cdNormalizeDbCredentials = { Object raw ->
    def normalized = [:]
    if (raw instanceof Map && raw.databases instanceof List) {
        raw.databases.each { item ->
            def name = item?.name?.toString()?.trim()
            if (name) {
                normalized[name] = [
                    DB_USER      : item.user?.toString() ?: item.DB_USER?.toString() ?: '',
                    DB_PASSWORD  : item.password?.toString() ?: item.DB_PASSWORD?.toString() ?: '',
                    credential_id: item.credential_id?.toString() ?: ''
                ]
            }
        }
        return normalized
    }
    if (raw instanceof Map) {
        raw.each { key, value ->
            if (!key.toString().startsWith('_') && value instanceof Map) {
                normalized[key.toString()] = value
            }
        }
    }
    normalized
}
def __cdNormalizeDbFullGlobal = { Object raw ->
    def normalized = [:]
    if (raw instanceof Map && raw.databases instanceof List) {
        raw.databases.each { item ->
            def name = item?.name?.toString()?.trim()
            if (name) {
                normalized[name] = [
                    DB_TYPE      : item.type?.toString() ?: item.DB_TYPE?.toString() ?: '',
                    DB_SID       : item.sid?.toString() ?: item.DB_SID?.toString() ?: '',
                    DB_IP        : item.host?.toString() ?: item.DB_IP?.toString() ?: '',
                    DB_PORT      : item.port?.toString() ?: item.DB_PORT?.toString() ?: '',
                    DB_USER      : item.user?.toString() ?: item.DB_USER?.toString() ?: '',
                    DB_PASSWORD  : item.password?.toString() ?: item.DB_PASSWORD?.toString() ?: '',
                    credential_id: item.credential_id?.toString()?.trim() ?: item.credentialId?.toString()?.trim() ?: ''
                ]
            }
        }
        return normalized
    }
    if (raw instanceof Map) {
        raw.each { key, value ->
            if (!key.toString().startsWith('_') && value instanceof Map) {
                normalized[key.toString()] = [
                    DB_TYPE      : value.DB_TYPE?.toString() ?: value.type?.toString() ?: '',
                    DB_SID       : value.DB_SID?.toString() ?: value.sid?.toString() ?: '',
                    DB_IP        : value.DB_IP?.toString() ?: value.host?.toString() ?: '',
                    DB_PORT      : value.DB_PORT?.toString() ?: value.port?.toString() ?: '',
                    DB_USER      : value.DB_USER?.toString() ?: value.user?.toString() ?: '',
                    DB_PASSWORD  : value.DB_PASSWORD?.toString() ?: value.password?.toString() ?: '',
                    credential_id: value.credential_id?.toString()?.trim() ?: value.credentialId?.toString()?.trim() ?: ''
                ]
            }
        }
    }
    normalized
}
def __cdNormalizeDbPerModuleMeta = { Object raw ->
    def normalized = [:]
    if (raw instanceof Map && raw.databases instanceof List) {
        raw.databases.each { item ->
            def name = item?.name?.toString()?.trim()
            if (name) {
                normalized[name] = [
                    DB_USER      : item.user?.toString() ?: item.DB_USER?.toString() ?: '',
                    credential_id: item.credential_id?.toString() ?: item.credentialId?.toString() ?: ''
                ]
            }
        }
        return normalized
    }
    if (raw instanceof Map) {
        raw.each { key, value ->
            if (!key.toString().startsWith('_') && value instanceof Map) {
                normalized[key.toString()] = [
                    DB_USER      : value.DB_USER?.toString() ?: value.user?.toString() ?: '',
                    credential_id: value.credential_id?.toString() ?: value.credentialId?.toString() ?: ''
                ]
            }
        }
    }
    normalized
}
'''
    }

    /**
     * P2-5: Never injects hardcoded tag names when GitLab returns none; default selection uses real tags only ({@code tagNames[0]} after sort).
     * P4-4: Inline script only calls {@code GET .../repository/tags}; minimize GitLab PAT scopes ({@link CdGitLabTokenScopes}).
     * <p>Also appends the CD row-visibility bootstrap (same body as {@link #dbCredentialsRowVisibilityScript}) so
     * {@code PER_MODULE_DB_JSON} / {@code DB_SERVER} rows sync as soon as this HTML loads — not only
     * when {@code DB_CREDENTIALS_INPUT} renders (which can be too late or omit the outer {@code <script>} in some setups).
     * {@code CC_PORT} row stays visible; its input is disabled and greyed when the registry {@code cc_setup} module
     * is not checked (deploy ignores CC_PORT unless cc is selected).
     */
    static String moduleVersionChoicesScript(String glApi, String glOrg, String glNs, String glCred, String jsMapForRowVis, String ccSetupModuleIdForRowVis) {
        def Lapi = sqLit(glApi)
        def Lorg = sqLit(glOrg)
        def Lns = sqLit(glNs)
        def Lcred = sqLit(glCred)
        def rowVisBody = dbCredentialsRowVisibilityScript(jsMapForRowVis ?: '{}', (ccSetupModuleIdForRowVis ?: '').toString().trim().toLowerCase())
        def Lrvs = sqLit('<script>' + rowVisBody + '</script>')
        """
                        import groovy.json.JsonSlurper
                        import java.net.URL
                        import java.net.URLEncoder
                        import jenkins.model.Jenkins
                        import com.cloudbees.plugins.credentials.CredentialsProvider
                        import org.jenkinsci.plugins.plaincredentials.StringCredentials
                        import hudson.slaves.EnvironmentVariablesNodeProperty
                        ${activeChoiceRuntimeHelpers()}
                        def gitlabUrl = (${Lapi}).toString().replaceAll('/+\$', '')
                        def gitOrg = ${Lorg}
                        def namespaces = (${Lns}).split(',').collect { it.trim() }
                        def credentialsId = ${Lcred}
                        def jenkins = Jenkins.get()
                        def creds = CredentialsProvider.lookupCredentials(StringCredentials.class, jenkins, null, null).find { it.id == credentialsId }
                        if (!creds) return "<b style='color:red'>GitLab credential not found: " + credentialsId + "</b>" + ${Lrvs}
                        def privateToken = creds.secret.plainText
                        def __msMv = null
                        try { __msMv = MODULE_SELECTED } catch (Throwable __ignored) { __msMv = null }
                        def selectedModules = __cdSelectedModulesLower(__msMv)
                        if (selectedModules.isEmpty()) return "<i>Select module(s) above first.</i>" + ${Lrvs}
                        def perPage = 100
                        def html = "<p style='margin:0 0 8px 0;color:#666;font-size:12px'>GitLab tag per module; use manual entry for a Nexus snapshot name.</p>"
                        html += "<style>.manual-version-inline{display:none;margin-top:6px;}.module-version-row .manual-version-toggle:checked ~ .manual-version-inline{display:block;}.module-version-select.manual-disabled{opacity:0.6;background:#f7f7f7;}</style>"
                        html += "<table style='width:100%; border-collapse: collapse; border:1px solid #ccc'>"
                        html += "<tr style='background:#eee'><th style='padding:6px;text-align:left'>Module</th><th style='padding:6px;text-align:left'>Version (GitLab tag)</th></tr>"
                        selectedModules.each { moduleName ->
                            def tagNames = []
                            def fetchErrors = []
                            def safeModuleId = moduleName.replaceAll(/[^A-Za-z0-9_-]/, '_')
                            namespaces.each { namespace ->
                                if (tagNames.isEmpty()) {
                                    try {
                                        for (int page = 1; page <= 5; page++) {
                                            def projectPath = gitOrg + '/' + namespace + '/' + moduleName
                                            def encodedPath = URLEncoder.encode(projectPath, 'UTF-8').replace('+', '%20')
                                            def url = gitlabUrl + '/projects/' + encodedPath + '/repository/tags?per_page=' + perPage + '&page=' + page + '&order_by=updated&sort=desc'
                                            def conn = new URL(url).openConnection()
                                            conn.setRequestProperty('PRIVATE-TOKEN', privateToken)
                                            if (conn.responseCode != 200) {
                                                fetchErrors.add(projectPath + ' -> HTTP ' + conn.responseCode)
                                                break
                                            }
                                            def json = new JsonSlurper().parseText(conn.inputStream.text)
                                            if (!(json instanceof List)) {
                                                fetchErrors.add(projectPath + ' -> invalid tag response')
                                                break
                                            }
                                            json.each { tag -> if (tag.name) tagNames.add(tag.name.toString().trim()) }
                                            if (json.size() < perPage) break
                                        }
                                    } catch (Exception e) {
                                        fetchErrors.add((gitOrg + '/' + namespace + '/' + moduleName) + ' -> ' + e.getClass().getSimpleName() + ': ' + (e.message ?: 'lookup failed'))
                                    }
                                }
                            }
                            def escAttr = { v -> (v == null ? "" : v.toString()).replace('&', '&amp;').replace("'", '&#39;') }
                            def jsSelect = 'var r=this.closest(".module-version-row");var c=r.querySelector(".manual-version-toggle");if(!c||!c.checked){r.querySelector(".module-version-hidden").value=this.value;}'
                            def jsCheck = 'var r=this.closest(".module-version-row");var m=r.getAttribute("data-module");var h=r.querySelector(".module-version-hidden");var s=r.querySelector(".module-version-select");var t=r.querySelector(".manual-version-input");if(this.checked){h.value=m+":"+(t&&t.value?t.value.trim():"");if(s){s.disabled=true;s.classList.add("manual-disabled");}}else{h.value=s?s.value:"";if(s){s.disabled=false;s.classList.remove("manual-disabled");}}'
                            def jsManual = 'var r=this.closest(".module-version-row");var c=r.querySelector(".manual-version-toggle");if(c&&c.checked){r.querySelector(".module-version-hidden").value=r.getAttribute("data-module")+":"+this.value.trim();}'
                            def errorMessage = fetchErrors ? fetchErrors.join(' | ') : ('No GitLab tags found for ' + moduleName + ' in namespaces ' + namespaces.join(', '))
                            tagNames = new ArrayList(new LinkedHashSet(tagNames)).sort().reverse()
                            def hasGitTags = !tagNames.isEmpty()
                            def defaultTag = hasGitTags ? tagNames[0] : ''
                            def defaultPair = hasGitTags ? (moduleName + ':' + defaultTag) : ''
                            def toggleChecked = hasGitTags ? '' : ' checked'
                            html += "<tr><td style='padding:5px'><b>" + moduleName + "</b></td><td style='padding:5px'>"
                            html += "<div class='module-version-row' data-module='" + escAttr(moduleName) + "'>"
                            if (hasGitTags) {
                                html += "<select class='module-version-select' data-module='" + escAttr(moduleName) + "' style='width:100%' onchange='" + jsSelect + "'>"
                                tagNames.each { tag ->
                                    def pair = moduleName + ':' + tag
                                    def sel = (tag == defaultTag) ? ' selected' : ''
                                    html += "<option value='" + escAttr(pair) + "'" + sel + ">" + tag + "</option>"
                                }
                                html += "</select>"
                            } else {
                                html += "<div style='margin-bottom:6px; color:#b00020; font-size:12px;'><b>No GitLab tags loaded.</b> " + escAttr(errorMessage) + "</div>"
                            }
                            html += "<input type='checkbox' class='manual-version-toggle' id='manualToggle_" + safeModuleId + "' data-module='" + escAttr(moduleName) + "' style='margin-top:6px;' onchange='" + jsCheck + "'" + toggleChecked + ">"
                            html += "<label for='manualToggle_" + safeModuleId + "' style='margin-left:4px; font-size:12px; color:#555;'>Enter exact Nexus snapshot manually</label>"
                            html += "<div id='manualVersion_" + safeModuleId + "' class='manual-version-inline'>"
                            html += "<input type='text' class='manual-version-input' data-module='" + escAttr(moduleName) + "' placeholder='Exact Nexus snapshot version' style='width:100%; box-sizing:border-box;' oninput='" + jsManual + "' onchange='" + jsManual + "'>"
                            html += "</div>"
                            html += "<input type='hidden' name='value' class='module-version-hidden setting-input' data-module='" + escAttr(moduleName) + "' value='" + escAttr(defaultPair) + "'>"
                            html += "</div></td></tr>"
                        }
                        html += "</table>"
                        def jsSubmit = 'document.addEventListener("submit",function(){document.querySelectorAll(".module-version-row").forEach(function(r){var h=r.querySelector(".module-version-hidden");var c=r.querySelector(".manual-version-toggle");var m=r.querySelector(".manual-version-input");var s=r.querySelector(".module-version-select");var mod=r.getAttribute("data-module")||"";if(h){if(c&&c.checked&&m){h.value=mod+":"+(m.value||"").trim();}else if(s){h.value=s.value;}}});},true);'
                        html += "<script>" + jsSubmit + "</" + "script>"
                        html += ${Lrvs}
                        return html
        """
    }

    static String perModuleDbJsonFormattedHtml(String perDbJsonForScripts) {
        """
                        import groovy.json.JsonOutput
                        import groovy.json.JsonSlurper
                        import java.util.Base64
                        import java.nio.charset.StandardCharsets
                        import jenkins.model.Jenkins
                        import hudson.slaves.EnvironmentVariablesNodeProperty
                        ${activeChoiceRuntimeHelpers()}
                        try {
                        // P5-4: One row per selected registry PRIMARY_DB/CUSTOM_DB module (intersection with MODULE_SELECTED). JDK_VERSION in referencedParameters satisfies Uno-Choice when paired with MODULE_SELECTED; script does not use JDK for logic.
                        def rawSpec = new JsonSlurper().parseText(${sqLit(perDbJsonForScripts)})
                        def spec = (rawSpec instanceof Collection) ? (rawSpec.collect { it?.toString()?.trim()?.toLowerCase() }.findAll { it } as Set) : ([] as Set)
                        def allRegistryTargets = new ArrayList(spec).sort()
                        println "[CD DEBUG PER_MODULE_DB_JSON] allRegistryPerDbModules=" + allRegistryTargets + " count=" + allRegistryTargets.size()
                        if (allRegistryTargets.isEmpty()) {
                            return "<p style='margin:4px 0; color:#666'><i>No PRIMARY_DB/CUSTOM_DB modules in the registry list for this job</i> (empty <code>perDbJsonForScripts</code> or <b>JENKINS_CD_PER_MODULE_DB_UI_MODULES</b> filtered all out).</p><input type='hidden' name='value' value=''>"
                        }
                        def __msPmdb = null
                        try { __msPmdb = MODULE_SELECTED } catch (Throwable __ignored) { __msPmdb = null }
                        def selectedMods = __cdSelectedModulesLower(__msPmdb)
                        def selSet = selectedMods as Set
                        def targets = allRegistryTargets.findAll { selSet.contains(it) }
                        println "[CD DEBUG PER_MODULE_DB_JSON] MODULE_SELECTED=" + selectedMods + " filteredTargets=" + targets
                        if (selectedMods.isEmpty()) {
                            return "<p style='margin:4px 0; color:#666'><i>Select at least one module in <b>MODULE_SELECTED</b> above.</i> Per-module DB rows appear only for <b>selected</b> registry PRIMARY_DB/CUSTOM_DB modules.</p><input type='hidden' name='value' value=''>"
                        }
                        if (targets.isEmpty()) {
                            def hint = allRegistryTargets.collect { it.toString().replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;') }.join(', ')
                            return "<p style='margin:4px 0; color:#666'><i>No PRIMARY_DB/CUSTOM_DB modules in your current selection.</i> For this job they are: <code>" + hint + "</code>. Check the matching module(s) in <b>MODULE_SELECTED</b> to configure DB here.</p><input type='hidden' name='value' value=''>"
                        }
                        def globalEnv = __cdFirstGlobalEnvVars()
                        def path = __cdDbConfigPath(globalEnv)
                        if (!path) {
                            return "<p style='margin:4px 0; color:red'><b>PER_MODULE_DB_JSON:</b> Set <b>JENKINS_CD_DB_CONFIG_FILE</b> (Manage Jenkins → Global properties or controller env) to the absolute path of your DB setups JSON.</p><input type='hidden' name='value' value=''>"
                        }
                        def configs = [:]
                        try {
                            def fp = new hudson.FilePath(new java.io.File(path))
                            if (fp.exists()) {
                                configs = __cdNormalizeDbPerModuleMeta(new JsonSlurper().parseText(fp.readToString()))
                            }
                        } catch (Exception ignored) {}
                        if (!configs || configs.isEmpty()) {
                            return "<p style='margin:4px 0; color:red'><b>PER_MODULE_DB_JSON:</b> Could not load DB setup names from <code>" + path + "</code>. Fix <b>JENKINS_CD_DB_CONFIG_FILE</b> path on the controller and ensure the file is valid JSON.</p><input type='hidden' name='value' value=''>"
                        }
                        def setupNames = configs.keySet().sort()
                        def setupMeta = setupNames.collectEntries { name ->
                            def cfg = configs[name] instanceof Map ? configs[name] : [:]
                            [(name): [user: cfg.DB_USER?.toString() ?: '', credentialId: cfg.credential_id?.toString() ?: '']]
                        }
                        def setupMetaJson = JsonOutput.toJson(setupMeta)
                        def targetJson = JsonOutput.toJson(targets)
                        def setupsB64 = Base64.getEncoder().encodeToString(setupMetaJson.getBytes(StandardCharsets.UTF_8))
                        def modsB64 = Base64.getEncoder().encodeToString(targetJson.getBytes(StandardCharsets.UTF_8))
                        def esc = { v ->
                            (v == null ? '' : v.toString())
                                .replace('&', '&amp;')
                                .replace('<', '&lt;')
                                .replace('>', '&gt;')
                                .replace('"', '&quot;')
                                .replace("'", '&#39;')
                        }
                        def pmdbUid = java.util.UUID.randomUUID().toString().replace('-', '').take(16)
                        def msg = "<div class='cd-pmdb-widget' data-cd-pmdb-id='" + esc(pmdbUid) + "'>"
                        msg += "<textarea readonly class='cd-pmdb-setups-b64' tabindex='-1' aria-hidden='true' style='position:absolute;left:-9999px;width:1px;height:1px;opacity:0' rows='1' cols='1'>" + setupsB64 + "</textarea>"
                        msg += "<textarea readonly class='cd-pmdb-mods-b64' tabindex='-1' aria-hidden='true' style='position:absolute;left:-9999px;width:1px;height:1px;opacity:0' rows='1' cols='1'>" + modsB64 + "</textarea>"
                        msg += "<p style='margin:0 0 8px 0;color:#666;font-size:12px'>DB setup + credentials per PRIMARY_DB/CUSTOM_DB module — Generate JSON, copy, paste into PER_MODULE_DB_JSON_SUBMITTED.</p>"
                        msg += "<table style='width:100%; border-collapse:collapse; border:1px solid #ccc'>"
                        msg += "<tr style='background:#eee'><th style='padding:6px; text-align:left'>Module</th><th style='padding:6px; text-align:left'>DB setup</th><th style='padding:6px; text-align:left'>Credentials</th></tr>"
                        targets.each { mid ->
                            msg += "<tr data-mid='" + esc(mid) + "'>"
                            msg += "<td style='padding:6px'><b>" + esc(mid) + "</b></td>"
                            msg += "<td style='padding:6px'><select class='pmdb-setup' data-mid='" + esc(mid) + "' style='width:100%'>"
                            setupNames.eachWithIndex { setupName, idx ->
                                def sel = (idx == 0) ? " selected" : ""
                                msg += "<option value='" + esc(setupName) + "'" + sel + ">" + esc(setupName) + "</option>"
                            }
                            msg += "</select></td>"
                            msg += "<td style='padding:6px'>"
                            msg += "<div class='pmdb-creds-block'><table><tr>"
                            msg += '<td style=\\'padding-right:10px\\'><b>User:</b><br><input type=\\'text\\' class=\\'pmdb-user\\'></td>'
                            msg += '<td><b>Password:</b><br><input type=\\'password\\' class=\\'pmdb-pwd\\'></td>'
                            msg += "</tr></table></div>"
                            msg += "</td></tr>"
                        }
                        msg += "</table>"
                        def pmdbGenJs = '(function(b){var w=b.closest(".cd-pmdb-widget");if(!w)return;var t2=w.querySelector("textarea.cd-pmdb-mods-b64");function J(tx){try{return JSON.parse(atob((tx&&tx.value||"").trim().replace(/\\\\s/g,"")))}catch(e){return null}}var MODS=J(t2);if(!Array.isArray(MODS))MODS=[];var P={},i,j,m,R,rows,sid,st,sel,nu,np,usr,pw,json;for(i=0;i<MODS.length;i++){m=MODS[i];R=null;rows=w.querySelectorAll("tr[data-mid]");for(j=0;j<rows.length;j++){if(rows[j].getAttribute("data-mid")===String(m)){R=rows[j];break}}if(!R)continue;sel=R.querySelector(".pmdb-setup");nu=R.querySelector(".pmdb-user");np=R.querySelector(".pmdb-pwd");sid=sel&&sel.value?String(sel.value).trim():"";usr=nu?nu.value:"";pw=np?np.value:"";if(sid)P[m]={setupId:sid,credentials:{type:"NEW",user:usr,pwd:pw}}}json=JSON.stringify(P);window.__cdPmdbLastPayloadJson=json;function M(s){try{var o=JSON.parse(s||"{}"),c=JSON.parse(JSON.stringify(o));Object.keys(c).forEach(function(k){if(c[k]&&c[k].credentials)c[k].credentials.pwd="***"});return JSON.stringify(c)}catch(e){return s}}w.querySelectorAll("textarea.cd-pmdb-json-preview").forEach(function(t){t.value=M(json)});st=w.querySelector(".cd-pmdb-gen-status");if(st){st.textContent=(json==="{}"||json==="")?"No rows exported.":"Preview OK.";st.style.color=(json==="{}"||json==="")?"#c00":"#080"}})(this)'
                        def pmdbCopyJs = '(function(){var r=window.__cdPmdbLastPayloadJson||"{}";function F(s){var t=document.createElement("textarea");t.value=s;t.style.position="fixed";t.style.left="-9999px";document.body.appendChild(t);t.focus();t.select();try{document.execCommand("copy")}catch(e){}document.body.removeChild(t)}if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(r).catch(function(){F(r)})}else{F(r)}})()'
                        def escJsAttr = { s -> s == null ? "" : s.toString().replace("'", "&#39;") }
                        msg += "<p style='margin:10px 0 6px 0'><input type='button' class='cd-pmdb-generate-json-btn' value='Generate JSON' style='padding:8px 14px;cursor:pointer;font-weight:bold' onclick='" + escJsAttr(pmdbGenJs) + "' /> <span class='cd-pmdb-gen-status' style='margin-left:10px;color:#666'></span></p>"
                        msg += "<p style='margin:4px 0 4px 0;font-size:12px'><b>Preview</b> (passwords masked)</p>"
                        msg += "<textarea readonly class='cd-pmdb-json-preview' rows='6' placeholder='Click Generate JSON for preview…' style='width:100%;box-sizing:border-box;font-family:monospace;font-size:12px;background:#f9f9f9;border:1px solid #ccc'></textarea>"
                        msg += "<p style='margin:6px 0'><input type='button' class='cd-pmdb-copy-json-btn' value='Copy JSON to clipboard' style='padding:6px 12px;cursor:pointer' onclick='" + escJsAttr(pmdbCopyJs) + "' /></p>"
                        msg += "<input type='hidden' name='value' value=''>"
                        msg += "</div>"
                        return msg
                        } catch (Throwable __cdPmdbErr) {
                            def _em = (__cdPmdbErr.message ?: __cdPmdbErr.class.name).toString().replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')
                            return "<p style='margin:4px 0;color:#b00020'><b>PER_MODULE_DB_JSON</b> runtime error (caught inside script): " + _em + "</p>" +
                                "<p style='margin:4px 0;color:#666;font-size:12px'>Class: " + __cdPmdbErr.getClass().getName() + "</p>" +
                                "<p style='margin:4px 0;color:#666;font-size:12px'>Controller log: search for <b>Error executing script for dynamic parameter</b> / <b>PER_MODULE_DB_JSON</b> if this box was replaced by a generic plugin error.</p>" +
                                "<input type='hidden' name='value' value=''>"
                        }
        """
    }

    /**
     * Uno-Choice {@link org.biouno.unochoice.model.GroovyScript} fallback when {@link #perModuleDbJsonFormattedHtml} fails to compile or evaluate.
     * Must stay minimal (no MODULE_SELECTED, no large HTML) so it always runs.
     */
    static String perModuleDbJsonFallbackGroovy() {
        return 'return """<div style="border:2px solid #b00020;padding:12px;margin:8px 0;background:#fff5f5;font-family:sans-serif;max-width:900px">' +
            '<p style="margin:0 0 10px 0;font-size:15px"><b>PER_MODULE_DB_JSON — primary script failed</b></p>' +
            '<p style="margin:0 0 10px 0">Active Choices (Uno-Choice) is showing this <b>fallback</b> because the main Groovy script for this parameter did not return a value successfully (compile error, runtime exception, or approval issue).</p>' +
            '<p style="margin:0 0 10px 0"><b>What to do:</b> On the Jenkins controller, open the log and search for <code>Error executing script for dynamic parameter</code> and <code>PER_MODULE_DB_JSON</code> — the stack trace names the real cause (e.g. <code>MultipleCompilationErrorsException</code>).</p>' +
            '<p style="margin:0 0 10px 0"><b>Typical fixes:</b> ensure the shared library revision used when <code>cdPipelineParameters</code> ran matches your fix; re-run the pipeline stage that registers job parameters; approve any pending In-process Script Approval entries for this script.</p>' +
            '<p style="margin:0;font-size:12px;color:#555">Hidden parameter value is intentionally empty (not <code>{}</code>) — deploy uses <b>PER_MODULE_DB_JSON_SUBMITTED</b> only.</p>' +
            '</div><input type="hidden" name="value" value="" />"""'
    }

    static String appServerCascadeFromServersJson() {
        """
                        import groovy.json.JsonSlurper
                        import jenkins.model.Jenkins
                        import hudson.slaves.EnvironmentVariablesNodeProperty
                        ${activeChoiceRuntimeHelpers()}
                        def configPath = __cdEnvLookupAppServer('JENKINS_CD_SERVERS_CONFIG_FILE')
                        if (configPath) {
                            try {
                                def fp = new hudson.FilePath(new java.io.File(configPath))
                                if (fp.exists()) {
                                    def textContent = fp.readToString()
                                    def json = new groovy.json.JsonSlurper().parseText(textContent)
                                    def servers = json.servers
                                    if (servers instanceof List && !servers.isEmpty()) {
                                        return servers.collect { it.name?.toString()?.trim() }.findAll { it }
                                    }
                                }
                            } catch (Exception ignored) { }
                        }
                        return ['[ERROR] APP_SERVER: No servers loaded. Set JENKINS_CD_SERVERS_CONFIG_FILE (Manage Jenkins → Global properties) to a controller-readable JSON file with a servers[] list.']
        """
    }

    static String globalDbServerCascade(String perDbJsonForScripts) {
        """
                        import groovy.json.JsonSlurper
                        import jenkins.model.Jenkins
                        import hudson.slaves.EnvironmentVariablesNodeProperty
                        ${activeChoiceRuntimeHelpers()}
                        def __msDb = null
                        try { __msDb = MODULE_SELECTED } catch (Throwable __ignored) { __msDb = null }
                        def selectedModsDb = __cdSelectedModulesLower(__msDb)
                        def specDb = new groovy.json.JsonSlurper().parseText('${perDbJsonForScripts}') as Set
                        def showDbFields = !selectedModsDb.intersect(specDb).isEmpty()
                        if (!showDbFields) return ['[HIDDEN] Shared Module (Reusing CC DB Context)']
                        def needsGlobalDb = selectedModsDb.any { !(it in specDb) }
                        if (!needsGlobalDb && !selectedModsDb.isEmpty()) {
                            return ['[HIDDEN] Use PER_MODULE_DB_JSON + PER_MODULE_DB_JSON_SUBMITTED for registry PRIMARY_DB/CUSTOM_DB modules']
                        }

                        def globalEnv = __cdFirstGlobalEnvVars()
                        def path = __cdDbConfigPath(globalEnv)

                        if (path) {
                            try {
                                def fp = new hudson.FilePath(new java.io.File(path))
                                if (fp.exists()) {
                                    def textContent = fp.readToString()
                                    if (!textContent || textContent.trim().isEmpty()) return ['[ERROR] File exists but is empty']
                                    try {
                                        def j = new groovy.json.JsonSlurper().parseText(textContent)
                                        def configs = __cdNormalizeDbFullGlobal(j)
                                        if (configs && !configs.isEmpty()) return configs.keySet().sort()
                                        return ['[ERROR] No DB setups found in config: ' + path]
                                    } catch (Exception parseEx) {
                                        return ['[ERROR] JSON Parse error: ' + parseEx.message.take(60) + '... length: ' + textContent.length()]
                                    }
                                } else {
                                    return ['[ERROR] File does not exist on Master disk: ' + path]
                                }
                            } catch (Exception e) { 
                                return ['[ERROR] Exception: ' + e.getClass().getName() + ' - ' + e.message.take(60)] 
                            }
                        }
                        return ['[ERROR] JENKINS_CD_DB_CONFIG_FILE is not set (Manage Jenkins → Global properties or controller env).']
        """
    }

    /**
     * Browser row-visibility script for DB_CREDENTIALS_INPUT. Built in Java (not nested Groovy quotes) so the
     * Active Choice compiler never sees ambiguous {@code +\'} or {@code /regex/} inside {@code rv.append('...')}.
     * <p><b>Important:</b> several registry modules can list the same parameter (e.g. {@code PER_MODULE_DB_JSON}).
     * Visibility must be the <b>OR</b> of “any driving module is selected”; a naive per-module loop would hide
     * the row after the last unselected module (e.g. only {@code cc} selected but {@code icms} also maps the same param).
     */
    private static String dbCredentialsRowVisibilityScript(String rawJson, String ccSetupModuleIdRaw) {
        def jsonLit = sqLit(rawJson ?: '{}')
        def ccEsc = (ccSetupModuleIdRaw ?: '').toString().replace('\\', '\\\\').replace("'", "\\'")
        def sb = new StringBuilder()
        sb.append('(function(){var MAP=JSON.parse(').append(jsonLit).append(');')
        sb.append('var CC_SETUP_MOD=\'' ).append(ccEsc).append('\';')
        sb.append('function cs(t){if(!t)return"";t=String(t);var r="",i,sp=0,c;for(i=0;i<t.length;i++){c=t.charCodeAt(i);')
        sb.append('if(c===32||c===9||c===10||c===13){if(!sp&&r.length){r=r+" ";sp=1}}else{r=r+String.fromCharCode(c);sp=0}}return r.trim();}')
        sb.append('function lt(el){return cs((el&&el.textContent)||"");}')
        sb.append('function rowForParam(pname){var pu=pname.toUpperCase();function m(s){if(!s)return false;s=cs(String(s));')
        sb.append('return s===pname||s.toUpperCase()===pu||s.indexOf(pname+" ")===0||s.toUpperCase().indexOf(pu+" ")===0;}')
        sb.append('function scan(el){if(!el)return false;var L=String(el.textContent||"").split(String.fromCharCode(10));')
        sb.append('for(var k=0;k<L.length;k++){var x=cs(L[k]);if(x&&m(x))return true;}return false;}')
        sb.append('var i,tr,c0,els=document.querySelectorAll("td.setting-name,.jenkins-form-label,label.jenkins-label,label,.setting-name,h2");')
        sb.append('for(i=0;i<els.length;i++){var e=els[i];if(scan(e)){tr=e.closest("tr");if(tr)return tr;var w=e.closest(".jenkins-form-item,.jenkins-form-row");if(w)return w.closest("tr")||w;}}')
        sb.append('var trs=document.querySelectorAll("#parameters tr,table.parameters tr,.jenkins-params tr");')
        sb.append('for(i=0;i<trs.length;i++){tr=trs[i];c0=tr.querySelector("td.setting-name")||(tr.cells&&tr.cells[0]);if(c0&&scan(c0))return tr;}')
        sb.append('trs=document.querySelectorAll("tr");')
        sb.append('for(i=0;i<trs.length;i++){tr=trs[i];c0=tr.querySelector("td:first-child,td.setting-name")||(tr.cells&&tr.cells[0]);if(c0&&scan(c0))return tr;}return null;}')
        sb.append('function setRowVisible(el,show){if(!el)return;if(show){el.style.removeProperty("display");el.style.display="";}else{el.style.setProperty("display","none","important");}}')
        sb.append('function moduleSelectedBlock(){var els=document.querySelectorAll("td.setting-name,.jenkins-form-label,label.jenkins-label,label,.setting-name");')
        sb.append('for(var i=0;i<els.length;i++){var raw=(els[i].textContent||"").toUpperCase();')
        sb.append('if(raw.indexOf("MODULE_SELECTED")>=0||raw.indexOf("SELECT INSTALLER")>=0){')
        sb.append('var row=els[i].closest("tr");if(row&&row.cells&&row.cells.length>1)return row.cells[1];')
        sb.append('var pane=els[i].closest(".jenkins-form-item,.jenkins-form-row,.parameter-container");')
        sb.append('return pane||row||els[i].parentElement;}}return null;}')
        sb.append('function selectedMods(){var o=[],seen={};function add(v){v=(v||"").toString().toLowerCase().trim();')
        sb.append('if(!v||v==="on")return;if(seen[v])return;seen[v]=1;o.push(v);}')
        sb.append('var blk=moduleSelectedBlock();')
        sb.append('if(blk){blk.querySelectorAll("input[type=checkbox]:checked").forEach(function(cb){add(cb.value);});')
        sb.append('blk.querySelectorAll("select").forEach(function(sel){')
        sb.append('if(sel.multiple){Array.prototype.forEach.call(sel.options,function(opt){if(opt.selected)add(opt.value);});}')
        sb.append('else if(sel.selectedOptions&&sel.selectedOptions.length){Array.prototype.forEach.call(sel.selectedOptions,function(opt){add(opt.value);});}')
        sb.append('else if(sel.selectedIndex>=0&&sel.options[sel.selectedIndex])add(sel.options[sel.selectedIndex].value);});}')
        sb.append('document.querySelectorAll("input[type=checkbox]:checked").forEach(function(cb){')
        sb.append('var v=(cb.value||"").toString().toLowerCase().trim();var nm=(cb.name||cb.id||"").toUpperCase();')
        sb.append('if(nm.indexOf("MODULE_SELECTED")>=0){add(v);return;}')
        sb.append('var row=cb.closest("tr"),lab=row&&row.querySelector("td.setting-name");var ltv=(lab&&lab.textContent||"").toUpperCase();')
        sb.append('if(ltv.indexOf("MODULE_SELECTED")>=0||ltv.indexOf("SELECT INSTALLER")>=0){add(v);}});')
        sb.append('document.querySelectorAll("select").forEach(function(sel){var nm=(sel.name||sel.id||"").toUpperCase();')
        sb.append('if(nm.indexOf("MODULE_SELECTED")<0)return;if(sel.multiple){Array.prototype.forEach.call(sel.options,function(opt){if(opt.selected)add(opt.value);});}')
        sb.append('else if(sel.selectedOptions){Array.prototype.forEach.call(sel.selectedOptions,function(opt){add(opt.value);});}});return o;}')
        sb.append('function ccGreyCcPortRow(tr,grey){if(!tr)return;var inp=tr.querySelector("input.setting-input,input.jenkins-input,input[name=value],input[type=text]");')
        sb.append('if(!inp){var c=tr.querySelectorAll("input");if(c&&c.length){inp=c[0];}}if(inp){inp.disabled=!!grey;inp.readOnly=!!grey;')
        sb.append('inp.style.opacity=grey?"0.65":"";inp.style.background=grey?"#f5f5f5":"";}tr.style.opacity=grey?"0.72":"";}')
        sb.append('function ccPortNeedsInput(sel){var m=(CC_SETUP_MOD||"cc").toLowerCase();return sel.indexOf(m)>=0;}')
        sb.append('function sync(){var sel=selectedMods();var __pm=rowForParam("PER_MODULE_DB_JSON"),__cc=rowForParam("CC_PORT");')
        sb.append('if(__pm){__pm.style.removeProperty("display");__pm.style.display="";}')
        sb.append('if(__cc){__cc.style.removeProperty("display");__cc.style.display="";var need=ccPortNeedsInput(sel);ccGreyCcPortRow(__cc,!need);}')
        sb.append('var vis={};Object.keys(MAP).forEach(function(mod){var sm=sel.indexOf(mod)>=0;(MAP[mod]||[]).forEach(function(pname){vis[pname]=(vis[pname]||false)||sm;});});Object.keys(vis).forEach(function(pname){setRowVisible(rowForParam(pname),vis[pname]);});')
        sb.append('["APP_SERVER","DB_SERVER"].forEach(function(pname){var tr=rowForParam(pname),selEl=tr&&tr.querySelector("select"),v="";')
        sb.append('if(selEl&&selEl.options&&selEl.selectedIndex>=0)v=(selEl.options[selEl.selectedIndex].text||selEl.value||"").trim();')
        sb.append('if(tr){if(v.indexOf("[HIDDEN]")===0)setRowVisible(tr,false);else setRowVisible(tr,true);}});')
        sb.append('var trD=rowForParam("DB_CREDENTIALS_INPUT");if(trD){var tx=(trD.textContent||"");')
        sb.append('if(tx.indexOf("use per-module")>=0||tx.indexOf("registry per-DB")>=0)setRowVisible(trD,false);else setRowVisible(trD,true);}}')
        sb.append('function deb(){clearTimeout(window.__cdRvT);window.__cdRvT=setTimeout(sync,40);}')
        sb.append('if(!window.__cdRowVisBound){window.__cdRowVisBound=1;document.addEventListener("change",function(ev){var t=ev.target;')
        sb.append('if(t&&(t.type==="checkbox"||((t.name||"").toUpperCase().indexOf("MODULE")>=0&&t.type!=="text")))deb();},true);')
        sb.append('if(typeof MutationObserver!=="undefined"){var root=document.querySelector("#main-panel,.jenkins-pane,.parameters,.jenkins-form,form")||document.body;')
        sb.append('try{new MutationObserver(function(){deb();}).observe(root,{childList:true,subtree:true});}catch(e){}}')
        sb.append('[0,50,100,250,500,1000,2000,3500,5000].forEach(function(ms){setTimeout(sync,ms);});}sync();})();')
        sb.toString()
    }

    static String globalDbCredentialsFormattedHtml(String jsMapFromRegistry, String ccPortMidEscJs, String perDbJsonForScripts) {
        """
                        import groovy.json.JsonSlurper
                        import jenkins.model.Jenkins
                        import hudson.slaves.EnvironmentVariablesNodeProperty
                        ${activeChoiceRuntimeHelpers()}
                        def __msCr = null
                        try { __msCr = MODULE_SELECTED } catch (Throwable __ignored) { __msCr = null }
                        def selectedModsCr = __cdSelectedModulesLower(__msCr)
                        def specCr = new groovy.json.JsonSlurper().parseText('${perDbJsonForScripts}') as Set
                        def showDbFields = !selectedModsCr.intersect(specCr).isEmpty()
                        if (!showDbFields) {
                            return "<p style='margin:4px 0; color:#666'><i>Shared module selected - Reusing previously saved CC DB configuration. DB credentials are hidden.</i></p>" +
                                   "<input type='hidden' name='value' value='SHARED_MODULE_NO_INPUT'>"
                        }
                        def needsGlobalDbCr = selectedModsCr.any { !(it in specCr) }
                        if (!needsGlobalDbCr && !selectedModsCr.isEmpty()) {
                            return "<p style='margin:4px 0; color:#666'><i>Only registry per-DB modules selected — use <b>PER_MODULE_DB_JSON</b> above.</i></p>" +
                                   "<input type='hidden' name='value' value='SHARED_MODULE_NO_INPUT'>"
                        }

                        def setupId = (DB_SERVER != null ? DB_SERVER.toString().trim() : '') ?: ''
                        
                        def existingUser = ''
                        def globalEnv = __cdFirstGlobalEnvVars()
                        def path = __cdDbConfigPath(globalEnv)

                        if (path && setupId && !setupId.startsWith('[ERROR]')) {
                            try {
                                def fp = new hudson.FilePath(new java.io.File(path))
                                if (fp.exists()) {
                                    def textContent = fp.readToString()
                                    def j = new groovy.json.JsonSlurper().parseText(textContent)
                                    def configs = __cdNormalizeDbCredentials(j)
                                    def selectedConfig = configs[setupId]
                                    if (selectedConfig && selectedConfig.DB_USER) {
                                        existingUser = selectedConfig.DB_USER.toString()
                                    }
                                }
                            } catch (Exception ignored) { }
                        }

                        def existingChecked = existingUser ? "checked" : "disabled"
                        def newChecked = existingUser ? "" : "checked"
                        def defaultJson = existingUser ? '{"type":"EXISTING","user":"' + existingUser + '","pwd":""}' : '{"type":"NEW","user":"","pwd":""}'

                        def msg = "<div>"
                        msg += "<p style='margin:0 0 8px 0;color:#666;font-size:12px'>Global DB credentials for this deploy.</p>"
                        msg += "<div style='margin-bottom:10px;'>"
                        msg += "<label style='margin-right:15px;'><input type='radio' id='DB_USER_TYPE_EXISTING' name='DB_USER_TYPE_SEL' value='EXISTING' " + existingChecked + "> Use Existing User</label>"
                        msg += "<label><input type='radio' id='DB_USER_TYPE_NEW' name='DB_USER_TYPE_SEL' value='NEW' " + newChecked + "> Create/Use New User & Password</label>"
                        msg += "</div>"
                        msg += "<div style='margin-bottom:10px;'>"
                        msg += "<b>Existing User:</b> <input type='text' id='DB_USER_EXISTING' value='" + existingUser + "' readonly style='background:#f0f0f0; border:1px solid #ccc; padding:3px;'>"
                        msg += " <i>(Password resolved from configuration)</i>"
                        msg += "</div>"
                        msg += "<div style='margin-bottom:10px;'>"
                        msg += "<table><tr>"
                        msg += "<td style='padding-right:10px;'><b>New User:</b><br><input type='text' id='DB_USER_NEW_INPUT'></td>"
                        msg += "<td><b>Password:</b><br><input type='password' id='DB_PASSWORD_NEW_INPUT'></td>"
                        msg += "</tr></table>"
                        msg += "</div>"
                        msg += "<input type='hidden' name='value' id='DB_CREDENTIALS_JSON' value='" + defaultJson + "'>"
                        msg += '''<script>
                            (function() {
                                function updateDbJson() {
                                    var existingRadio = document.getElementById('DB_USER_TYPE_EXISTING');
                                    var newRadio = document.getElementById('DB_USER_TYPE_NEW');
                                    var existingInput = document.getElementById('DB_USER_EXISTING');
                                    var newUserInput = document.getElementById('DB_USER_NEW_INPUT');
                                    var newPasswordInput = document.getElementById('DB_PASSWORD_NEW_INPUT');
                                    var hiddenInput = document.getElementById('DB_CREDENTIALS_JSON');
                                    if (!hiddenInput) return;

                                    var useNew = newRadio && newRadio.checked;
                                    var payload = {
                                        type: useNew ? 'NEW' : 'EXISTING',
                                        user: useNew ? (newUserInput ? newUserInput.value : '') : (existingInput ? existingInput.value : ''),
                                        pwd: useNew ? (newPasswordInput ? newPasswordInput.value : '') : ''
                                    };
                                    hiddenInput.value = JSON.stringify(payload);
                                }

                                var existingRadio = document.getElementById('DB_USER_TYPE_EXISTING');
                                var newRadio = document.getElementById('DB_USER_TYPE_NEW');
                                var newUserInput = document.getElementById('DB_USER_NEW_INPUT');
                                var newPasswordInput = document.getElementById('DB_PASSWORD_NEW_INPUT');

                                if (existingRadio) {
                                    existingRadio.onclick = updateDbJson;
                                    existingRadio.onchange = updateDbJson;
                                }
                                if (newRadio) {
                                    newRadio.onclick = updateDbJson;
                                    newRadio.onchange = updateDbJson;
                                }
                                if (newUserInput) {
                                    newUserInput.onfocus = function() {
                                        if (newRadio) newRadio.checked = true;
                                        updateDbJson();
                                    };
                                    newUserInput.oninput = updateDbJson;
                                }
                                if (newPasswordInput) {
                                    newPasswordInput.onfocus = function() {
                                        if (newRadio) newRadio.checked = true;
                                        updateDbJson();
                                    };
                                    newPasswordInput.oninput = updateDbJson;
                                }

                                updateDbJson();
                            })();
                        </script>'''
                        msg += "</div>"
                        return msg
        """
    }
}
