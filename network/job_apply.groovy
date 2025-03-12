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
                            steps withAWS(credentials: 'aws-terraform') {
                                // Change into the network directory and initialize Terraform
                                dir('network') {
                                    sh 'terraform init'
                                }
                            }
                        }
                        stage('Terraform Plan') {
                            steps withAWS(credentials: 'aws-terraform') {
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
                        stage('Terraform Apply') {
                            steps withAWS(credentials: 'aws-terraform') {
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
