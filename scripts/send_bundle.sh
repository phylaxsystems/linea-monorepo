#!/bin/bash

# Flexible bundle sender for linea_sendBundle
# Allows creating multiple bundles with configurable transactions and reverting positions

# Exit on error, but allow us to handle specific failures
set -e

# Trap errors for better debugging
trap 'echo "Error on line $LINENO. Exit code: $?"' ERR

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if cast is installed
if ! command -v cast &> /dev/null; then
    echo -e "${RED}Error: 'cast' (Foundry) is not installed.${NC}"
    echo "Install it with: curl -L https://foundry.paradigm.xyz | bash && foundryup"
    exit 1
fi

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: 'jq' is not installed.${NC}"
    echo "Install it with: sudo apt install jq (Ubuntu) or brew install jq (macOS)"
    exit 1
fi

# Configuration
if [[ -z "$1" ]]; then
    echo -e "${RED}Error: MNEMONIC is required as the first argument.${NC}"
    echo "Usage: $0 \"<MNEMONIC>\" [NUM_ACCOUNTS]"
    echo "Example: $0 \"test test test test test test test test test test test junk\" 5"
    exit 1
fi

MNEMONIC="$1"
NUM_ACCOUNTS="${2:-10}"  # Default to 10 accounts if not specified
RPC_URL="http://localhost:8545"
RECIPIENT="0x321eE2A3E574aF9a5A1C11f9Be9B1438E3177a39"
AMOUNT_ETH="0.0001"
FUNDING_AMOUNT_ETH="0.5"  # Amount to fund each derived account

# Associative arrays to store derived keys and addresses
declare -A PK
declare -A ADDR

echo -e "${YELLOW}=== Linea Flexible Bundle Sender ===${NC}"
echo ""

# Derive private keys from mnemonic
echo -e "${YELLOW}Deriving $NUM_ACCOUNTS accounts from mnemonic...${NC}"
for i in $(seq 0 $((NUM_ACCOUNTS - 1))); do
    PK[$i]=$(cast wallet derive-private-key "$MNEMONIC" $i)
    ADDR[$i]=$(cast wallet address "${PK[$i]}")
    echo -e "  Account[$i]: ${GREEN}${ADDR[$i]}${NC}"
done
echo ""

# Set default private key to first account for backward compatibility
PRIVATE_KEY="${PK[0]}"
SENDER="${ADDR[0]}"

# Check balance of Account[0] (funder account)
FUNDER_BALANCE=$(cast balance "${ADDR[0]}" --rpc-url "$RPC_URL")
FUNDER_BALANCE_ETH=$(cast from-wei "$FUNDER_BALANCE" ether)
echo -e "Funder Account[0] balance: ${GREEN}${FUNDER_BALANCE_ETH}${NC} ETH"

# Fund derived accounts from Account[0]
echo -e "${YELLOW}Funding accounts with ${FUNDING_AMOUNT_ETH} ETH each...${NC}"
FUNDING_AMOUNT_WEI=$(cast to-wei "$FUNDING_AMOUNT_ETH" ether)

# Get initial nonce for funder account and track it locally
FUNDER_NONCE=$(cast nonce "${ADDR[0]}" --rpc-url "$RPC_URL")
echo -e "${BLUE}[DEBUG]${NC} Initial funder nonce: $FUNDER_NONCE"
echo ""

