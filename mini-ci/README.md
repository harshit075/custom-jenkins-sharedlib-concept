# Mini CI Pipeline — CustomerXP Architecture on Local Jenkins

## What is This?

A **working mini version** of the CustomerXP CI pipeline that runs on your local Docker Jenkins. It demonstrates the same architecture and patterns using public GitHub repos instead of private GitLab repos.

## Architecture Comparison

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CustomerXP (Production)                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  GitLab (private) → Active Choices → Maven → Nexus → SonarQube → Report    │
│  30+ env vars │ Multiple JDK │ Credentials │ Security tools                 │
└─────────────────────────────────────────────────────────────────────────────┘
                              ↕ Same Pattern
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Mini CI (This Pipeline)                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  GitHub (public) → Choice params → Detect & Build → Simulate → HTML Report │
│  5 env vars │ Single agent │ No credentials │ Simulated scans               │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Pipeline Flow (Same 4 Stages)

```
┌────────────────────┐     ┌───────────────────────┐     ┌─────────────────┐
│ 1. Register Params │────→│ 2. Validate Config    │────→│ 3. Clean        │
│    (Build w/ Params│     │    (Check env vars)   │     │    (Workspace)  │
└────────────────────┘     └───────────────────────┘     └────────┬────────┘
                                                                   │
                    ┌──────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│ 4. Process Selection & Execution                                         │
│                                                                          │
│  ┌─────────────────────────┐  ┌────────────────────────────────────┐   │
│  │ A) Chain Build           │  │ B) Parallel Build                   │   │
│  │ (sequential, in order)   │  │ (all at once, simultaneously)      │   │
│  │                          │  │                                     │   │
│  │  2048 ──→ markdown-here  │  │  html5-bp ──┐                      │   │
│  │  (must build first!)     │  │  you-dont-js ├──→ All at same time │   │
│  │                          │  │              ┘                      │   │
│  └──────────┬───────────────┘  └────────────────┬───────────────────┘   │
│             └───────────────┬───────────────────┘                        │
│                             ↓                                            │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ C) Publish Report (HTML with all module results)                  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

## File-to-File Mapping

| Mini CI File | CustomerXP File | Purpose |
|-------------|-----------------|---------|
| `mini-final-cipipeline.jenkinsfile` | `CI/final-cipipeline.jenkinsfile` | Main pipeline orchestrator |
| `vars/miniPipelineConfig.groovy` | `CI/vars/cxPipelineConfig.groovy` | Validate env vars, return config |
| `vars/executeChainBuild.groovy` | `CI/vars/executeChainBuild.groovy` | Sequential module builds |
| `vars/executeParallelBuild.groovy` | `CI/vars/executeParallelBuild.groovy` | Parallel module builds |
| `vars/buildModule.groovy` | `CI/vars/buildModulesWithJDK.groovy` | Actual build logic per module |
| `vars/publishReport.groovy` | `CI/vars/publishSonarReport.groovy` | Generate consolidated report |
| `vars/resolveModuleOrder.groovy` | `CI/vars/resolveChainBuildModules.groovy` | Order modules by chain |

## Module Mapping (Public Repos = CustomerXP Modules)

| Public GitHub Repo | Simulates CXP Module | Why |
|-------------------|----------------------|-----|
| `2048` | `commons` / core base | Must build first (foundation) |
| `markdown-here` | `core-lib` | Depends on commons being built |
| `html5-boilerplate` | `cmq` installer | Independent, can run in parallel |
| `you-dont-need-js` | `efmapp` installer | Independent, can run in parallel |

## Environment Variables Required

Set these in **Manage Jenkins → System → Environment variables**:

| Variable | Value | Maps to (CustomerXP) |
|----------|-------|----------------------|
| `MINI_CHAIN_ORDER` | `2048,markdown-here,html5-boilerplate,you-dont-need-js` | `CX_CHAIN_ORDER` |
| `MINI_CORE_MODULE_IDS` | `2048,markdown-here` | `CX_CORE_MODULE_IDS` |
| `MINI_INSTALLER_MODULE_IDS` | `html5-boilerplate,you-dont-need-js` | `CX_INSTALLER_MODULE_IDS` |
| `MINI_GIT_BASE_URL` | `https://github.com` | `CX_GIT_HOST` |
| `MINI_GIT_ORG` | `gabrielecirulli` | `JENKINS_GIT_ORG` / `CX_GITLAB_ORG` |

> **Note:** `MINI_GIT_ORG` is set to `gabrielecirulli` because that's who owns the 2048 repo. The pipeline handles multiple orgs via repo URL override.

## How to Set Up on Your Local Jenkins

### Step 1: Configure the Shared Library

