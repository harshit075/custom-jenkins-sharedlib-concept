/**
 * sayHello.groovy - Simplest Shared Library Function
 * 
 * PURPOSE:
 * This is the most basic example of a shared library function.
 * It demonstrates how the `call()` method works in Jenkins Shared Libraries.
 * 
 * HOW IT WORKS:
 * - Any .groovy file in the `vars/` folder becomes a "global variable"
 * - The `call()` method is what gets executed when you invoke the function
 * - Jenkins automatically calls `call()` when you use: sayHello('name')
 * 
 * USAGE IN JENKINSFILE:
 * 
 *   @Library('my-shared-lib') _
 *   
 *   pipeline {
 *       agent any
 *       stages {
 *           stage('Greet') {
 *               steps {
 *                   sayHello('Harshit')          // Output: Hello, Harshit! This comes from the Shared Library.
 *                   sayHello()                    // Output: Hello, World! This comes from the Shared Library.
 *                   sayHello('Jenkins Team')      // Output: Hello, Jenkins Team! This comes from the Shared Library.
 *               }
 *           }
 *       }
 *   }
 * 
 * PARAMETERS:
 *   @param name (String) - The name to greet. Defaults to 'World' if not provided.
 * 
 * KEY CONCEPTS:
 *   - Default parameters: `String name = 'World'` means the param is optional
 *   - String interpolation: `${name}` inserts the variable value into the string
 *   - The `echo` step prints to Jenkins console output
 */

def call(String name = 'World') {
    // Print greeting to Jenkins console output
    echo "Hello, ${name}! This comes from the Shared Library."
    
    // Return the greeting string (useful if caller wants to capture it)
    return "Hello, ${name}!"
}
