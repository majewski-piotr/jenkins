pipelineJob('network/test-connectivity') {
  description('Connectivity tests using AWS CLI with Terraform outputs, split into stages with mandatory cleanup')
  definition {
    cps {
      script('''
        pipeline {
          agent any

          stages {
            stage('Retrieve Terraform Outputs') {
              steps {
                sh 'terraform output -json > tf_outputs.json'
                archiveArtifacts artifacts: 'tf_outputs.json', fingerprint: true
              }
            }
            stage('Test 1: Compute NOT Accessible from Public') {
              steps {
                withAWS(credentials: 'aws-terraform') {
                  script {
                    // Retrieve subnet IDs for public and compute
                    def subnetIdsPublic = sh(script: "terraform output -json subnet_ids_public | jq -r '.[]'", returnStdout: true).trim()
                    def subnetIdsCompute = sh(script: "terraform output -json subnet_ids_compute | jq -r '.[]'", returnStdout: true).trim()
                    def publicSubnet = sh(script: "echo '${subnetIdsPublic}' | head -n1", returnStdout: true).trim()
                    def computeSubnet = sh(script: "echo '${subnetIdsCompute}' | head -n1", returnStdout: true).trim()
                    echo "Using Public Subnet: ${publicSubnet}"
                    echo "Using Compute Subnet: ${computeSubnet}"
                    
                    // Create dummy ENI in the public subnet
                    def publicIp = "10.0.1.10"  // Update with an available IP from the public CIDR
                    def publicEniId = sh(script: "aws ec2 create-network-interface --subnet-id ${publicSubnet} --private-ip-address ${publicIp} --query 'NetworkInterface.NetworkInterfaceId' --output text", returnStdout: true).trim()
                    echo "Created Public ENI: ${publicEniId}"
                    
                    // Create dummy ENI in the compute subnet
                    def computeIp = "10.0.0.10"  // Update with an available IP from the compute CIDR
                    def computeEniId = sh(script: "aws ec2 create-network-interface --subnet-id ${computeSubnet} --private-ip-address ${computeIp} --query 'NetworkInterface.NetworkInterfaceId' --output text", returnStdout: true).trim()
                    echo "Created Compute ENI: ${computeEniId}"
                    
                    // Create a Network Insights Path from Public to Compute (simulate TCP:80)
                    def pathId1 = sh(script: "aws ec2 create-network-insights-path --source ${publicEniId} --destination ${computeEniId} --protocol tcp --destination-port 80 --query 'NetworkInsightsPath.NetworkInsightsPathId' --output text", returnStdout: true).trim()
                    echo "Created Network Insights Path (Public->Compute): ${pathId1}"
                    
                    // Start the Network Insights Analysis
                    def analysisId1 = sh(script: "aws ec2 start-network-insights-analysis --network-insights-path-id ${pathId1} --query 'NetworkInsightsAnalysis.NetworkInsightsAnalysisId' --output text", returnStdout: true).trim()
                    echo "Started Analysis (Public->Compute): ${analysisId1}"
                    sh "sleep 10"
                    sh "aws ec2 describe-network-insights-analysis --network-insights-analysis-ids ${analysisId1} > analysis1.json"
                    echo "Test 1 results saved to analysis1.json"
                    
                    // Save dummy ENI IDs for cleanup
                    writeFile file: 'public_eni_id.txt', text: publicEniId
                    writeFile file: 'compute_eni_id.txt', text: computeEniId
                  }
                }
              }
            }
          } // end stages

          post {
            always {
              withAWS(credentials: 'aws-terraform') {
                script {
                  // List of files that store ENI IDs
                  def eniFiles = ["public_eni_id.txt", "public_eni_id2.txt", "compute_eni_id.txt", "edge_eni_id.txt", "compute_eni_id2.txt"]
                  for (fileName in eniFiles) {
                    if (fileExists(fileName)) {
                      def eni = readFile(fileName).trim()
                      echo "Attempting to delete ENI: ${eni}"
                      try {
                        sh "aws ec2 delete-network-interface --network-interface-id ${eni}"
                      } catch (err) {
                        echo "Failed to delete ENI: ${eni}"
                      }
                    } else {
                      echo "File ${fileName} not found, skipping..."
                    }
                  }
                  echo "Cleanup completed."
                }
              }
            }
          }
        }
      ''')
    }
  }
}
