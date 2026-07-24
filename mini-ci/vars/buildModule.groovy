/**
 * buildModule.groovy
 * 
 * MAPS TO: CI/vars/buildModulesWithJDK.groovy in CustomerXP
 * 
 * PURPOSE:
 * The actual build logic for a single module.
 * This is where the real work happens — clone, build, scan, report.
 *
 * CUSTOMERXP BEHAVIOR:
 * - Clones from GitLab using credentials
 * - Sets up JDK (JAVA_HOME)
 * - Generates run_build.sh (Maven build script)
 * - Runs Maven clean install
 * - Uploads artifacts to Nexus (if installer)
 * - Runs security tools: SonarQube, SBOM, Gitleaks, JaCoCo, DefectDojo
 * - Cleans workspace
 *
 * OUR MINI VERSION:
 * - Clones from public GitHub (no credentials needed)
 * - Detects project type (Node.js, Python, Java, Static)
 * - Runs appropriate build/validate commands
 * - Simulates security scan output
 * - Reports results
 */
def call(Map args) {
    def moduleName = args.moduleName
    def branch = args.branch ?: 'main'
    def repoUrl = args.repoUrl
    def config = args.config ?: [:]
    def buildType = args.buildType ?: 'snapshot'
    def isChainBuild = args.isChainBuild ?: false

    def buildLabel = isChainBuild ? "CHAIN" : "PARALLEL"
    def startTime = System.currentTimeMillis()

    echo "⚙️ [${buildLabel}] Building module: ${moduleName} (${branch})"

    // --- Step 1: Clone the repository ---
    echo "📥 Step 1: Cloning repository..."
    dir("module-${moduleName}") {
        // Clean directory first (like CustomerXP's deleteDir())
        deleteDir()

        // Clone the repo — CustomerXP uses withCredentials + .netrc for private GitLab repos
        // We use public repos so no credentials needed
        git branch: branch, url: repoUrl, changelog: false, poll: false
        
        echo "✅ Repository cloned successfully"

        // --- Step 2: Detect project type and build ---
        echo "🔍 Step 2: Detecting project type..."
        def projectType = detectProjectType()
        echo "   Detected: ${projectType}"

        // --- Step 3: Execute build ---
        echo "🔨 Step 3: Executing build..."
        executeBuild(projectType, moduleName)

        // --- Step 4: Run security scan (simplified) ---
        echo "🔒 Step 4: Security scan..."
        runSecurityScan(moduleName, projectType)

        // --- Step 5: Generate artifacts info ---
        echo "📦 Step 5: Artifact info..."
        def duration = (System.currentTimeMillis() - startTime) / 1000
        echo "   Module: ${moduleName}"
        echo "   Branch: ${branch}"
        echo "   Build Type: ${buildType}"
        echo "   Duration: ${duration}s"
        echo "   Artifact: ${moduleName}-${buildType}-SNAPSHOT.jar (simulated)"

        // Return build result (like CustomerXP tracks per-module results)
        return [
            module: moduleName,
            branch: branch,
            status: 'SUCCESS',
            duration: duration,
            projectType: projectType
        ]
    }
}

/**
 * Detect the project type by looking at files in the repo.
 * CustomerXP knows all modules are Java/Maven, but this handles multiple types.
 */
def detectProjectType() {
    if (fileExists('pom.xml')) {
        return 'java-maven'
    } else if (fileExists('build.gradle')) {
        return 'java-gradle'
    } else if (fileExists('package.json')) {
        return 'nodejs'
    } else if (fileExists('requirements.txt') || fileExists('setup.py')) {
        return 'python'
    } else if (fileExists('Gemfile')) {
        return 'ruby'
    } else if (fileExists('index.html')) {
        return 'static-site'
    } else {
        return 'unknown'
    }
}

/**
 * Execute the appropriate build command based on project type.
 * CustomerXP generates a run_build.sh with Maven commands + Nexus upload.
 * We run simpler commands appropriate for each project type.
 */
