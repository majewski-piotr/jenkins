pipelineJob('network/destroy') {
    description('Terraform pipeline job with manual approval for network infrastructure terraform destroy')
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
                                dir('network') {
                                    sh 'terraform init'
                                }
                            }
                        }
                        stage('Terraform Plan Destroy') {
                            steps withAWS(credentials: 'aws-terraform') {
                                dir('network') {
                                    sh 'terraform plan -destroy -var-file="../global.tfvars" -out=tfplan.destroy.out'
                                    archiveArtifacts artifacts: 'tfplan.destroy.out', fingerprint: true
                                }
                            }
                        }
                        stage('Manual Approval') {
                            steps {
                                input message: 'Review the Terraform destroy plan output. Approve to proceed with destroy?', ok: 'Destroy'
                            }
                        }
                        stage('Terraform Destroy') {
                            steps withAWS(credentials: 'aws-terraform') {
                                dir('network') {
                                    sh 'terraform apply tfplan.destroy.out'
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
