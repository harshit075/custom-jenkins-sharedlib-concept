# Custom Jenkins Shared Library Concept

## 📖 What is a Jenkins Shared Library?

A Jenkins Shared Library is a **reusable collection of Groovy scripts** that can be used by multiple Jenkins pipelines. Instead of duplicating pipeline code across every project, you centralize common logic in a single Git repository.

**Think of it like a package/module** — just like you import `express` in Node.js or `requests` in Python, you import your shared library in Jenkins pipelines.

---

## 🤔 Why Use Shared Libraries?

| Problem | Solution with Shared Library |
|---------|------------------------------|
| Same pipeline code in 20 repos | Write once, use everywhere |
| Bug in pipeline? Fix in 20 places | Fix in one place, all pipelines updated |
| New team member needs to write pipeline | Just call a function, no need to learn pipeline syntax |
| Inconsistent build processes | Enforce standards through shared functions |
| Complex pipeline logic | Abstract away complexity into simple function calls |

---

## 📁 Project Structure

```
custom-jenkins-sharedlib-concept/
├── vars/                       # Global pipeline functions
│   ├── sayHello.groovy         # Simple greeting function (learning example)
│   ├── buildPipeline.groovy    # Full reusable pipeline template
│   └── dockerBuild.groovy      # Docker build & push utility
└── README.md                   # This file
```

### Understanding the `vars/` Directory

- Every `.groovy` file in `vars/` becomes a **globally accessible function** in Jenkins
- The **filename** becomes the **function name** (e.g., `sayHello.groovy` → `sayHello()`)
- Each file must contain a `call()` method — Jenkins invokes this automatically
- These are called **Global Variables** in Jenkins documentation

---

## 📋 Files in This Library

### 1. `vars/sayHello.groovy` — Hello World Example

**Purpose:** Simplest possible shared library function to understand the concept.

**What it teaches:**
- How `call()` method works
- Default parameters in Groovy
- String interpolation with `${variable}`

**Usage:**
```groovy
@Library('my-shared-lib') _

pipeline {
    agent any
    stages {
        stage('Greet') {
            steps {
                sayHello('Harshit')    // → Hello, Harshit! This comes from the Shared Library.
                sayHello()             // → Hello, World! This comes from the Shared Library.
            }
        }
    }
}
```

---

### 2. `vars/buildPipeline.groovy` — Full Pipeline Template

**Purpose:** A complete, reusable pipeline that any project can call with its own configuration.

**What it teaches:**
- Passing configuration as a Map (key-value pairs)
- Parameterized builds (Build with Parameters)
- Conditional stages with `when` block
- Post-build actions (success/failure notifications)
- Elvis operator (`?:`) for default values

**Usage:**
```groovy
@Library('my-shared-lib') _

// That's it! Your entire Jenkinsfile is just this:
buildPipeline(
    appName: 'my-node-app',
    repoUrl: 'https://github.com/user/app.git',
    buildCmd: 'npm install',
    testCmd: 'npm test',
    runTests: true
)
```

**Pipeline Flow:**
```
┌──────────────┐    ┌──────────┐    ┌──────────────┐    ┌──────────┐
│  Checkout    │───→│  Build   │───→│  Test        │───→│  Deploy  │
│  (git clone) │    │  (npm i) │    │  (npm test)  │    │  (target)│
└──────────────┘    └──────────┘    └──────────────┘    └──────────┘
                                     ↑ conditional
                                     (skipped if runTests=false)
```

**Parameters (config Map):**
| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `appName` | String | 'unnamed-app' | Application name for logging |
| `repoUrl` | String | — | Git repository URL |
| `buildCmd` | String | 'echo no build' | Shell command to build |
| `testCmd` | String | 'echo no tests' | Shell command for tests |
| `runTests` | Boolean | true | Whether to execute test stage |

---

### 3. `vars/dockerBuild.groovy` — Docker Utility

**Purpose:** Build, tag, and push Docker images with a single function call.

**What it teaches:**
- Method overloading (same function name, different parameters)
- Simple vs. advanced usage patterns
- Working with Docker in Jenkins pipelines
- Utility methods (cleanup, health check)

**Simple Usage:**
```groovy
steps {
    dockerBuild('my-app')              // Builds my-app:latest
    dockerBuild('my-app', 'v2.0.0')   // Builds my-app:v2.0.0
}
```

**Advanced Usage:**
```groovy
steps {
    dockerBuild(
        imageName: 'my-app',
        tag: "${env.BUILD_NUMBER}",
        dockerfile: 'Dockerfile.prod',
        context: './app',
        push: true,
        registry: 'docker.io/harshit075',
        buildArgs: [NODE_ENV: 'production', VERSION: '1.0']
    )
}
```