for i in $(seq 1 $((NUM_ACCOUNTS - 1))); do
    echo -e "${BLUE}[DEBUG]${NC} Processing Account[$i]: ${ADDR[$i]}"

    # Check current balance
    echo -e "${BLUE}[DEBUG]${NC} Fetching balance for Account[$i]..."
    current_balance=$(cast balance "${ADDR[$i]}" --rpc-url "$RPC_URL")
    echo -e "${BLUE}[DEBUG]${NC} Balance fetched: $current_balance wei"

    current_balance_eth=$(cast from-wei "$current_balance" ether)
    echo -e "${BLUE}[DEBUG]${NC} Balance in ETH: $current_balance_eth"
    echo -e "${BLUE}[DEBUG]${NC} Funding threshold: $FUNDING_AMOUNT_WEI wei ($FUNDING_AMOUNT_ETH ETH)"

    # Only fund if balance is less than funding amount (compare as integers)
    if [[ "$current_balance" -lt "$FUNDING_AMOUNT_WEI" ]]; then
        echo -e "  Funding Account[$i] (current: ${current_balance_eth} ETH)..."
        echo -e "${BLUE}[DEBUG]${NC} Creating funding transaction with nonce: $FUNDER_NONCE"

        # Create raw transaction for funding
        set +e  # Temporarily disable exit on error
        raw_funding_tx=$(cast mktx \
            --private-key "${PK[0]}" \
            --rpc-url "$RPC_URL" \
            --nonce "$FUNDER_NONCE" \
            --gas-limit 21000 \
            --value "$FUNDING_AMOUNT_WEI" \
            "${ADDR[$i]}" 2>&1)
        funding_exit_code=$?
        set -e  # Re-enable exit on error

        echo -e "${BLUE}[DEBUG]${NC} Transaction creation exit code: $funding_exit_code"

        if [[ $funding_exit_code -eq 0 ]]; then
            echo -e "${BLUE}[DEBUG]${NC} Sending funding transaction via JSON-RPC..."

            # Send via JSON-RPC without waiting for receipt
            funding_response=$(curl -s -X POST "$RPC_URL" \
                -H "Content-Type: application/json" \
                -d "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\",\"params\":[\"$raw_funding_tx\"],\"id\":1}")

            if echo "$funding_response" | jq -e '.error' > /dev/null 2>&1; then
                error_msg=$(echo "$funding_response" | jq -r '.error.message')
                echo -e "    ${RED}Failed to send: $error_msg${NC}"
            else
                tx_hash=$(echo "$funding_response" | jq -r '.result')
                echo -e "    ${GREEN}Sent ${FUNDING_AMOUNT_ETH} ETH${NC} - TX: ${tx_hash:0:18}..."
                # Increment nonce only on success
                FUNDER_NONCE=$((FUNDER_NONCE + 1))
                echo -e "${BLUE}[DEBUG]${NC} Incremented funder nonce to: $FUNDER_NONCE"
            fi
        else
            echo -e "    ${RED}Failed to create funding transaction${NC}"
            echo -e "    ${RED}Error: $raw_funding_tx${NC}"
        fi
    else
        echo -e "  Account[$i] already funded (${current_balance_eth} ETH) - skipping"
    fi
    echo -e "${BLUE}[DEBUG]${NC} Completed processing Account[$i]"
    echo ""
done

echo -e "${BLUE}[DEBUG]${NC} Funding loop completed"
echo ""

# Get current nonce for first account
echo -e "${BLUE}[DEBUG]${NC} Fetching nonce for Account[0]..."
CURRENT_NONCE=$(cast nonce "$SENDER" --rpc-url "$RPC_URL")
echo -e "Starting nonce for Account[0]: ${GREEN}$CURRENT_NONCE${NC}"

# Get current block number
echo -e "${BLUE}[DEBUG]${NC} Fetching current block number..."
BLOCK_NUMBER=$(cast block-number --rpc-url "$RPC_URL")
TARGET_BLOCK=$((BLOCK_NUMBER + 5))
echo -e "Current block: ${GREEN}$BLOCK_NUMBER${NC}"
echo -e "Target block for bundles: ${GREEN}$TARGET_BLOCK${NC}"

# Get chain ID
echo -e "${BLUE}[DEBUG]${NC} Fetching chain ID..."
CHAIN_ID=$(cast chain-id --rpc-url "$RPC_URL")
echo -e "Chain ID: ${GREEN}$CHAIN_ID${NC}"
echo -e "${BLUE}[DEBUG]${NC} Chain info fetched successfully"

# Convert amount to wei
AMOUNT_WEI=$(cast to-wei "$AMOUNT_ETH" ether)
echo -e "Transfer amount: ${GREEN}$AMOUNT_ETH ETH${NC}"

# Get gas price and apply multiplier
BASE_GAS_PRICE=$(cast gas-price --rpc-url "$RPC_URL")
GAS_PRICE=$((BASE_GAS_PRICE * 2))
MAX_PRIORITY_FEE=$(cast to-wei 1 gwei)
MAX_FEE=$((GAS_PRICE + MAX_PRIORITY_FEE))
echo -e "Gas price (2x): ${GREEN}$GAS_PRICE${NC} wei"

# Gas limit for simple ETH transfer
GAS_LIMIT=21000

echo ""

