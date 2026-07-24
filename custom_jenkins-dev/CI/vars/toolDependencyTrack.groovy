// vars/toolDependencyTrack.groovy

// Dependency-Track publish (SBOM path resolution lives in vars/sbomDiscovery.groovy; DefectDojo — toolDefectdojo.groovy).



Object securityToolsCommonScript() {

    loadSharedLibVarScript('securityToolsCommon')

}



Object sbomDiscoveryScript() {

    def s = loadSharedLibVarScript('sbomDiscovery')

    if (!s) {

        error('❌ sbomDiscovery could not be loaded (required for SBOM path resolution).')

    }

    s

}



String getDependencyTrackProjectName(String moduleName, String branchOrTag) {

    if (!moduleName?.trim()) return moduleName

    if (!branchOrTag?.toString()?.trim()) {

        error('❌ getDependencyTrackProjectName: branchOrTag must be set.')

    }

    def versionPart = branchOrTag.toString().trim()

    if (versionPart.startsWith('develop-')) versionPart = versionPart.substring(8)

    else if (versionPart.startsWith('release-')) versionPart = versionPart.substring(8)

    if (versionPart == 'master' || versionPart == 'main' || versionPart == 'develop') return moduleName

    versionPart = versionPart.replaceAll('[^a-zA-Z0-9.\\-]', '-')

    return "${moduleName}-${versionPart}"

}



String getDependencyTrackProjectVersion(String jdkVersion, String branchOrTag) {

    if (!jdkVersion?.toString()?.trim()) {

        error('❌ getDependencyTrackProjectVersion: jdkVersion must be set.')

    }

    if (!branchOrTag?.toString()?.trim()) {

        error('❌ getDependencyTrackProjectVersion: branchOrTag must be set.')

    }

    def cleanJdk = jdkVersion.toString().replaceAll(/^jdk-?/, '').trim()

    def shortVer = securityToolsCommonScript().getNexusShortVersion(branchOrTag)

    return "jdk${cleanJdk}-${shortVer}-SNAPSHOT"

}



/**

 * @return map: dtPublishOk (boolean), fullSbomPath (String or null)

 */

Map publishToDependencyTrackBranch(Map args) {

    def cfg = args.cfg

    String moduleName = args.moduleName?.toString()

    String moduleBranch = args.moduleBranch?.toString()

    String jdkVersion = args.jdkVersion?.toString()

    String namespace = args.namespace?.toString()



    def currentWorkspaceAbsolutePath = pwd()

    def gitOrgForSbom = env.CX_GIT_ORG?.toString()?.trim() ?: cfg?.gitOrg?.toString()?.trim() ?: ''



    def sbom = sbomDiscoveryScript()

    def sbomResolved = sbom.resolveSbomPathForDependencyTrack(currentWorkspaceAbsolutePath, gitOrgForSbom, namespace, moduleName)

    def fullSbomPath = sbomResolved.path



    if (!fullSbomPath) {

        sbom.echoSbomMissForDependencyTrack(currentWorkspaceAbsolutePath, gitOrgForSbom, namespace, moduleName, '', true)

        return [dtPublishOk: false, fullSbomPath: null]

    }



    echo "--- PHASE: Publishing SBOM to Dependency-Track for ${moduleName} ---"

    def dtInstanceName = cfg.dependencyTrackInstance

    def relativeSbomPathForUpload = fullSbomPath.replace(currentWorkspaceAbsolutePath + '/', '')



    try {

        def dtProjectName = getDependencyTrackProjectName(moduleName, moduleBranch)

        def dtProjectVersion = getDependencyTrackProjectVersion(jdkVersion, moduleBranch)

        echo "   Using Dependency-Track instance: ${dtInstanceName}"

        echo "   Project: ${dtProjectName}"

        echo "   Version: ${dtProjectVersion}"

        echo "   SBOM File: ${relativeSbomPathForUpload}"



        dependencyTrackPublisher(

            artifact           : relativeSbomPathForUpload,

            projectName        : dtProjectName,

            projectVersion     : dtProjectVersion,

            synchronous        : true,

            failOnViolationFail: false

        )



        echo "✅ Successfully published SBOM to Dependency-Track for ${moduleName}"

        return [dtPublishOk: true, fullSbomPath: fullSbomPath]

    } catch (Exception e) {

        echo "⚠️ WARNING: Failed to publish SBOM to Dependency-Track for ${moduleName}: ${e.message}"

        echo '⚠️ Build will continue, but Dependency-Track analysis was not performed.'

        return [dtPublishOk: false, fullSbomPath: fullSbomPath]

    }

}



void publishToDependencyTrackTagRebuild(Map args) {

    def cfg = args.cfg

    String moduleName = args.moduleName?.toString()

    String releaseBranchName = args.releaseBranchName?.toString()

    String jdkVersion = args.jdkVersion?.toString()

    String namespace = args.namespace?.toString()



    def currentWorkspaceAbsolutePathForTag = pwd()

    def gitOrgForSbomTag = env.CX_GIT_ORG?.toString()?.trim() ?: cfg?.gitOrg?.toString()?.trim() ?: ''



    def sbomTag = sbomDiscoveryScript()

    def sbomResolvedTag = sbomTag.resolveSbomPathForDependencyTrack(currentWorkspaceAbsolutePathForTag, gitOrgForSbomTag, namespace, moduleName)

    def fullSbomPathForTag = sbomResolvedTag.path



    if (!fullSbomPathForTag) {

        sbomTag.echoSbomMissForDependencyTrack(

            currentWorkspaceAbsolutePathForTag,

            gitOrgForSbomTag,

            namespace,

            moduleName,

            " (${releaseBranchName})",

            false

        )

        return

    }



    echo "--- PHASE: Publishing SBOM to Dependency-Track for ${moduleName} (${releaseBranchName}) ---"

    def dtInstanceName = cfg.dependencyTrackInstance

    def relativeSbomPathForTag = fullSbomPathForTag.replace(currentWorkspaceAbsolutePathForTag + '/', '')



    try {

        def dtProjectNameTag = getDependencyTrackProjectName(moduleName, releaseBranchName)

        def dtProjectVersionTag = getDependencyTrackProjectVersion(jdkVersion, releaseBranchName)

        echo "   Using Dependency-Track instance: ${dtInstanceName}"

        echo "   Project: ${dtProjectNameTag}"

        echo "   Version: ${dtProjectVersionTag}"

        echo "   SBOM File: ${relativeSbomPathForTag}"



        dependencyTrackPublisher(

            artifact           : relativeSbomPathForTag,

            projectName        : dtProjectNameTag,

            projectVersion     : dtProjectVersionTag,

            synchronous        : true,

            failOnViolationFail: false

        )



        echo "✅ Successfully published SBOM to Dependency-Track for ${moduleName} (${releaseBranchName})"

    } catch (Exception e) {

        echo "⚠️ WARNING: Failed to publish SBOM to Dependency-Track for ${moduleName} (${releaseBranchName}): ${e.message}"

        echo '⚠️ Build will continue, but Dependency-Track analysis was not performed.'

    }

}



return this

