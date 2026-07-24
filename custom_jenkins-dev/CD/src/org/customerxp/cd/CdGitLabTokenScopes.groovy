package org.customerxp.cd

/**
 * P4-4: Reference only — GitLab enforces token scopes when the token is created; Jenkins cannot narrow scopes at runtime.
 *
 * <p>The CD pipeline Active Choice script ({@code CdJobParameterScripts}) calls exactly one API surface per module:
 * {@code GET /api/v4/projects/:id/repository/tags} (pagination query params only). No POST/PUT/DELETE, no GraphQL.
 *
 * <p><strong>Recommended PAT (classic):</strong> scope {@link #MINIMUM_CLASSIC_PAT_SCOPE_READ_ONLY} ({@code read_api}).
 * Avoid {@code api} (full API write access), {@code write_repository}, {@code sudo}, etc., unless another integration requires them.
 *
 * <p><strong>Project / group access tokens:</strong> grant only read access to repositories that host CD modules; narrow role in GitLab UI.
 *
 * <p>Jenkins wires the token via {@code CX_GITLAB_TOKEN_CREDENTIAL_ID} — see {@code cdPipelineJobUiConfig}.
 *
 * @see <a href="https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html">GitLab personal access tokens</a>
 */
@SuppressWarnings(['unused'])
final class CdGitLabTokenScopes {

    private CdGitLabTokenScopes() {}

    /** Minimum classic PAT scope for read-only REST API use including listing repository tags. */
    static final String MINIMUM_CLASSIC_PAT_SCOPE_READ_ONLY = 'read_api'

    /** Documentary — pipeline does not use registry/package/project DELETE or repo writes via GitLab API. */
    static final String API_TAGS_RELATIVE_PATH = '/repository/tags'
}