# Global nonce counter
NONCE=$CURRENT_NONCE

# Associative array to store created transactions by name
declare -A TX_STORE

# ============================================
# Transaction Creation Functions
# ============================================

# Create a transaction using account index from mnemonic derivation
# Usage: create_tx_from_index <name> <account_index> [type]
# Example: create_tx_from_index tx1 0           - creates normal tx from PK[0]
# Example: create_tx_from_index tx2 1 revert    - creates reverting tx from PK[1]
create_tx_from_index() {
    local name=$1
    local idx=$2
    local type=${3:-normal}  # "normal" or "revert"

    if [[ -z "${PK[$idx]}" ]]; then
        echo -e "  ${RED}ERROR: Account index $idx not found!${NC}"
        return 1
    fi

    create_tx "$name" "$type" "${PK[$idx]}"
}

# Forge and send transaction immediately, then store it for bundling
# Usage: forge_and_send <name> <account_index> [type]
# Example: forge_and_send tx1 0           - forge from PK[0], send immediately, store for bundle
# Example: forge_and_send tx2 1 revert    - forge reverting tx from PK[1], send immediately
forge_and_send() {
    local name=$1
    local idx=$2
    local type=${3:-normal}  # "normal" or "revert"

    if [[ -z "${PK[$idx]}" ]]; then
        echo -e "  ${RED}ERROR: Account index $idx not found!${NC}"
        return 1
    fi

    local pk="${PK[$idx]}"
    local sender="${ADDR[$idx]}"
    local sender_nonce=$(cast nonce "$sender" --rpc-url "$RPC_URL")

    local raw_tx
    if [[ "$type" == "revert" ]]; then
        local huge_amount
        huge_amount=$(cast to-wei 1000000000 ether)
        raw_tx=$(cast mktx \
            --private-key "$pk" \
            --rpc-url "$RPC_URL" \
            --nonce "$sender_nonce" \
            --gas-limit "$GAS_LIMIT" \
            --gas-price "$MAX_FEE" \
            --priority-gas-price "$MAX_PRIORITY_FEE" \
            "$RECIPIENT" \
            --value "$huge_amount" 2>&1)
    else
        raw_tx=$(cast mktx \
            --private-key "$pk" \
            --rpc-url "$RPC_URL" \
            --nonce "$sender_nonce" \
            --gas-limit "$GAS_LIMIT" \
            --gas-price "$MAX_FEE" \
            --priority-gas-price "$MAX_PRIORITY_FEE" \
            "$RECIPIENT" \
            --value "$AMOUNT_WEI" 2>&1)
    fi

    # Store the transaction for later bundling
    TX_STORE[$name]="$raw_tx"
    TX_STORE["${name}_type"]="$type"
    TX_STORE["${name}_nonce"]="$sender_nonce"
    TX_STORE["${name}_sender"]="$sender"
    TX_STORE["${name}_pk"]="$pk"

    # Send immediately without waiting for receipt
    echo -e "  Forging and sending ${BLUE}$name${NC} from Account[$idx] (${sender:0:10}..., nonce $sender_nonce)"

    local response
    response=$(curl -s -X POST "$RPC_URL" \
        -H "Content-Type: application/json" \
        -d "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\",\"params\":[\"$raw_tx\"],\"id\":1}")

    if echo "$response" | jq -e '.error' > /dev/null 2>&1; then
        local error_msg=$(echo "$response" | jq -r '.error.message')
        echo -e "    ${RED}Send FAILED: $error_msg${NC}"
    else
        local tx_hash=$(echo "$response" | jq -r '.result')
        echo -e "    ${GREEN}Sent immediately${NC} - TX hash: ${tx_hash:0:18}... (stored as '$name' for bundling)"
    fi
}

