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
                // Save outputs to a file for later use
                sh 'terraform output -json > tf_outputs.json'
                archiveArtifacts artifacts: 'tf_outputs.json', fingerprint: true
              }
            }
            stage('Test 1: Compute NOT Accessible from Public') {
              steps {
                withAWS(credentials: 'aws-terraform') {
                  sh '''
                    set -e

                    echo "Loading Terraform outputs..."
                    SUBNET_IDS_PUBLIC=$(terraform output -json subnet_ids_public | jq -r '.[]')
                    SUBNET_IDS_COMPUTE=$(terraform output -json subnet_ids_compute | jq -r '.[]')
                    
                    PUBLIC_SUBNET=$(echo "$SUBNET_IDS_PUBLIC" | head -n1)
                    COMPUTE_SUBNET=$(echo "$SUBNET_IDS_COMPUTE" | head -n1)
                    
                    echo "Using Public Subnet: $PUBLIC_SUBNET"
                    echo "Using Compute Subnet: $COMPUTE_SUBNET"

                    # Create dummy ENI in public subnet (simulate external source)
                    PUBLIC_IP="10.0.1.10"  # Update with an available IP from the public CIDR
                    public_eni_id=$(aws ec2 create-network-interface --subnet-id "$PUBLIC_SUBNET" --private-ip-address "$PUBLIC_IP" --query 'NetworkInterface.NetworkInterfaceId' --output text)
                    echo "Created Public ENI: $public_eni_id"

                    # Create dummy ENI in compute subnet (destination)
                    COMPUTE_IP="10.0.0.10"  # Update with an available IP from the compute CIDR
                    compute_eni_id=$(aws ec2 create-network-interface --subnet-id "$COMPUTE_SUBNET" --private-ip-address "$COMPUTE_IP" --query 'NetworkInterface.NetworkInterfaceId' --output text)
                    echo "Created Compute ENI: $compute_eni_id"

                    # Create a Network Insights Path from Public to Compute (simulate TCP:80)
                    path_id_1=$(aws ec2 create-network-insights-path --source "$public_eni_id" --destination "$compute_eni_id" --protocol tcp --destination-port 80 --query 'NetworkInsightsPath.NetworkInsightsPathId' --output text)
                    echo "Created Network Insights Path (Public->Compute): $path_id_1"

                    # Start the Network Insights Analysis
                    analysis_id_1=$(aws ec2 start-network-insights-analysis --network-insights-path-id "$path_id_1" --query 'NetworkInsightsAnalysis.NetworkInsightsAnalysisId' --output text)
                    echo "Started Analysis (Public->Compute): $analysis_id_1"
                    sleep 10
                    aws ec2 describe-network-insights-analysis --network-insights-analysis-ids "$analysis_id_1" > analysis1.json
                    echo "Test 1 results saved to analysis1.json"

                    # Save dummy ENI IDs for cleanup
                    echo "$public_eni_id" > public_eni_id.txt
                    echo "$compute_eni_id" > compute_eni_id.txt
                  '''
                }
              }
            }
            stage('Test 2: Edge NOT Accessible from Public') {
              steps {
                withAWS(credentials: 'aws-terraform') {
                  sh '''
                    set -e

                    SUBNET_IDS_PUBLIC=$(terraform output -json subnet_ids_public | jq -r '.[]')
                    SUBNET_IDS_EDGE=$(terraform output -json subnet_ids_edge | jq -r '.[]')
                    
                    PUBLIC_SUBNET=$(echo "$SUBNET_IDS_PUBLIC" | head -n1)
                    EDGE_SUBNET=$(echo "$SUBNET_IDS_EDGE" | head -n1)
                    
                    echo "Using Public Subnet: $PUBLIC_SUBNET"
                    echo "Using Edge Subnet: $EDGE_SUBNET"

                    # Create dummy ENI in public subnet (simulate external/internet source)
                    PUBLIC_IP2="10.0.1.20"  # Update as needed
                    public_eni_id2=$(aws ec2 create-network-interface --subnet-id "$PUBLIC_SUBNET" --private-ip-address "$PUBLIC_IP2" --query 'NetworkInterface.NetworkInterfaceId' --output text)
                    echo "Created second Public ENI: $public_eni_id2"

                    # Create dummy ENI in edge subnet (destination)
                    EDGE_IP="10.0.0.130"    # Update with an available IP from the edge CIDR
                    edge_eni_id=$(aws ec2 create-network-interface --subnet-id "$EDGE_SUBNET" --private-ip-address "$EDGE_IP" --query 'NetworkInterface.NetworkInterfaceId' --output text)
                    echo "Created Edge ENI: $edge_eni_id"

                    # Create a Network Insights Path from Public to Edge (simulate TCP:80)
                    path_id_2=$(aws ec2 create-network-insights-path --source "$public_eni_id2" --destination "$edge_eni_id" --protocol tcp --destination-port 80 --query 'NetworkInsightsPath.NetworkInsightsPathId' --output text)
                    echo "Created Network Insights Path (Public->Edge): $path_id_2"

                    # Start the Network Insights Analysis
                    analysis_id_2=$(aws ec2 start-network-insights-analysis --network-insights-path-id "$path_id_2" --query 'NetworkInsightsAnalysis.NetworkInsightsAnalysisId' --output text)
                    echo "Started Analysis (Public->Edge): $analysis_id_2"
                    sleep 10
                    aws ec2 describe-network-insights-analysis --network-insights-analysis-ids "$analysis_id_2" > analysis2.json
                    echo "Test 2 results saved to analysis2.json"

                    # Save dummy ENI IDs for cleanup
                    echo "$public_eni_id2" > public_eni_id2.txt
                    echo "$edge_eni_id" > edge_eni_id.txt
                  '''
                }
              }
            }
            stage('Test 3: Compute Can Access Internet via NAT') {
              steps {
                withAWS(credentials: 'aws-terraform') {
                  sh '''
                    set -e

                    SUBNET_IDS_COMPUTE=$(terraform output -json subnet_ids_compute | jq -r '.[]')
                    
                    COMPUTE_SUBNET=$(echo "$SUBNET_IDS_COMPUTE" | head -n1)
                    echo "Using Compute Subnet: $COMPUTE_SUBNET"

                    # Create dummy ENI in compute subnet (simulate source)
                    COMPUTE_IP2="10.0.0.20"  # Update with an available IP from the compute CIDR
                    compute_eni_id2=$(aws ec2 create-network-interface --subnet-id "$COMPUTE_SUBNET" --private-ip-address "$COMPUTE_IP2" --query 'NetworkInterface.NetworkInterfaceId' --output text)
                    echo "Created Compute ENI for NAT test: $compute_eni_id2"

                    # Identify the NAT Gateway's ENI (assumes NAT Gateway exists)
                    nat_eni_id=$(aws ec2 describe-network-interfaces --filters "Name=description,Values=*NAT Gateway*" --query 'NetworkInterfaces[0].NetworkInterfaceId' --output text)
                    echo "Identified NAT Gateway ENI: $nat_eni_id"

                    # Create a Network Insights Path from Compute to NAT Gateway (simulate TCP:80)
                    path_id_3=$(aws ec2 create-network-insights-path --source "$compute_eni_id2" --destination "$nat_eni_id" --protocol tcp --destination-port 80 --query 'NetworkInsightsPath.NetworkInsightsPathId' --output text)
                    echo "Created Network Insights Path (Compute->NAT): $path_id_3"

                    # Start the Network Insights Analysis
                    analysis_id_3=$(aws ec2 start-network-insights-analysis --network-insights-path-id "$path_id_3" --query 'NetworkInsightsAnalysis.NetworkInsightsAnalysisId' --output text)
                    echo "Started Analysis (Compute->NAT): $analysis_id_3"
                    sleep 10
                    aws ec2 describe-network-insights-analysis --network-insights-analysis-ids "$analysis_id_3" > analysis3.json
                    echo "Test 3 results saved to analysis3.json"

                    # Save dummy ENI ID for cleanup
                    echo "$compute_eni_id2" > compute_eni_id2.txt
                  '''
                }
              }
            }
          } // end stages

          post {
            always {
              // Ensure cleanup always runs
              withAWS(credentials: 'aws-terraform') {
                sh '''
                  set -e
                  echo "Starting cleanup of dummy ENIs..."

                  for eni in $(cat public_eni_id.txt public_eni_id2.txt compute_eni_id.txt edge_eni_id.txt compute_eni_id2.txt 2>/dev/null || true); do
                    echo "Deleting ENI: $eni"
                    aws ec2 delete-network-interface --network-interface-id "$eni" || echo "Failed to delete ENI: $eni"
                  done

                  echo "Cleanup completed."
                '''
              }
            }
          }
        }
      ''')
    }
  }
}
