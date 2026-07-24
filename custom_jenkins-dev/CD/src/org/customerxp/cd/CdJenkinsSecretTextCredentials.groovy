package org.customerxp.cd

import java.util.Collections

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import hudson.security.ACL
import hudson.util.Secret
import jenkins.model.Jenkins
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl

/**
 * P4-3: Store DB passwords only in Jenkins Credentials (Secret text), not on disk.
 */
@SuppressWarnings(['unused'])
final class CdJenkinsSecretTextCredentials {

    private CdJenkinsSecretTextCredentials() {}

    /** Safe fragment for a Jenkins credential id (alphanumeric, dash, underscore). */
    static String sanitizeCredentialIdFragment(String name) {
        def s = name?.toString()?.trim() ?: 'setup'
        def safe = s.replaceAll(/[^a-zA-Z0-9_-]/, '-').replaceAll(/-+/, '-')
        def out = safe.toLowerCase()
        if (out.length() > 180) {
            return out.substring(0, 180)
        }
        out
    }

    /**
     * Creates or replaces a global Secret text credential. Caller must have permission to manage credentials.
     */
    static void upsertSecretText(String credentialId, String secretPlaintext, String description) {
        if (!credentialId?.trim()) {
            throw new IllegalArgumentException('credentialId required')
        }
        Jenkins j = Jenkins.getInstance()
        if (j == null) {
            throw new IllegalStateException('Jenkins controller instance unavailable')
        }
        List<StringCredentials> list = CredentialsProvider.lookupCredentials(
            StringCredentials.class,
            j,
            ACL.SYSTEM,
            Collections.emptyList())
        StringCredentials old = list.find { it.id == credentialId }
        StringCredentials neu = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            credentialId,
            (description ?: 'CD DB password').toString(),
            Secret.fromString(secretPlaintext ?: ''))
        def store = SystemCredentialsProvider.getInstance().getStore()
        Domain domain = Domain.global()
        if (old != null) {
            store.updateCredentials(domain, old, neu)
        } else {
            store.addCredentials(domain, neu)
        }
    }
}