# Create a named transaction (stores it for later use)
# Usage: create_tx <name> [type] [private_key]
# Example: create_tx tx1                     - creates normal tx with default pk
# Example: create_tx tx2 revert              - creates reverting tx with default pk
# Example: create_tx tx3 normal 0xabc123...  - creates normal tx with custom pk
# Example: create_tx tx4 revert 0xabc123...  - creates reverting tx with custom pk
create_tx() {
    local name=$1
    local type=${2:-normal}  # "normal" or "revert"
    local pk=${3:-$PRIVATE_KEY}  # use provided pk or default

    local sender=$(cast wallet address "$pk")
    local sender_nonce=$(cast nonce "$sender" --rpc-url "$RPC_URL")

    local raw_tx
    if [[ "$type" == "revert" ]]; then
        local huge_amount
        huge_amount=$(cast to-wei 1000000000 ether)
        raw_tx=$(cast mktx \
            --private-key "$pk" \
            --rpc-url "$RPC_URL" \
            --nonce "$sender_nonce" \
            --gas-limit "$GAS_LIMIT" \
            --gas-price "$MAX_FEE" \
            --priority-gas-price "$MAX_PRIORITY_FEE" \
            "$RECIPIENT" \
            --value "$huge_amount" 2>&1)
        echo -e "  Created ${YELLOW}$name${NC} (from ${sender:0:10}..., nonce $sender_nonce) - ${YELLOW}REVERTING${NC}"
    else
        raw_tx=$(cast mktx \
            --private-key "$pk" \
            --rpc-url "$RPC_URL" \
            --nonce "$sender_nonce" \
            --gas-limit "$GAS_LIMIT" \
            --gas-price "$MAX_FEE" \
            --priority-gas-price "$MAX_PRIORITY_FEE" \
            "$RECIPIENT" \
            --value "$AMOUNT_WEI" 2>&1)
        echo -e "  Created ${GREEN}$name${NC} (from ${sender:0:10}..., nonce $sender_nonce) - normal"
    fi

    TX_STORE[$name]="$raw_tx"
    TX_STORE["${name}_type"]="$type"
    TX_STORE["${name}_nonce"]="$sender_nonce"
    TX_STORE["${name}_sender"]="$sender"
    TX_STORE["${name}_pk"]="$pk"
}

# Create a named transaction with a specific nonce (for duplicating txs with same nonce)
# Usage: create_tx_with_nonce <name> <nonce> [type] [private_key]
create_tx_with_nonce() {
    local name=$1
    local specific_nonce=$2
    local type=${3:-normal}
    local pk=${4:-$PRIVATE_KEY}

    local sender=$(cast wallet address "$pk")

    local raw_tx
    if [[ "$type" == "revert" ]]; then
        local huge_amount
        huge_amount=$(cast to-wei 1000000000 ether)
        raw_tx=$(cast mktx \
            --private-key "$pk" \
            --rpc-url "$RPC_URL" \
            --nonce "$specific_nonce" \
            --gas-limit "$GAS_LIMIT" \
            --gas-price "$MAX_FEE" \
            --priority-gas-price "$MAX_PRIORITY_FEE" \
            "$RECIPIENT" \
            --value "$huge_amount" 2>&1)
        echo -e "  Created ${YELLOW}$name${NC} (from ${sender:0:10}..., nonce $specific_nonce) - ${YELLOW}REVERTING${NC}"
    else
        raw_tx=$(cast mktx \
            --private-key "$pk" \
            --rpc-url "$RPC_URL" \
            --nonce "$specific_nonce" \
            --gas-limit "$GAS_LIMIT" \
            --gas-price "$MAX_FEE" \
            --priority-gas-price "$MAX_PRIORITY_FEE" \
            "$RECIPIENT" \
            --value "$AMOUNT_WEI" 2>&1)
        echo -e "  Created ${GREEN}$name${NC} (from ${sender:0:10}..., nonce $specific_nonce) - normal"
    fi

    TX_STORE[$name]="$raw_tx"
    TX_STORE["${name}_type"]="$type"
    TX_STORE["${name}_nonce"]="$specific_nonce"
    TX_STORE["${name}_sender"]="$sender"
    TX_STORE["${name}_pk"]="$pk"
    # Note: does NOT increment global NONCE
}

