package org.customerxp.cd

/**
 * Resolves Nexus / CD script URLs and module artifact bases from Jenkins {@code env} only —
 * no hardcoded hosts or path segments.
 */
final class CdNexusEnvUrls {

    private static String trim(def v) {
        v?.toString()?.trim() ?: ''
    }

    static String joinUrl(String base, String path) {
        if (!trim(base) || !trim(path)) {
            return ''
        }
        "${base.replaceAll(/\/+$/, '')}/${path.replaceAll(/^\/+|\/+$/, '')}"
    }

    /**
     * GitLab release tags often include a leading {@code v} (e.g. {@code vClari5.Neo.5}); Nexus
     * package-images paths use the tag without it ({@code Clari5.Neo.5}). UI / {@code MODULE_VERSION}
     * keeps the GitLab form; use this only when building Nexus .tgz URLs (pre-download).
     */
    static String nexusArtifactVersionTag(String versionTag) {
        def v = versionTag?.toString()?.trim() ?: ''
        if (v.length() > 1 && v.startsWith('v')) {
            return v.substring(1)
        }
        return v
    }

    /** Host-style Nexus base, e.g. {@code https://nexus.example.com} — {@code JENKINS_NEXUS_URL} only. */
    static String nexusHostBase(def env) {
        trim(env?.JENKINS_NEXUS_URL)
    }

    /** Path under host for raw/script artifacts (no leading slash), e.g. {@code repository/resources/scripts}. */
    static String scriptsUriPath(def env) {
        trim(env?.JENKINS_NEXUS_SCRIPTS_URI_PATH)
    }

    /**
     * Path under host for module .tgz layout (no leading slash), e.g. {@code repository/package-images/modules}.
     * Used with {@link #nexusHostBase} for {@link #modulePackageBase}.
     */
    static String moduleArtifactPath(def env) {
        trim(env?.JENKINS_NEXUS_MODULE_ARTIFACT_PATH)
    }

    /**
     * Directory URL prefix for module packages (no trailing slash): {@code JENKINS_NEXUS_URL} + {@code JENKINS_NEXUS_MODULE_ARTIFACT_PATH}.
     */
    static String modulePackageBase(def env) {
        def host = nexusHostBase(env)
        def ap = moduleArtifactPath(env)
        if (host && ap) {
            return joinUrl(host, ap)
        }
        return ''
    }

    static String ccSetupScriptUrl(def env) {
        def host = nexusHostBase(env)
        def sp = scriptsUriPath(env)
        def fn = trim(env?.JENKINS_NEXUS_CC_SETUP_SCRIPT_NAME)
        if (host && sp && fn) {
            return "${joinUrl(joinUrl(host, sp), fn)}"
        }
        return ''
    }

    static String moduleDeployScriptUrl(def env) {
        def host = nexusHostBase(env)
        def sp = scriptsUriPath(env)
        def fn = trim(env?.JENKINS_NEXUS_MODULE_DEPLOY_SCRIPT_NAME)
        if (host && sp && fn) {
            return "${joinUrl(joinUrl(host, sp), fn)}"
        }
        return ''
    }

    static String moduleJsonTarUrl(def env) {
        def host = nexusHostBase(env)
        def sp = scriptsUriPath(env)
        def fn = trim(env?.JENKINS_NEXUS_MODULE_JSON_ARCHIVE_NAME)
        if (host && sp && fn) {
            return "${joinUrl(joinUrl(host, sp), fn)}"
        }
        return ''
    }
}
