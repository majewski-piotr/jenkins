#!/bin/bash
# aws_connectivity_tests.sh
#
# This script performs AWS CLI connectivity tests using Terraform outputs provided in a JSON file.
# It computes dummy IP addresses based on subnet CIDRs, creates dummy ENIs in the specified subnets,
# runs network insights analyses (simulating TCP:80 connectivity tests), and then cleans up any created ENIs.
#
# Usage:
#   ./aws_connectivity_tests.sh <tf_outputs.json>
#
# The Terraform outputs JSON must contain the following keys (each with a "value" property):
#   subnet_ids_public, subnet_ids_compute, subnet_ids_edge,
#   subnet_cidrs_public, subnet_cidrs_compute, subnet_cidrs_edge,
#   vpc_cidr_private, vpc_cidr_public, vpc_id_private, vpc_id_public
#
# Note: Subnets may be in different VPCs.
#
# Requirements:
#   - AWS CLI configured with proper credentials.
#   - jq installed.
#
set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <tf_outputs.json>"
    exit 1
fi

TF_OUTPUTS_FILE="$1"
if [ ! -f "$TF_OUTPUTS_FILE" ]; then
    echo "Terraform outputs file '$TF_OUTPUTS_FILE' not found!"
    exit 1
fi

#----------------------------------------
# Helper functions for IP arithmetic
#----------------------------------------

# Convert an IP address (A.B.C.D) to an integer.
ip2int() {
    local ip=$1
    IFS=. read -r a b c d <<< "$ip"
    echo $(( (a << 24) | (b << 16) | (c << 8) | d ))
}

# Convert an integer back to an IP address.
int2ip() {
    local ipnum=$1
    echo "$(( (ipnum >> 24) & 0xFF )).$(( (ipnum >> 16) & 0xFF )).$(( (ipnum >> 8) & 0xFF )).$(( ipnum & 0xFF ))"
}

# Given a CIDR (e.g. 10.0.1.0/24), compute the network address.
get_network_address() {
    local cidr=$1
    local ip=${cidr%%/*}
    local prefix=${cidr#*/}
    local ip_int=$(ip2int "$ip")
    local mask=$(( 0xFFFFFFFF << (32 - prefix) & 0xFFFFFFFF ))
    local network_int=$(( ip_int & mask ))
    echo "$(int2ip $network_int)"
}

# Compute a dummy IP address by taking the network address from a CIDR and adding an offset.
get_dummy_ip() {
    local cidr=$1
    local offset=$2
    local network_ip
    network_ip=$(get_network_address "$cidr")
    local network_int
    network_int=$(ip2int "$network_ip")
    local dummy_int=$(( network_int + offset ))
    echo "$(int2ip $dummy_int)"
}

#----------------------------------------
# Extract Terraform outputs using jq
#----------------------------------------

# For Public subnets (used in Test 1 & Test 2)
public_subnet_id=$(jq -r '.subnet_ids_public.value[0]' "$TF_OUTPUTS_FILE")
public_subnet_cidr=$(jq -r '.subnet_cidrs_public.value[0]' "$TF_OUTPUTS_FILE")

# For Compute subnets (used in Test 1 & Test 3)
compute_subnet_id=$(jq -r '.subnet_ids_compute.value[0]' "$TF_OUTPUTS_FILE")
compute_subnet_cidr=$(jq -r '.subnet_cidrs_compute.value[0]' "$TF_OUTPUTS_FILE")

# For Edge subnets (used in Test 2)
edge_subnet_id=$(jq -r '.subnet_ids_edge.value[0]' "$TF_OUTPUTS_FILE")
edge_subnet_cidr=$(jq -r '.subnet_cidrs_edge.value[0]' "$TF_OUTPUTS_FILE")

echo "Terraform Outputs:"
echo "  Public Subnet ID: $public_subnet_id, CIDR: $public_subnet_cidr"
echo "  Compute Subnet ID: $compute_subnet_id, CIDR: $compute_subnet_cidr"
echo "  Edge Subnet ID: $edge_subnet_id, CIDR: $edge_subnet_cidr"