# Send a bundle with named transactions (for a future block)
# Usage: send_bundle_with_txs <bundle_id> <tx_names...>
# Example: send_bundle_with_txs 1 tx1 tx2 tx3
send_bundle_with_txs() {
    local bundle_id=$1
    shift
    local tx_names=("$@")

    echo -e "${BLUE}=== Bundle $bundle_id: [${tx_names[*]}] -> block $TARGET_BLOCK ===${NC}"

    local txs_json=""
    local reverting_hashes_json=""

    for name in "${tx_names[@]}"; do
        local raw_tx="${TX_STORE[$name]}"
        local tx_type="${TX_STORE[${name}_type]}"
        local tx_nonce="${TX_STORE[${name}_nonce]}"

        if [[ -z "$raw_tx" ]]; then
            echo -e "  ${RED}ERROR: Transaction '$name' not found!${NC}"
            return 1
        fi

        # Add to txs array
        if [[ -n "$txs_json" ]]; then
            txs_json="$txs_json, \"$raw_tx\""
        else
            txs_json="\"$raw_tx\""
        fi

        # If reverting, add hash to reverting list
        if [[ "$tx_type" == "revert" ]]; then
            local tx_hash=$(cast keccak "$raw_tx")
            echo -e "  $name (nonce $tx_nonce): ${YELLOW}REVERTING${NC}"
            if [[ -n "$reverting_hashes_json" ]]; then
                reverting_hashes_json="$reverting_hashes_json, \"$tx_hash\""
            else
                reverting_hashes_json="\"$tx_hash\""
            fi
        else
            echo -e "  $name (nonce $tx_nonce): ${GREEN}normal${NC}"
        fi
    done

    # Build JSON payload
    local json_payload
    if [[ -n "$reverting_hashes_json" ]]; then
        json_payload=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "method": "linea_sendBundle",
  "params": [{
    "txs": [$txs_json],
    "blockNumber": $TARGET_BLOCK,
    "revertingTxHashes": [$reverting_hashes_json]
  }],
  "id": $bundle_id
}
EOF
)
    else
        json_payload=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "method": "linea_sendBundle",
  "params": [{
    "txs": [$txs_json],
    "blockNumber": $TARGET_BLOCK
  }],
  "id": $bundle_id
}
EOF
)
    fi

    # Send the bundle
    local response
    response=$(curl -s -X POST "$RPC_URL" \
        -H "Content-Type: application/json" \
        -d "$json_payload")

    if echo "$response" | jq -e '.error' > /dev/null 2>&1; then
        local error_msg=$(echo "$response" | jq -r '.error.message')
        echo -e "  ${RED}FAILED: $error_msg${NC}"
    else
        local bundle_hash=$(echo "$response" | jq -r '.result.bundleHash')
        echo -e "  ${GREEN}SUCCESS${NC} - Bundle hash: ${bundle_hash:0:18}..."
    fi
    echo ""
}

# Send a named transaction immediately (via eth_sendRawTransaction)
# Usage: send_tx_now <name>
# Example: send_tx_now tx1
send_tx_now() {
    local name=$1
    local raw_tx="${TX_STORE[$name]}"
    local tx_nonce="${TX_STORE[${name}_nonce]}"

    if [[ -z "$raw_tx" ]]; then
        echo -e "${RED}ERROR: Transaction '$name' not found!${NC}"
        return 1
    fi

    echo -e "${BLUE}=== Sending $name immediately (nonce $tx_nonce) ===${NC}"

    local response
    response=$(curl -s -X POST "$RPC_URL" \
        -H "Content-Type: application/json" \
        -d "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\",\"params\":[\"$raw_tx\"],\"id\":1}")

    if echo "$response" | jq -e '.error' > /dev/null 2>&1; then
        local error_msg=$(echo "$response" | jq -r '.error.message')
        echo -e "  ${RED}FAILED: $error_msg${NC}"
    else
        local tx_hash=$(echo "$response" | jq -r '.result')
        echo -e "  ${GREEN}SUCCESS${NC} - TX hash: $tx_hash"
    fi
    echo ""
}

# ============================================
# Legacy Functions (still available)
# ============================================

# Function to create a normal signed transaction
# Returns: raw signed tx on last line
create_normal_tx() {
    local nonce=$1
    local tx_label=$2

    local signed_tx
    signed_tx=$(cast mktx \
        --private-key "$PRIVATE_KEY" \
        --rpc-url "$RPC_URL" \
        --nonce "$nonce" \
        --gas-limit "$GAS_LIMIT" \
        --gas-price "$MAX_FEE" \
        --priority-gas-price "$MAX_PRIORITY_FEE" \
        "$RECIPIENT" \
        --value "$AMOUNT_WEI" 2>&1)

    echo "$signed_tx"
}

