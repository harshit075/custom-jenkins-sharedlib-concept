# CustomerXP CI/CD Pipelines

This repository contains Jenkins pipeline definitions and shared-library code for learning, running, and maintaining the CustomerXP CI/CD flow from the GitLab `dev` / `develop` branch.

The code is organized around two areas:

- `CI/` - Continuous Integration pipelines for building modules, running quality checks, publishing reports, and producing artifacts.
- `CD/` - Continuous Deployment pipeline code for deploying installer modules to configured application servers.

> Note: This repository is displayed in GitLab, but the pipeline implementation here is Jenkins-based. There is no `.gitlab-ci.yml` in this repo at the time of writing.

## Repository Structure

```text
CI/
  final-cipipeline.jenkinsfile       Snapshot CI pipeline for selected modules
  release-cipipeline.jenkinsfile     Release CI pipeline with release tag support
  nightly-cipipeline.jenkinsfile     Scheduled nightly snapshot pipeline
  vars/                              Jenkins shared-library steps for CI
  resources/                         CI resource files

CD/
  cd-pipeline.jenkinsfile            Continuous deployment pipeline
  vars-cd/                           Jenkins shared-library steps for CD
  src/org/customerxp/cd/             CD helper classes
  resources/cd/module-registry.json  Module deployment registry
```

## CI Pipeline Overview

CI answers the question: "Can the selected code be built, scanned, and packaged correctly?"

The main CI entry points are:

- `CI/final-cipipeline.jenkinsfile` - snapshot pipeline for selected core and installer modules.
- `CI/release-cipipeline.jenkinsfile` - release pipeline that builds selected modules using release branches and release tags.
- `CI/nightly-cipipeline.jenkinsfile` - scheduled nightly snapshot pipeline for configured core and installer modules.

Common CI responsibilities include:

- Registering Jenkins job parameters dynamically.
- Reading module lists, branch choices, and JDK choices from Jenkins global properties.
- Resolving GitLab branches for selected modules.
- Cleaning Jenkins workspace and optional Git mirrors.
- Building modules in chain order where required.
- Running selected installer modules in parallel where safe.
- Publishing artifacts to Nexus.
- Running optional security and quality tools such as SonarQube, SBOM, Dependency-Track, DefectDojo, JaCoCo, and Gitleaks.
- Sending failure notifications for nightly jobs.

## CD Pipeline Overview

CD answers the question: "Can the selected build artifacts be deployed to the selected environment?"

The main CD entry point is:

- `CD/cd-pipeline.jenkinsfile`

The CD pipeline:

- Verifies required Jenkins and CD environment configuration.
- Registers deployment parameters.
- Reads deployable modules from `CD/resources/cd/module-registry.json`.
- Resolves selected module versions or manual Nexus snapshot inputs.
- Resolves application server and database configuration.
- Prepares deployment environment files.
- Downloads deployment scripts and module metadata from Nexus.
- Runs remote deployment scripts on the selected application server.
- Preserves CC database context for shared database module deployments.

## How To Use The CI Pipelines

### Snapshot CI

Use `CI/final-cipipeline.jenkinsfile` when you want to build selected modules from `develop*` branches.

Typical flow:

1. Open the Jenkins job configured with `final-cipipeline.jenkinsfile`.
2. Run the job once to register or refresh dynamic parameters.
3. Refresh the job page.
4. Choose core modules in `CORE_SELECTED`.
5. Choose installer modules in `INST_SELECTED`.
6. Pick the branch for each selected module.
7. Select the JDK version from `jdk_version`.
8. Start the build.

The pipeline builds core modules and special installer modules in chain order, then builds remaining installer modules in parallel.

### Release CI

Use `CI/release-cipipeline.jenkinsfile` when you want to build release artifacts from `release*` branches.

Typical flow:

1. Open the Jenkins job configured with `release-cipipeline.jenkinsfile`.
2. Run the job once to refresh parameters if needed.
3. Select modules and release branches.
4. Provide `RELEASE_TAG`, or use per-module release tag choices when configured.
5. Select the JDK version.
6. Start the build.

Release CI disables job-level security tools by default in the release execution path and focuses on release-tagged builds.

### Nightly CI

Use `CI/nightly-cipipeline.jenkinsfile` for scheduled snapshot builds.

Typical flow:

1. Configure `JENKINS_CRON_SCHEDULE` in Jenkins global properties.
2. Configure module IDs and branch resolution globals.
3. Configure failure notification recipients.
4. Let Jenkins run the job on schedule, or trigger it manually for validation.

Nightly CI resolves the configured module plan, builds snapshot artifacts, and sends email on failure or unstable results.

## How To Use The CD Pipeline

Use `CD/cd-pipeline.jenkinsfile` when you want to deploy selected module artifacts to an application server.

Typical flow:

1. Open the Jenkins job configured with `CD/cd-pipeline.jenkinsfile`.
2. Run the job once to register or refresh deployment parameters.
3. Refresh the job page.
4. Select one or more modules in `MODULE_SELECTED`.
5. Choose GitLab tags or provide manual Nexus snapshot versions.
6. Select `JDK_VERSION`.
7. Set `DEP_BASE_PATH` for the app server deployment root.
8. Select `APP_SERVER`.
9. Provide DB details when required by the selected module type.
10. Start the deploy job.