def executeBuild(String projectType, String moduleName) {
    switch (projectType) {
        case 'java-maven':
            echo "   Running: mvn clean compile -q (quiet mode)"
            sh '''
                if command -v mvn &> /dev/null; then
                    mvn clean compile -q -DskipTests 2>/dev/null || echo "Maven build completed (or mvn not installed — simulating)"
                else
                    echo "Maven not installed — simulating Java build"
                    echo "  [INFO] Scanning for projects..."
                    echo "  [INFO] Building ${moduleName} 1.0-SNAPSHOT"
                    echo "  [INFO] --- maven-compiler-plugin:3.8.1:compile ---"
                    echo "  [INFO] BUILD SUCCESS"
                fi
            '''
            break
        case 'nodejs':
            echo "   Running: npm install (or simulation)"
            sh '''
                if command -v node &> /dev/null; then
                    echo "Node.js version: $(node --version 2>/dev/null || echo 'not found')"
                    # Don't actually install — just validate package.json
                    cat package.json | head -5
                    echo "  ✅ package.json validated"
                else
                    echo "Node.js not installed — simulating npm install"
                    echo "  added 342 packages in 4.2s"
                fi
            '''
            break
        case 'python':
            echo "   Running: python validation"
            sh '''
                echo "Python version: $(python3 --version 2>/dev/null || python --version 2>/dev/null || echo 'not found')"
                if [ -f requirements.txt ]; then
                    echo "  Dependencies found: $(wc -l < requirements.txt) packages"
                fi
                echo "  ✅ Python project validated"
            '''
            break
        case 'static-site':
            echo "   Validating static site..."
            sh '''
                echo "  Files found:"
                find . -maxdepth 1 -name "*.html" -o -name "*.css" -o -name "*.js" | head -10
                echo "  ✅ Static site validated"
            '''
            break
        default:
            echo "   Running: generic validation"
            sh '''
                echo "  Listing project files:"
                ls -la | head -15
                echo "  ✅ Generic validation complete"
            '''
    }
    echo "   ✅ Build step complete for ${moduleName} (${projectType})"
}

/**
 * Simplified security scan simulation.
 * CustomerXP runs: SonarQube, CycloneDX SBOM, Gitleaks, JaCoCo, Dependency-Track, DefectDojo.
 * We simulate the output to show what each tool does.
 */
def runSecurityScan(String moduleName, String projectType) {
    echo "   ┌─ Security Scan Results for ${moduleName} ─────────"

    // 1. Gitleaks — scan for secrets
    echo "   │ 🔑 Gitleaks: Scanning for secrets..."
    sh """
        echo '   │    Scanning ${moduleName} for hardcoded secrets...'
        echo '   │    Rules checked: AWS keys, API tokens, passwords, private keys'
        echo '   │    Files scanned: \$(find . -type f | wc -l)'
        echo '   │    ✅ No secrets found'
    """

    // 2. SBOM — Software Bill of Materials
    echo "   │ 📋 SBOM: Generating Software Bill of Materials..."
    sh """
        echo '   │    Format: CycloneDX JSON'
        if [ -f package.json ]; then
            echo "   │    Dependencies: \$(cat package.json | grep -c '\"' 2>/dev/null || echo '~20') entries"
        elif [ -f pom.xml ]; then
            echo "   │    Dependencies: \$(grep -c '<dependency>' pom.xml 2>/dev/null || echo '~15') entries"
        else
            echo '   │    Dependencies: analyzed'
        fi
        echo '   │    ✅ SBOM generated: ${moduleName}-sbom.json'
    """

    // 3. SonarQube — code quality
    echo "   │ 📊 SonarQube: Code quality analysis..."
    sh """
        echo '   │    Project Key: mini-ci_${moduleName}'
        echo '   │    Lines of Code: \$(find . -name "*.java" -o -name "*.js" -o -name "*.py" | xargs wc -l 2>/dev/null | tail -1 | awk "{print \\\$1}" || echo "~500")'
        echo '   │    Bugs: 0 | Vulnerabilities: 0 | Code Smells: 3'
        echo '   │    Coverage: 72.4% | Duplications: 2.1%'
        echo '   │    Quality Gate: ✅ PASSED'
    """

    echo "   └────────────────────────────────────────────────"
}
