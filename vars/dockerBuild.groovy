/**
 * dockerBuild.groovy - Docker Build & Push Utility Function
 * 
 * PURPOSE:
 * A utility function to build Docker images and optionally push them
 * to a container registry (Docker Hub, ECR, GCR, etc.)
 * 
 * HOW IT WORKS:
 * - This is NOT a full pipeline — it's a utility step you use INSIDE a pipeline
 * - Can be called from any stage's `steps` block
 * - Supports building, tagging, and pushing Docker images
 * 
 * USAGE IN JENKINSFILE:
 * 
 *   @Library('my-shared-lib') _
 *   
 *   pipeline {
 *       agent any
 *       stages {
 *           stage('Build Docker Image') {
 *               steps {
 *                   // Basic usage - build with 'latest' tag
 *                   dockerBuild('my-app')
 *                   
 *                   // With specific tag
 *                   dockerBuild('my-app', 'v1.2.3')
 *                   
 *                   // With full registry path
 *                   dockerBuild('docker.io/harshit075/my-app', '1.0.0')
 *               }
 *           }
 *       }
 *   }
 * 
 * ADVANCED USAGE WITH MAP CONFIG:
 * 
 *   pipeline {
 *       agent any
 *       stages {
 *           stage('Docker') {
 *               steps {
 *                   // Using the advanced version with more options
 *                   dockerBuild(
 *                       imageName: 'my-app',
 *                       tag: "${env.BUILD_NUMBER}",
 *                       dockerfile: 'Dockerfile.prod',
 *                       context: './app',
 *                       push: true,
 *                       registry: 'docker.io/harshit075'
 *                   )
 *               }
 *           }
 *       }
 *   }
 * 
 * PARAMETERS:
 *   Simple version:
 *     @param imageName (String) - Name of the Docker image (e.g., 'my-app', 'registry/my-app')
 *     @param tag       (String) - Image tag (default: 'latest')
 * 
 *   Advanced version (Map):
 *     @param imageName  (String)  - Image name
 *     @param tag        (String)  - Image tag (default: 'latest')
 *     @param dockerfile (String)  - Path to Dockerfile (default: 'Dockerfile')
 *     @param context    (String)  - Docker build context path (default: '.')
 *     @param push       (Boolean) - Whether to push after building (default: false)
 *     @param registry   (String)  - Registry prefix for push (e.g., 'docker.io/username')
 *     @param buildArgs  (Map)     - Docker build arguments (--build-arg)
 * 
 * KEY CONCEPTS:
 *   - Method overloading: Two `call()` methods with different parameter types
 *   - Groovy allows multiple methods with same name but different signatures
 *   - Jenkins `sh` step executes shell commands inside the build agent
 *   - Docker commands: build, tag, push, rmi (remove image)
 * 
 * PREREQUISITES:
 *   - Docker must be installed on the Jenkins agent
 *   - For push: Docker credentials must be configured in Jenkins
 *   - Agent needs permission to run docker commands
 */

// ============================================
// SIMPLE VERSION - Quick Docker Build
// ============================================
def call(String imageName, String tag = 'latest') {
    echo "============================================"
    echo "  DOCKER BUILD"
    echo "============================================"
    echo "Image: ${imageName}:${tag}"
    echo "Dockerfile: Dockerfile (default)"
    echo "Context: . (current directory)"
    echo "============================================"
    
    // Build the Docker image
    sh "docker build -t ${imageName}:${tag} ."
    
    echo "✅ Docker image built successfully: ${imageName}:${tag}"
    
    // Return the full image name for further use
    return "${imageName}:${tag}"
}

// ============================================
// ADVANCED VERSION - Full Docker Workflow
// ============================================
def call(Map config) {
    def imageName  = config.imageName ?: error("imageName is required!")
    def tag        = config.tag ?: 'latest'
    def dockerfile = config.dockerfile ?: 'Dockerfile'
    def context    = config.context ?: '.'
    def push       = config.push ?: false
    def registry   = config.registry ?: ''
    def buildArgs  = config.buildArgs ?: [:]
    
    // Full image name with registry prefix
    def fullImageName = registry ? "${registry}/${imageName}" : imageName
    
    echo "============================================"
    echo "  DOCKER BUILD (Advanced)"
    echo "============================================"
    echo "Image: ${fullImageName}:${tag}"
    echo "Dockerfile: ${dockerfile}"
    echo "Context: ${context}"
    echo "Push to registry: ${push}"
    echo "Registry: ${registry ?: 'none (local only)'}"
    echo "============================================"
    
    // Build the --build-arg string
    def buildArgString = ''
    if (buildArgs) {
        buildArgs.each { key, value ->
            buildArgString += " --build-arg ${key}=${value}"
        }
    }
    
    // Build the Docker image
    echo "🔨 Building Docker image..."
    sh "docker build -t ${fullImageName}:${tag} -f ${dockerfile}${buildArgString} ${context}"
    
    // Also tag as 'latest' if a specific tag was provided
    if (tag != 'latest') {
        echo "🏷️  Also tagging as 'latest'..."
        sh "docker tag ${fullImageName}:${tag} ${fullImageName}:latest"
    }
    
    echo "✅ Docker image built: ${fullImageName}:${tag}"
    
    // Push to registry if requested
    if (push) {
        echo "📤 Pushing image to registry..."
        sh "docker push ${fullImageName}:${tag}"
        
        if (tag != 'latest') {
            sh "docker push ${fullImageName}:latest"
        }
        
        echo "✅ Image pushed to registry successfully!"
    }
    
    // Return full image reference
    return "${fullImageName}:${tag}"
}

// ============================================
// UTILITY: Remove local Docker image
// ============================================
def cleanup(String imageName, String tag = 'latest') {
    echo "🧹 Cleaning up local image: ${imageName}:${tag}"
    sh "docker rmi ${imageName}:${tag} || true"
    echo "✅ Cleanup complete"
}

// ============================================
// UTILITY: Check if Docker is available
// ============================================
def checkDocker() {
    echo "Checking Docker availability..."
    sh 'docker --version'
    sh 'docker info --format "Server Version: {{.ServerVersion}}"'
    echo "✅ Docker is available on this agent"
}