Important deployment behavior:

- `cc` is treated as a primary database / platform setup module.
- Shared database modules depend on a previously known CC database context.
- Deployment order is controlled by module metadata such as `deployOrder` and `dependsOn`.
- Server information comes from `JENKINS_CD_SERVERS_CONFIG_FILE`.
- Nexus URL and credentials are required for artifact and script downloads.

## Required Jenkins Configuration

Most configuration should live in Jenkins global properties or folder/job environment variables, not hardcoded in Jenkinsfiles.

Core CI variables include:

- `BUILD_TYPE`
- `JENKINS_GIT_ORG`
- `CX_GIT_HOST` or `JENKINS_GIT_URL`
- `JENKINS_GIT_CREDENTIALS` or `JENKINS_GIT_HTTP_CREDENTIALS_ID`
- `JENKINS_NEXUS_URL`
- `JENKINS_NEXUS_CREDENTIALS`
- `CX_GITLAB_API_URL`
- `CX_GITLAB_ORG`
- `CX_GITLAB_NAMESPACES`
- `CX_GITLAB_TOKEN_CREDENTIAL_ID`
- `CX_CORE_MODULE_IDS`
- `CX_INSTALLER_MODULE_IDS`
- `CX_CHAIN_ORDER`
- `CX_JDK_VERSION_OPTIONS`
- `CX_JDK_VERSION_DEFAULT`

Nightly CI also uses:

- `JENKINS_CRON_SCHEDULE`
- `JENKINS_FAILURE_EMAIL_RECIPIENTS`
- `CX_SEQUENTIAL_INSTALLER_MODULE_IDS` or `CX_CREINSTALLER_MODULES`

Core CD variables include:

- `JENKINS_CD_WORKSPACE`
- `JENKINS_NEXUS_URL`
- `JENKINS_NEXUS_CREDENTIALS`
- `JENKINS_CD_SERVERS_CONFIG_FILE`
- `JENKINS_CD_MODULE_INSTANCE_COUNT`
- `JENKINS_NEXUS_MODULE_ARTIFACT_PATH`
- `JENKINS_NEXUS_SCRIPTS_URI_PATH`
- `JENKINS_NEXUS_CC_SETUP_SCRIPT_NAME`
- `JENKINS_NEXUS_MODULE_DEPLOY_SCRIPT_NAME`
- `JENKINS_CD_CC_DB_CONTEXT_FILE`
- `JENKINS_CD_DB_CONFIG_ALLOWED_PREFIX`

For the full source of truth, review:

- `CI/vars/cxPipelineConfig.groovy`
- `CI/vars/getDefaults.groovy`
- `CD/vars-cd/verifyCdPipelineEnv.groovy`
- `CD/vars-cd/getCDDefaults.groovy`

## Learning Path

If you are learning this CI/CD setup, follow this order:

1. Start with `CI/final-cipipeline.jenkinsfile` to understand how parameters, module selection, branch selection, and build execution work.
2. Read `CI/vars/cxPipelineConfig.groovy` to learn which Jenkins global variables are required and why.
3. Read `CI/vars/buildModulesWithJDK.groovy` to understand how a module build is executed.
4. Read `CI/vars/executeChainBuild.groovy` and `CI/vars/executeParallelBuild.groovy` to understand sequential versus parallel build execution.
5. Read `CI/release-cipipeline.jenkinsfile` to learn how release tags change the CI flow.
6. Read `CI/nightly-cipipeline.jenkinsfile` to learn the scheduled nightly build flow.
7. Move to `CD/cd-pipeline.jenkinsfile` to understand the deployment stages.
8. Read `CD/resources/cd/module-registry.json` to understand deployment metadata.
9. Read `CD/vars-cd/executeDeployPipeline.groovy` to understand how deployment plans are built and executed.
10. Read `CD/vars-cd/runDeployOnAppServer.groovy` to understand remote deployment execution.

## Common Troubleshooting

If Jenkins shows only "Build Now" instead of "Build with Parameters", run the job once, wait for the parameter registration stage to complete, then refresh the job page.

If GitLab branch dropdowns are empty, check:

- `CX_GITLAB_API_URL`
- `CX_GITLAB_ORG`
- `CX_GITLAB_NAMESPACES`
- `CX_GITLAB_TOKEN_CREDENTIAL_ID`
- Jenkins Secret Text credential permissions

If CI fails during validation, check the missing variables printed by `cxPipelineConfig`.

If CD fails during validation, check the report printed by `verifyCdPipelineEnv`.

If deployment cannot find artifacts or scripts, verify Nexus URL, credentials, artifact path, and script path variables.

If shared database modules fail, deploy the primary `cc` module first or verify that the CC DB context file is readable and allowed by the configured prefix.

## Development Notes

- Keep pipeline behavior in shared-library `vars` scripts where possible.
- Keep secrets in Jenkins credentials, not in Git.
- Keep environment-specific values in Jenkins global properties or folder/job configuration.
- Update `CD/resources/cd/module-registry.json` when adding or changing deployable modules.
- Validate changes in a Jenkins test job before promoting them for production use.
