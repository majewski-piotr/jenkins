pipelineJob('security/network-access-analyzer-tests') {
  description('Connectivity tests using AWS CLI with Terraform outputs, preparing data and running connectivity tests script. WARNING: This job provisions infra elements using AWS CLI; if failures occur, please investigate the infrastructure state manually.')
  definition {
    cps {
      script("""
        pipeline {
          agent any

          stages {
            stage('Checkout') {
              steps {
                // Checkout the Terraform repo that includes the Terraform code and the /scripts folder
                git url: https://github.com/majewski-piotr/aws-infra.git, branch: 'main'
              }
            }
            stage('Warning') {
              steps {
                echo "WARNING: This job will use a bash script to provision infrastructure elements using AWS CLI."
                echo "In case of failure, please investigate the infrastructure state manually."
              }
            }
            stage('Terraform Init & Retrieve Outputs') {
              steps {
                withAWS(credentials: params.CREDENTIALS_ID) {
                  dir(network) {
                    sh 'terraform init -no-color'
                    sh 'terraform output -json > tf_outputs.json'
                  }
                }
                // Archive the Terraform outputs file
                archiveArtifacts artifacts: "scripts/tf_outputs.json", fingerprint: true
              }
            }
            stage('Run Connectivity Tests Script') {
              steps {
                withAWS(credentials: params.CREDENTIALS_ID) {
                  dir(scripts) {
                    sh 'chmod +x aws_connectivity_tests.sh'
                    sh 'scripts/aws_connectivity_tests.sh tf_outputs.json'
                  }
                }
              }
            }
          }

          post {
            always {
              echo "Pipeline execution completed."
            }
          }
        }
      """)
    }
  }
}
