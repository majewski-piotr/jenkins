pipelineJob('security/network-access-analyzer-tests') {
  description('Connectivity tests using AWS CLI with Terraform outputs, preparing data and running connectivity tests script. WARNING: This job provisions infra elements using AWS CLI; if failures occur, please investigate the infrastructure state manually.')
  definition {
    cps {
      script('''
        pipeline {
          agent any

          stages {
            stage('Warning') {
              steps {
                echo "WARNING: This job will use a bash script to provision infrastructure elements using AWS CLI."
                echo "In case of failure, please investigate the infrastructure state manually."
              }
            }
            stage('Retrieve Terraform Outputs') {
              steps {
                sh 'terraform output -json > tf_outputs.json'
                archiveArtifacts artifacts: 'tf_outputs.json', fingerprint: true
              }
            }
            stage('Run Connectivity Tests Script') {
              steps {
                withAWS(credentials: 'aws-terraform') {
                  script {
                    sh 'chmod +x ../scripts/aws_connectivity_tests.sh'
                    sh '../scripts/aws_connectivity_tests.sh tf_outputs.json'
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
      ''')
    }
  }
}
