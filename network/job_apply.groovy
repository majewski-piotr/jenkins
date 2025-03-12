pipelineJob('network/apply') {
    description('Terraform pipeline job with manual approval for network infrastructure terraform apply')
    definition {
        cps {
            script("""
                pipeline {
                    agent any
                    stages {
                        stage('Checkout') {
                            steps {
                                git url: 'https://github.com/majewski-piotr/aws-infra.git', branch: 'main'
                            }
                        }
                        stage('Terraform Init') {
                            steps withCredentials([[\$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-terraform']]) {
                                // Change into the network directory and initialize Terraform
                                dir('network') {
                                    sh 'terraform init'
                                }
                            }
                        }
                        stage('Terraform Plan') withCredentials([[\$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-terraform']]) {
                            steps {
                                dir('network') {
                                    sh 'terraform plan -var-file="../global.tfvars" -out=tfplan.out'
                                    archiveArtifacts artifacts: 'tfplan.out', fingerprint: true
                                }
                            }
                        }
                        stage('Manual Approval') {
                            steps {
                                // Wait for manual approval before applying the plan
                                input message: 'Review the Terraform plan output. Approve to proceed with apply?', ok: 'Apply'
                            }
                        }
                        stage('Terraform Apply') withCredentials([[\$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-terraform']]) {
                            steps {
                                dir('network') {
                                    // Apply the Terraform plan with the global variable file
                                    sh 'terraform apply -var-file="../global.tfvars" tfplan.out'
                                }
                            }
                        }
                    }
                }
            """)
            sandbox()
        }
    }
}
