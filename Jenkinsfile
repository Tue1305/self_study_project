pipeline {
    agent any // Tells Jenkins to run this pipeline on any available executor

    stages {
        stage('Build') {
            steps {
                echo 'Building application...'
                // Run Linux shell commands using 'sh'
                sh 'docker build -t app-demo:latest .'
            }
        }

        stage('Test') {
            steps {
                echo 'Running tests...'
                sh 'chmod +x ./mvnw && ./mvnw test'
            }
        }

        stage('Deploy') {
            steps {
                echo 'Deploying application...'
                sh 'docker-compose up -d --build'
            }
        }
    }

    post {
        success {
            echo 'Pipeline executed successfully!'
        }
        failure {
            echo 'Pipeline failed.'
        }
    }
}
