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
                            steps {
                                withAWS(credentials: 'aws-terraform') {
                                    dir('network') {
                                        sh 'terraform init'
                                    }
                                }
                            }
                        }
                        stage('Terraform Plan') {
                            steps {
                                withAWS(credentials: 'aws-terraform') {
                                    dir('network') {
                                        sh 'terraform plan -var-file="../global.tfvars" -out=tfplan.out'
                                        archiveArtifacts artifacts: 'tfplan.out', fingerprint: true
                                    }
                                }
                            }
                        }
                        stage('Manual Approval') {
                            steps {
                                input message: 'Review the Terraform plan output. Approve to proceed with apply?', ok: 'Apply'
                            }
                        }
                        stage('Terraform Apply') {
                            steps {
                                withAWS(credentials: 'aws-terraform') {
                                    dir('network') {
                                        sh 'terraform apply -var-file="../global.tfvars" tfplan.out'
                                    }
                                }
                            }
                        }
                    }
                }
            """)
        }
    }
}
