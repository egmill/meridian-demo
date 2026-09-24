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

        stage('transaction-service: mutation') {
            steps {
                dir('services/transaction-service') {
                    sh 'mvn -B org.pitest:pitest-maven:mutationCoverage -DmutationThreshold=70'
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'services/transaction-service/target/site/jacoco/**, services/transaction-service/target/pit-reports/**', allowEmptyArchive: true
                }
            }
        }
    }
}