#----------------------------------------
# Compute dummy IP addresses (using offsets)
#----------------------------------------
# Test 1 (Public and Compute): offset 10 for each.
dummy_ip_public_test1=$(get_dummy_ip "$public_subnet_cidr" 10)
dummy_ip_compute_test1=$(get_dummy_ip "$compute_subnet_cidr" 10)

# Test 2 (Public and Edge): offset 20 for Public, 10 for Edge.
dummy_ip_public_test2=$(get_dummy_ip "$public_subnet_cidr" 20)
dummy_ip_edge_test2=$(get_dummy_ip "$edge_subnet_cidr" 10)

# Test 3 (Compute for NAT test): offset 30.
dummy_ip_compute_test3=$(get_dummy_ip "$compute_subnet_cidr" 30)

echo "Computed Dummy IPs:"
echo "  Test 1 - Public: $dummy_ip_public_test1, Compute: $dummy_ip_compute_test1"
echo "  Test 2 - Public: $dummy_ip_public_test2, Edge: $dummy_ip_edge_test2"
echo "  Test 3 - Compute for NAT: $dummy_ip_compute_test3"

#----------------------------------------
# Define filenames to store created ENI IDs (for cleanup)
#----------------------------------------
eni_file_test1_public="public_eni_id_test1.txt"
eni_file_test1_compute="compute_eni_id_test1.txt"
eni_file_test2_public="public_eni_id_test2.txt"
eni_file_test2_edge="edge_eni_id_test2.txt"
eni_file_test3_compute="compute_eni_id_test3.txt"

#----------------------------------------
# Cleanup function: delete any created ENIs
#----------------------------------------
cleanup() {
  echo "Starting cleanup..."
  for file in "$eni_file_test1_public" "$eni_file_test1_compute" "$eni_file_test2_public" "$eni_file_test2_edge" "$eni_file_test3_compute"; do
    if [ -f "$file" ]; then
      eni=$(tr -d '[:space:]' < "$file")
      if [ -n "$eni" ]; then
        echo "Attempting to delete ENI: $eni"
        if aws ec2 delete-network-interface --network-interface-id "$eni"; then
          echo "Deleted ENI: $eni"
        else
          echo "Failed to delete ENI: $eni"
        fi
      fi
    fi
  done
  echo "Cleanup completed."
}
trap cleanup EXIT

#----------------------------------------
# Test 1: Compute NOT Accessible from Public
#----------------------------------------
echo "Running Test 1: Compute NOT Accessible from Public..."

# Create dummy ENI in Public subnet (Test 1)
public_eni_test1=$(aws ec2 create-network-interface \
  --subnet-id "$public_subnet_id" \
  --private-ip-address "$dummy_ip_public_test1" \
  --query 'NetworkInterface.NetworkInterfaceId' --output text)
echo "Created Public ENI (Test 1): $public_eni_test1"
echo "$public_eni_test1" > "$eni_file_test1_public"

# Create dummy ENI in Compute subnet (Test 1)
compute_eni_test1=$(aws ec2 create-network-interface \
  --subnet-id "$compute_subnet_id" \
  --private-ip-address "$dummy_ip_compute_test1" \
  --query 'NetworkInterface.NetworkInterfaceId' --output text)
echo "Created Compute ENI (Test 1): $compute_eni_test1"
echo "$compute_eni_test1" > "$eni_file_test1_compute"

# Create a Network Insights Path from Public to Compute (simulate TCP:80)
path_id_test1=$(aws ec2 create-network-insights-path \
  --source "$public_eni_test1" \
  --destination "$compute_eni_test1" \
  --protocol tcp --destination-port 80 \
  --query 'NetworkInsightsPath.NetworkInsightsPathId' --output text)
echo "Created Network Insights Path (Public->Compute) (Test 1): $path_id_test1"

# Start the Network Insights Analysis for Test 1
analysis_id_test1=$(aws ec2 start-network-insights-analysis \
  --network-insights-path-id "$path_id_test1" \
  --query 'NetworkInsightsAnalysis.NetworkInsightsAnalysisId' --output text)
