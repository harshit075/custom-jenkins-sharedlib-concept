/**
 * buildPipeline.groovy - Reusable Full Pipeline Template
 * 
 * PURPOSE:
 * This is a complete, reusable pipeline that any project can use.
 * Instead of writing a full Jenkinsfile in every repo, you call this
 * single function with your project-specific configuration.
 * 
 * HOW IT WORKS:
 * - Accepts a Map (key-value pairs) of configuration
 * - Defines a complete pipeline with: Checkout → Build → Test → Deploy
 * - Includes parameterized builds (user can select environment from dropdown)
 * - Uses Elvis operator (?:) for default values if config is missing
 * 
 * USAGE IN JENKINSFILE:
 * 
 *   @Library('my-shared-lib') _
 *   
 *   // Example 1: Node.js Application
 *   buildPipeline(
 *       appName: 'my-node-app',
 *       repoUrl: 'https://github.com/user/node-app.git',
 *       buildCmd: 'npm install',
 *       testCmd: 'npm test',
 *       runTests: true
 *   )
 *   
 *   // Example 2: Python Application (no tests)
 *   buildPipeline(
 *       appName: 'flask-api',
 *       repoUrl: 'https://github.com/user/flask-api.git',
 *       buildCmd: 'pip install -r requirements.txt',
 *       runTests: false
 *   )
 *   
 *   // Example 3: Java/Maven Application
 *   buildPipeline(
 *       appName: 'spring-service',
 *       repoUrl: 'https://github.com/user/spring-service.git',
 *       buildCmd: 'mvn clean package -DskipTests',
 *       testCmd: 'mvn test',
 *       runTests: true
 *   )
 * 
 * PARAMETERS (config Map):
 *   @param appName   (String)  - Name of the application (used in logs)
 *   @param repoUrl   (String)  - Git repository URL to checkout
 *   @param buildCmd  (String)  - Shell command to build the project
 *   @param testCmd   (String)  - Shell command to run tests
 *   @param runTests  (Boolean) - Whether to run the Test stage (default: true)
 * 
 * JENKINS PARAMETERS (selected at build time by user):
 *   - ENV: Dropdown to choose deployment environment (dev/staging/prod)
 * 
 * KEY CONCEPTS:
 *   - Map config = [:] → Empty map as default (no config = all defaults)
 *   - config.appName ?: 'app' → If appName is null/empty, use 'app'
 *   - when { expression { } } → Conditional stage execution
 *   - post { } → Actions that run after all stages complete
 *   - params.ENV → Accesses the user-selected parameter value
 * 
 * PIPELINE FLOW:
 *   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
 *   │ Checkout │───→│  Build   │───→│   Test   │───→│  Deploy  │
 *   └──────────┘    └──────────┘    └──────────┘    └──────────┘
 *                                    (conditional)
 */

def call(Map config = [:]) {
    pipeline {
        agent any
        
        // Build Parameters - User selects these when clicking "Build with Parameters"
        parameters {
            choice(
                name: 'ENV', 
                choices: ['dev', 'staging', 'prod'], 
                description: 'Select the target deployment environment'
            )
        }
        
        // Environment variables available to all stages
        environment {
            APP_NAME = "${config.appName ?: 'unnamed-app'}"
        }
        
        stages {
            /**
             * STAGE 1: Checkout
             * Purpose: Clone the source code from Git repository
             * In a real scenario, this would use: git url: config.repoUrl, branch: 'main'
             */
            stage('Checkout') {
                steps {
                    echo "============================================"
                    echo "  STAGE: CHECKOUT"
                    echo "============================================"
                    echo "Application: ${config.appName ?: 'app'}"
                    echo "Repository: ${config.repoUrl ?: 'not specified'}"
                    echo "Checking out source code..."
                    // In real usage, uncomment the next line:
                    // git url: config.repoUrl, branch: config.branch ?: 'main'
                }
            }
            
            /**
             * STAGE 2: Build
             * Purpose: Compile/install dependencies for the application
             * Examples: npm install, mvn package, pip install, go build
             */
            stage('Build') {
                steps {
                    echo "============================================"
                    echo "  STAGE: BUILD"
                    echo "============================================"
                    echo "Running build command: ${config.buildCmd ?: 'echo no build'}"
                    sh "${config.buildCmd ?: 'echo no build command specified'}"
                    echo "Build completed successfully!"
                }
            }
            
            /**
             * STAGE 3: Test (Conditional)
             * Purpose: Run automated tests
             * This stage ONLY runs if config.runTests is not explicitly set to false
             * Using `when` block for conditional execution
             */
            stage('Test') {
                when { 
                    expression { config.runTests != false } 
                }
                steps {
                    echo "============================================"
                    echo "  STAGE: TEST"
                    echo "============================================"
                    echo "Running test suite..."
                    sh "${config.testCmd ?: 'echo no tests configured'}"
                    echo "All tests passed!"
                }
            }
            
            /**
             * STAGE 4: Deploy
             * Purpose: Deploy the application to the selected environment
             * Uses params.ENV which is selected by the user before build
             */
            stage('Deploy') {
                steps {
                    echo "============================================"
                    echo "  STAGE: DEPLOY"
                    echo "============================================"
                    echo "Deploying ${config.appName ?: 'app'} to ${params.ENV} environment"
                    echo "Deployment target: ${params.ENV}"
                    
                    // Example: Different behavior per environment
                    script {
                        switch(params.ENV) {
                            case 'dev':
                                echo "Deploying to development server..."
                                break
                            case 'staging':
                                echo "Deploying to staging server..."
                                echo "Running smoke tests after deploy..."
                                break
                            case 'prod':
                                echo "⚠️  PRODUCTION DEPLOYMENT"
                                echo "Deploying to production with blue-green strategy..."
                                break
                        }
                    }
                }
            }
        }
        
        /**
         * POST ACTIONS
         * These run after all stages complete, regardless of success/failure
         * - always: runs no matter what
         * - success: runs only if pipeline succeeded
         * - failure: runs only if pipeline failed
         * - unstable: runs if build is unstable (e.g., test failures)
         */
        post {
            always {
                echo "============================================"
                echo "  PIPELINE FINISHED"
                echo "============================================"
                echo "Application: ${config.appName ?: 'App'}"
                echo "Environment: ${params.ENV}"
            }
            success { 
                echo "✅ ${config.appName ?: 'App'} pipeline completed SUCCESSFULLY!" 
                // In real usage: send Slack notification, update status badge, etc.
            }
            failure { 
                echo "❌ ${config.appName ?: 'App'} pipeline FAILED!" 
                // In real usage: send alert, create incident ticket, etc.
            }
        }
    }
}
