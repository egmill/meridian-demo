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

        stage('audit-log: test') {
            steps {
                dir('services/audit-log') {
                    sh '''
                        python3.11 -m venv .venv
                        .venv/bin/pip install -r requirements-dev.txt
                        .venv/bin/pytest --cov=app --junitxml=test-results.xml
                    '''
                }
            }
            post {
                always {
                    junit 'services/audit-log/test-results.xml'
                }
            }
        }
    }
}