# Function to create a reverting transaction (sends way more than balance)
# Returns: raw signed tx on last line
create_reverting_tx() {
    local nonce=$1
    local tx_label=$2

    # 1 billion ETH - guaranteed to exceed balance
    local huge_amount
    huge_amount=$(cast to-wei 1000000000 ether)

    local signed_tx
    signed_tx=$(cast mktx \
        --private-key "$PRIVATE_KEY" \
        --rpc-url "$RPC_URL" \
        --nonce "$nonce" \
        --gas-limit "$GAS_LIMIT" \
        --gas-price "$MAX_FEE" \
        --priority-gas-price "$MAX_PRIORITY_FEE" \
        "$RECIPIENT" \
        --value "$huge_amount" 2>&1)

    echo "$signed_tx"
}

# Function to create and send a bundle
# Usage: send_bundle <num_txs> <reverting_positions>
# Example: send_bundle 3 "2" - creates 3 txs, 2nd one reverts
# Example: send_bundle 3 "1,3" - creates 3 txs, 1st and 3rd revert
# Example: send_bundle 2 "" - creates 2 txs, none revert
send_bundle() {
    local num_txs=$1
    local reverting_positions=$2  # comma-separated list of 1-indexed positions
    local bundle_id=$3

    echo -e "${BLUE}=== Bundle $bundle_id: $num_txs tx(s), reverting: [${reverting_positions:-none}] ===${NC}"

    local txs_json=""
    local reverting_hashes_json=""
    local tx_details=""

    for i in $(seq 1 $num_txs); do
        local is_reverting=false

        # Check if this position should revert
        if [[ -n "$reverting_positions" ]]; then
            IFS=',' read -ra positions <<< "$reverting_positions"
            for pos in "${positions[@]}"; do
                if [[ "$pos" == "$i" ]]; then
                    is_reverting=true
                    break
                fi
            done
        fi

        local raw_tx
        if $is_reverting; then
            raw_tx=$(create_reverting_tx "$NONCE" "tx$i")
            local tx_hash=$(cast keccak "$raw_tx")
            echo -e "  TX$i (nonce $NONCE): ${YELLOW}REVERTING${NC} - hash: ${tx_hash:0:18}..."

            # Add to reverting hashes
            if [[ -n "$reverting_hashes_json" ]]; then
                reverting_hashes_json="$reverting_hashes_json, \"$tx_hash\""
            else
                reverting_hashes_json="\"$tx_hash\""
            fi
        else
            raw_tx=$(create_normal_tx "$NONCE" "tx$i")
            local tx_hash=$(cast keccak "$raw_tx")
            echo -e "  TX$i (nonce $NONCE): ${GREEN}normal${NC} - hash: ${tx_hash:0:18}..."
        fi

        # Add to txs array
        if [[ -n "$txs_json" ]]; then
            txs_json="$txs_json, \"$raw_tx\""
        else
            txs_json="\"$raw_tx\""
        fi

        NONCE=$((NONCE + 1))
    done

    # Build JSON payload
    # 
    # "revertingTxHashes": [$reverting_hashes_json]
    local json_payload
    if [[ -n "$reverting_hashes_json" ]]; then
        json_payload=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "method": "linea_sendBundle",
  "params": [{
    "txs": [$txs_json],
    "blockNumber": $TARGET_BLOCK
  }],
  "id": $bundle_id
}
EOF
)
    else
        json_payload=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "method": "linea_sendBundle",
  "params": [{
    "txs": [$txs_json],
    "blockNumber": $TARGET_BLOCK
  }],
  "id": $bundle_id
}
EOF
)
    fi

    # Send the bundle
    local response
    response=$(curl -s -X POST "$RPC_URL" \
        -H "Content-Type: application/json" \
        -d "$json_payload")

    # Check response
    if echo "$response" | jq -e '.error' > /dev/null 2>&1; then
        local error_msg=$(echo "$response" | jq -r '.error.message')
        echo -e "  ${RED}FAILED: $error_msg${NC}"
    else
        local bundle_hash=$(echo "$response" | jq -r '.result.bundleHash')
        echo -e "  ${GREEN}SUCCESS${NC} - Bundle hash: ${bundle_hash:0:18}..."
    fi
    echo ""
}

