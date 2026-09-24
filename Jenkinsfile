// Mirrors .github/workflows/ci.yml for teams building on Jenkins.
pipeline {
    agent any

    tools {
        jdk 'jdk17'
        maven 'maven3'
    }

    stages {
        stage('transaction-service: test') {
            steps {
                dir('services/transaction-service') {
                    sh 'mvn -B test'
                }
            }
            post {
                always {
                    junit 'services/transaction-service/target/surefire-reports/*.xml'
                }
            }
        }
    }
}
