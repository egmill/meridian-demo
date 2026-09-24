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
        stage('pii-vault: test') {
            steps {
                dir('services/pii-vault') {
                    sh 'python3.11 -m venv .venv'
                    sh '.venv/bin/pip install -r requirements.txt -r requirements-dev.txt'
                    sh '.venv/bin/pytest --cov=app --junitxml=test-results.xml'
                }
            }
            post {
                always {
                    junit 'services/pii-vault/test-results.xml'
                }
            }
        }
    }
}
