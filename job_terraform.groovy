pipelineJob('apply') {
    description('Generic Terraform pipeline job with manual approval for Terraform apply or destroy')
    parameters {
        stringParam('GIT_URL', '', 'Git repository URL')
        stringParam('CREDENTIALS_ID', '', 'AWS Credentials ID to use')
        stringParam('WORKING_DIR', '', 'Directory containing Terraform code')
        booleanParam('DESTROY', false, 'Set to true to perform terraform destroy instead of apply')
    }
    definition {
        cps {
            script("""
                pipeline {
                    agent any
                    stages {
                        stage('Checkout') {
                            steps {
                                git url: params.GIT_URL, branch: 'main'
                            }
                        }
                        stage('Terraform Init') {
                            steps {
                                withAWS(credentials: params.CREDENTIALS_ID) {
                                    dir(params.WORKING_DIR) {
                                        sh 'terraform init -no-color'
                                    }
                                }
                            }
                        }
                        stage('Terraform Plan') {
                            when {
                                expression { return !params.DESTROY }
                            }
                            steps {
                                withAWS(credentials: params.CREDENTIALS_ID) {
                                    dir(params.WORKING_DIR) {
                                        sh 'terraform plan -var-file="../global.tfvars" -out=tfplan.out -no-color'
                                        archiveArtifacts artifacts: 'tfplan.out', fingerprint: true
                                    }
                                }
                            }
                        }
                        stage('Manual Approval') {
                            steps {
                                input message: 'Review the Terraform plan output. Approve to proceed?', ok: 'Proceed'
                            }
                        }
                        stage('Terraform Apply/Destroy') {
                            steps {
                                withAWS(credentials: params.CREDENTIALS_ID) {
                                    dir(params.WORKING_DIR) {
                                        script {
                                            if (params.DESTROY) {
                                                sh 'terraform destroy -auto-approve -var-file="../global.tfvars" -no-color'
                                            } else {
                                                sh 'terraform apply tfplan.out -no-color'
                                            }
                                        }
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