---

## ⚙️ How to Configure in Jenkins

### Step 1: Add as Global Pipeline Library

1. Go to **Manage Jenkins** → **System**
2. Scroll to **Global Pipeline Libraries** section
3. Click **Add**
4. Fill in:
   - **Name:** `my-shared-lib`
   - **Default version:** `main`
   - **Retrieval method:** Modern SCM
   - **Source Code Management:** Git
   - **Project Repository:** `https://github.com/harshit075/custom-jenkins-sharedlib-concept.git`
5. Click **Save**

### Step 2: Use in Any Pipeline

```groovy
// Method 1: Import with annotation (recommended)
@Library('my-shared-lib') _

// Method 2: Import specific version/branch
@Library('my-shared-lib@main') _

// Method 3: Import with specific tag
@Library('my-shared-lib@v1.0.0') _
```

> **Note:** The underscore `_` after `@Library` is required! It's a Groovy annotation quirk — it tells Groovy the annotation applies to the script, not to a class.

---

## 🧪 Build with Parameters

The `buildPipeline.groovy` includes a `parameters` block. This enables **"Build with Parameters"** in Jenkins UI.

**How it works:**
1. First build: Run "Build Now" to register the parameters
2. Subsequent builds: Click "Build with Parameters" → select options → Build

**Available parameter types:**
```groovy
parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch to build')
    choice(name: 'ENV', choices: ['dev', 'staging', 'prod'], description: 'Environment')
    booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Run tests?')
    password(name: 'API_KEY', description: 'Secret API key')
    text(name: 'NOTES', defaultValue: '', description: 'Release notes')
}
```

**Accessing parameters in pipeline:**
```groovy
echo "Branch: ${params.BRANCH}"
echo "Environment: ${params.ENV}"

if (params.RUN_TESTS) {
    sh 'npm test'
}
```

---

## 🔑 Key Concepts Explained

### The `call()` Method
```groovy
// In vars/sayHello.groovy
def call(String name) {
    echo "Hello, ${name}"
}

// In Jenkinsfile — Jenkins automatically calls call()
sayHello('World')  // ← This invokes call('World')
```

### The `@Library` Annotation
```groovy
@Library('library-name') _
// library-name = the name configured in Jenkins Global Pipeline Libraries
// _ = required Groovy syntax (underscore is the import target)
```

### Map Parameters (config)
```groovy
// Groovy Maps are like JavaScript objects or Python dicts
def config = [
    appName: 'my-app',
    buildCmd: 'npm install',
    runTests: true
]

// Access with dot notation or bracket notation
echo config.appName       // → my-app
echo config['buildCmd']   // → npm install
```

### Elvis Operator (`?:`)
```groovy
// If left side is null/empty, use right side
def name = config.appName ?: 'default-app'

// Equivalent to:
def name = config.appName != null ? config.appName : 'default-app'
```

---

## 📚 Complete Library Structure (for reference)

A full-featured shared library can also include:

```
jenkins-shared-library/
├── vars/                    # Global functions (what we built)
│   ├── sayHello.groovy
│   ├── buildPipeline.groovy
│   └── dockerBuild.groovy
├── src/                     # OOP Groovy classes (advanced)
│   └── org/
│       └── company/
│           ├── Docker.groovy
│           └── Notifications.groovy
├── resources/               # Non-code files (templates, configs)
│   ├── deploy-template.yaml
│   └── email-template.html
└── README.md
```

| Directory | Purpose | When to Use |
|-----------|---------|-------------|
| `vars/` | Simple global functions | Most use cases |
| `src/` | Complex OOP classes | When you need reusable objects with state |
| `resources/` | Static files (templates) | Config templates, email HTML, k8s manifests |

---

## 🚀 Next Steps After This Library

1. **Configure this library in your Jenkins instance**
2. **Create a test pipeline** that uses `sayHello()` and `buildPipeline()`
3. **Add more functions** like `notifySlack.groovy`, `deployToK8s.groovy`
4. **Version your library** with Git tags (v1.0, v2.0)
5. **Set up webhooks** so library changes auto-update in Jenkins

---

## 👤 Author

**Harshit Borana** — Learning Jenkins Shared Libraries hands-on

---

## 📎 References

- [Jenkins Official Docs: Shared Libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)
- [Jenkins Pipeline Syntax](https://www.jenkins.io/doc/book/pipeline/syntax/)
- [Groovy Language Documentation](https://groovy-lang.org/documentation.html)
