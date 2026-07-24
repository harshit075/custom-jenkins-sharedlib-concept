// vars/getModuleChoices.groovy

import java.net.URLEncoder // Keep this for robustness if any non-git URLs needed encoding.

/**
 * Dynamically discovers and returns a list of available modules by querying GitLab repositories.
 * This script securely queries GitLab repositories by setting up temporary .netrc entries
 * before executing git commands.
 *
 * @return A List of strings, where each string is the name of a discovered module (e.g., "platform", "clari5-api").
 * Candidate modules to probe come from {@code env.CX_CORE_MODULE_IDS} and {@code env.CX_INSTALLER_MODULE_IDS} (union, deduped).
 */
def call() {
    def discoveredModules = []

    node { // Enclose the core logic in a 'node' block to ensure Git commands and 'sh' steps are executable.
        def gitSetupSuccess = false // Flag to ensure cleanup

        // --- 1. Environment Variable & Credential Validation ---
        if (!env.JENKINS_GIT_URL || !env.JENKINS_GIT_ORG) {
            error("❌ Required environment variables (JENKINS_GIT_URL, JENKINS_GIT_ORG) for module discovery are not set. Cannot proceed with dynamic module discovery.")
        }

        def gitBaseUrl = env.JENKINS_GIT_URL
        def gitHost = new URL(gitBaseUrl).getHost() // Extract host from URL for .netrc
        def getCredentialsScript = loadSharedLibVarScript('getCredentials')
        if (!getCredentialsScript) {
            error("❌ CRITICAL ERROR: Could not load 'getCredentials' script. Cannot resolve Git credentials for module discovery.")
        }
        def gitCredsId = getCredentialsScript.getGitCredentials()

        // Use withCredentials to get username/password for .netrc and for curl if needed.
        // Also setup .netrc directly within this scope.
        withCredentials([usernamePassword(credentialsId: gitCredsId, usernameVariable: 'G_USR', passwordVariable: 'G_PWD')]) {
            try { // --- Start of .netrc management
                // Create .netrc file securely. G_USR and G_PWD are environment variables now.
                def netrcScriptContent = """#!/bin/bash -ex
echo "machine ${gitHost} login ${G_USR} password ${G_PWD}" > ~/.netrc
chmod 600 ~/.netrc
"""
                writeFile file: "netrc_setup_getModuleChoices.sh", text: netrcScriptContent
                sh "bash netrc_setup_getModuleChoices.sh"
                gitSetupSuccess = true // Mark success
                echo "DEBUG: .netrc setup for module discovery complete."

                // --- 2. Define Discovery Parameters ---
                // The Git root URL *without* credentials for ls-remote, relying on .netrc
                def gitOrg = env.JENKINS_GIT_ORG
                def unauthenticatedGitRootUrl = "https://${gitHost}/${gitOrg}" // NO USER/PASS HERE

                List<String> coreIds = (env.CX_CORE_MODULE_IDS ?: '').split(',').collect { it.trim() }.findAll { it }
                List<String> instIds = (env.CX_INSTALLER_MODULE_IDS ?: '').split(',').collect { it.trim() }.findAll { it }
                def allPotentialModules = (coreIds + instIds).unique().sort()
                if (allPotentialModules.isEmpty()) {
                    error('❌ getModuleChoices: CX_CORE_MODULE_IDS and CX_INSTALLER_MODULE_IDS are both empty. Set them in Jenkins (global or job) to define modules to probe.')
                }

                // --- 3. Dynamic Module Discovery using Git ls-remote (using .netrc) ---
                echo "⚙️ Dynamically discovering modules by probing GitLab repositories..."
                allPotentialModules.each { moduleName ->
                    def moduleFound = false
                    // Now, use unauthenticated URL, `git` will consult ~/.netrc
                    for (ns in ['platform', 'product-modules']) {
                        if (sh(script: "git ls-remote -h \"${unauthenticatedGitRootUrl}/${ns}/${moduleName}.git\" HEAD >/dev/null 2>&1", returnStatus: true, quiet: true) == 0) {
                            discoveredModules << moduleName
                            moduleFound = true
                            break
                        }
                    }

                    if (!moduleFound) {
                        if (moduleName == "cmq-common" && sh(script: "git ls-remote -h \"${unauthenticatedGitRootUrl}/platform/component-utilities/${moduleName}.git\" HEAD >/dev/null 2>&1", returnStatus: true, quiet: true) == 0) {
                            discoveredModules << moduleName
                            moduleFound = true
                        } else if ((moduleName == "cmq-client" || moduleName == "cmq-driver" || moduleName == "nats-client") && sh(script: "git ls-remote -h \"${unauthenticatedGitRootUrl}/platform/component-sdk/${moduleName}.git\" HEAD >/dev/null 2>&1", returnStatus: true, quiet: true) == 0) {
                            discoveredModules << moduleName
                            moduleFound = true
                        } else if (moduleName == "cmq-server" && sh(script: "git ls-remote -h \"${unauthenticatedGitRootUrl}/platform/component/${moduleName}.git\" HEAD >/dev/null 2>&1", returnStatus: true, quiet: true) == 0) {
                            discoveredModules << moduleName
                            moduleFound = true
                        }
                    }
                    if (moduleFound) {
                        echo "DEBUG: Discovered module: '${moduleName}'"
                    } else {
                        echo "DEBUG: Module '${moduleName}' not found in any probed namespaces."
                    }
                }
                echo "✅ Dynamic module discovery complete. Found ${discoveredModules.size()} modules."
                def uniqueSorted = discoveredModules.unique().sort()
                if (!uniqueSorted) {
                    error("❌ Module discovery found no matching repositories. Verify JENKINS_GIT_URL, JENKINS_GIT_ORG, Git credentials, and that probed repos exist.")
                }
                return uniqueSorted
            } catch (Exception e) {
                error("❌ Dynamic module discovery failed: ${e.message}")
            } finally { // --- End of .netrc management cleanup
                if (gitSetupSuccess) {
                    sh "rm -f ~/.netrc"
                    echo "DEBUG: .netrc cleaned up after module discovery."
                }
                // Also clean up temp script file
                sh "rm -f netrc_setup_getModuleChoices.sh"
            }
        } // End withCredentials
    } // End node block
}