# Function to send a standalone transaction (not in a bundle)
send_standalone_tx() {
    local tx_id=$1

    echo -e "${BLUE}=== Standalone TX $tx_id (nonce $NONCE) ===${NC}"

    cast send \
        --private-key "$PRIVATE_KEY" \
        --rpc-url "$RPC_URL" \
        --nonce "$NONCE" \
        --gas-limit "$GAS_LIMIT" \
        --gas-price "$MAX_FEE" \
        --priority-gas-price "$MAX_PRIORITY_FEE" \
        "$RECIPIENT" \
        --value "$AMOUNT_WEI" \
        --async \
        --json > /dev/null 2>&1

    echo -e "  ${GREEN}Sent standalone tx with nonce $NONCE${NC}"
    NONCE=$((NONCE + 1))
    echo ""
}

echo -e "${YELLOW}=== Executing Bundle Scenario ===${NC}"
echo ""

# ============================================
# SCENARIO DEFINITION - Edit this section!
# ============================================
#
# NEW API (mnemonic-based multi-sender):
#   create_tx_from_index <name> <idx> [type]  - create tx from PK[idx]
#   forge_and_send <name> <idx> [type]        - send immediately AND store for bundling
#   send_bundle_with_txs <id> <tx1> <tx2> ... - bundle txs for future block
#   send_tx_now <name>                        - send stored tx immediately
#
# Original API (custom private keys):
#   create_tx <name> [type] [pk]              - create and store a tx
#   create_tx_with_nonce <name> <nonce> [type] [pk] - create with specific nonce
#
# LEGACY API (anonymous transactions):
#   send_bundle <num_txs> <reverting_positions> <bundle_id>
#   send_standalone_tx <tx_id>
#
# Available accounts: PK[0] to PK[NUM_ACCOUNTS-1]
# ============================================

echo -e "${YELLOW}--- Example: Multi-sender bundle with forge-and-send ---${NC}"
echo ""

# Example 1: Create transactions from different accounts and bundle them
create_tx_from_index tx1 0        # Normal tx from Account[0]
create_tx_from_index tx2 1        # Normal tx from Account[1]
create_tx_from_index tx3 2 revert       # Reverting tx from Account[2]
create_tx_from_index tx4 3        # Normal tx from Account[3]

# Example 2: Forge and send immediately, then include in bundle
forge_and_send tx5 4              # Send from Account[4] immediately, store for bundle
forge_and_send tx6 5              # Send from Account[5] immediately, store for bundle

forge_and_send tx5 1              # Send from Account[6] immediately, store for bundle
send_bundle_with_txs 1 tx1 tx2 tx3 tx4 tx5 # Bundle with 4 txs

# Example 3: Send bundles with mixed transactions
# send_bundle_with_txs 1 tx1 tx2 tx3      # Bundle with reverting tx
# send_bundle_with_txs 2 tx4 tx5 tx6      # Bundle including already-sent txs

echo ""
echo -e "${YELLOW}--- Randomized multi-sender example ---${NC}"
echo ""

# Example 4: Create multiple transactions with random account indices
# Uncomment and customize as needed:
# for i in {0..9}; do
#     random_idx=$((RANDOM % NUM_ACCOUNTS))
#     create_tx_from_index "rand_tx_$i" $random_idx
# done
# send_bundle_with_txs 3 rand_tx_0 rand_tx_1 rand_tx_2 rand_tx_3 rand_tx_4

# ============================================
# More examples:
# ============================================
#
# # Forge-and-send pattern: send tx immediately, then include in bundle
# forge_and_send front_tx 0           # Send from Account[0], don't wait
# create_tx_from_index mid_tx 1       # Create from Account[1] (not sent yet)
# create_tx_from_index back_tx 2      # Create from Account[2] (not sent yet)
# send_bundle_with_txs 1 front_tx mid_tx back_tx  # Bundle all three
#
# # Race condition test: send same tx both ways
# create_tx_from_index race_tx 3
# send_bundle_with_txs 2 race_tx      # Include in bundle
# send_tx_now race_tx                 # Also send immediately
#
# # Multi-sender bundle with specific accounts
# create_tx_from_index alice_tx 0
# create_tx_from_index bob_tx 1
# create_tx_from_index charlie_tx 2 revert  # Charlie's tx will revert
# send_bundle_with_txs 3 alice_tx bob_tx charlie_tx
#
# ============================================

echo ""
echo -e "${YELLOW}=== Summary ===${NC}"
echo -e "Number of accounts derived: $NUM_ACCOUNTS"
echo -e "Target block for bundles: $TARGET_BLOCK"
echo ""