1. **Manage Jenkins** → **System** → scroll to **Global Pipeline Libraries**
2. Click **Add**:
   - Name: `mini-ci-lib`
   - Default version: `mini-ci-pipeline`
   - Retrieval method: Modern SCM → Git
   - Project Repository: `https://github.com/harshit075/custom-jenkins-sharedlib-concept.git`
   - Library Path: `mini-ci`
3. **Save**

### Step 2: Set Environment Variables

1. **Manage Jenkins** → **System** → scroll to **Environment variables**
2. Check "Environment variables" checkbox
3. Add these key-value pairs:

```
MINI_CHAIN_ORDER = 2048,markdown-here,html5-boilerplate,you-dont-need-js
MINI_CORE_MODULE_IDS = 2048,markdown-here
MINI_INSTALLER_MODULE_IDS = html5-boilerplate,you-dont-need-js
MINI_GIT_BASE_URL = https://github.com
MINI_GIT_ORG = gabrielecirulli
```

4. **Save**

### Step 3: Create the Pipeline Job

1. **New Item** → name it `mini-ci-pipeline` → **Pipeline** → OK
2. Under **Pipeline** section:
   - Definition: **Pipeline script from SCM**
   - SCM: Git
   - Repository URL: `https://github.com/harshit075/custom-jenkins-sharedlib-concept.git`
   - Branch: `*/mini-ci-pipeline`
   - Script Path: `mini-ci/mini-final-cipipeline.jenkinsfile`
3. **Save**

### Step 4: Run It!

1. Click **Build Now** (first run registers parameters)
2. Refresh the page
3. Click **Build with Parameters**
4. Select:
   - CORE_MODULES: `all`
   - INSTALLER_MODULES: `all`
   - BUILD_SPEED: `normal`
5. Click **Build**

Watch the stage view — you'll see chain builds happen one-by-one, then parallel builds happen simultaneously!

## What You'll See in Console Output

```
═══════════════════════════════════════════════════
  Stage 1: REGISTER JOB PARAMETERS
═══════════════════════════════════════════════════
✅ Parameters registered

═══════════════════════════════════════════════════
  Stage 2: VALIDATE GLOBAL CONFIGURATION  
═══════════════════════════════════════════════════
✅ Configuration validated successfully!
   Chain Order: 2048 → markdown-here → html5-boilerplate → you-dont-need-js

═══════════════════════════════════════════════════
  Stage 3: INITIALIZE & CLEAN
═══════════════════════════════════════════════════
✅ Workspace cleaned

═══════════════════════════════════════════════════
  SEQUENTIAL CHAIN BUILD
  Order: 2048 → markdown-here
═══════════════════════════════════════════════════
🏗️ CHAIN BUILD: 2048
   📥 Cloning... ✅
   🔍 Detected: static-site
   🔨 Building... ✅  
   🔒 Security scan... ✅

🏗️ CHAIN BUILD: markdown-here
   📥 Cloning... ✅
   🔍 Detected: nodejs
   🔨 Building... ✅
   🔒 Security scan... ✅

═══════════════════════════════════════════════════
  PARALLEL BUILD (all at once!)
═══════════════════════════════════════════════════
🚀 html5-boilerplate  ──┐
🚀 you-dont-need-js   ──┤──→ Running simultaneously!
                         ┘

═══════════════════════════════════════════════════
  📊 CI REPORT
  All 4 modules passed!
═══════════════════════════════════════════════════
```

## Key Concepts Demonstrated

1. **Shared Library Pattern** — All logic in `vars/` files, Jenkinsfile just orchestrates
2. **Config Validation** — Fail fast if environment not configured correctly
3. **Chain Build** — Sequential execution respecting dependency order
4. **Parallel Build** — Simultaneous execution for independent modules
5. **Build with Parameters** — User selects what to build at runtime
6. **Report Publishing** — Consolidated results after all builds complete
7. **Module Detection** — Different build strategies per project type
8. **Security Scanning** — Per-module security analysis (simulated)

## Differences from Production CustomerXP

| Feature | CustomerXP | Mini CI | Why |
|---------|-----------|---------|-----|
| Git source | Private GitLab | Public GitHub | No credentials needed |
| Branch selection | GitLab API dropdown | Fixed to master/main | No GitLab API available |
| Build tool | Maven (mvn) | Auto-detect | Docker Jenkins has no Maven |
| Artifact store | Nexus upload | Skip | No Nexus server |
| Security | Real SonarQube scan | Simulated output | No SonarQube server |
| JDK selection | Multiple JDK tools | Single agent | Simpler setup |
| Parameters | Active Choices plugin | Standard choice | Plugin not required |