echo "Started Network Insights Analysis (Test 1): $analysis_id_test1"
sleep 10
aws ec2 describe-network-insights-analysis --network-insights-analysis-ids "$analysis_id_test1" > analysis1.json
echo "Test 1 results saved to analysis1.json"

#----------------------------------------
# Test 2: Edge NOT Accessible from Public
#----------------------------------------
echo "Running Test 2: Edge NOT Accessible from Public..."

# Create dummy ENI in Public subnet (Test 2)
public_eni_test2=$(aws ec2 create-network-interface \
  --subnet-id "$public_subnet_id" \
  --private-ip-address "$dummy_ip_public_test2" \
  --query 'NetworkInterface.NetworkInterfaceId' --output text)
echo "Created Public ENI (Test 2): $public_eni_test2"
echo "$public_eni_test2" > "$eni_file_test2_public"

# Create dummy ENI in Edge subnet (Test 2)
edge_eni_test2=$(aws ec2 create-network-interface \
  --subnet-id "$edge_subnet_id" \
  --private-ip-address "$dummy_ip_edge_test2" \
  --query 'NetworkInterface.NetworkInterfaceId' --output text)
echo "Created Edge ENI (Test 2): $edge_eni_test2"
echo "$edge_eni_test2" > "$eni_file_test2_edge"

# Create a Network Insights Path from Public to Edge (simulate TCP:80)
path_id_test2=$(aws ec2 create-network-insights-path \
  --source "$public_eni_test2" \
  --destination "$edge_eni_test2" \
  --protocol tcp --destination-port 80 \
  --query 'NetworkInsightsPath.NetworkInsightsPathId' --output text)
echo "Created Network Insights Path (Public->Edge) (Test 2): $path_id_test2"

# Start the Network Insights Analysis for Test 2
analysis_id_test2=$(aws ec2 start-network-insights-analysis \
  --network-insights-path-id "$path_id_test2" \
  --query 'NetworkInsightsAnalysis.NetworkInsightsAnalysisId' --output text)
echo "Started Network Insights Analysis (Test 2): $analysis_id_test2"
sleep 10
aws ec2 describe-network-insights-analysis --network-insights-analysis-ids "$analysis_id_test2" > analysis2.json
echo "Test 2 results saved to analysis2.json"

#----------------------------------------
# Test 3: Compute Can Access Internet via NAT
#----------------------------------------
echo "Running Test 3: Compute Can Access Internet via NAT..."

# Create dummy ENI in Compute subnet (Test 3)
compute_eni_test3=$(aws ec2 create-network-interface \
  --subnet-id "$compute_subnet_id" \
  --private-ip-address "$dummy_ip_compute_test3" \
  --query 'NetworkInterface.NetworkInterfaceId' --output text)
echo "Created Compute ENI for NAT test (Test 3): $compute_eni_test3"
echo "$compute_eni_test3" > "$eni_file_test3_compute"

# Identify the NAT Gateway's ENI (assumes a NAT Gateway already exists)
nat_eni=$(aws ec2 describe-network-interfaces \
  --filters "Name=description,Values=*NAT Gateway*" \
  --query 'NetworkInterfaces[0].NetworkInterfaceId' --output text)
echo "Identified NAT Gateway ENI: $nat_eni"

# Create a Network Insights Path from Compute to NAT Gateway (simulate TCP:80)
path_id_test3=$(aws ec2 create-network-insights-path \
  --source "$compute_eni_test3" \
  --destination "$nat_eni" \
  --protocol tcp --destination-port 80 \
  --query 'NetworkInsightsPath.NetworkInsightsPathId' --output text)
echo "Created Network Insights Path (Compute->NAT) (Test 3): $path_id_test3"

# Start the Network Insights Analysis for Test 3
analysis_id_test3=$(aws ec2 start-network-insights-analysis \
  --network-insights-path-id "$path_id_test3" \
  --query 'NetworkInsightsAnalysis.NetworkInsightsAnalysisId' --output text)
echo "Started Network Insights Analysis (Test 3): $analysis_id_test3"
sleep 10
aws ec2 describe-network-insights-analysis --network-insights-analysis-ids "$analysis_id_test3" > analysis3.json
echo "Test 3 results saved to analysis3.json"

echo "All tests completed successfully."
