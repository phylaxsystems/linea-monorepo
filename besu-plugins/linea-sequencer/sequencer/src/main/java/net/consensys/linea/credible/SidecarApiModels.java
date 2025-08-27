package net.consensys.linea.credible;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;
import java.math.BigInteger;

public class SidecarApiModels {
    /**
     * Java equivalent of Rust TxEnv struct
     * Updated to match API specification field names
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TxEnv {
        
        @JsonProperty("caller")
        private String caller; // Address as hex string
        
        @JsonProperty("gas_limit")
        private Long gasLimit; // u64 -> Long
        
        @JsonProperty("gas_price")
        private String gasPrice; // Hex string (e.g., "0x5f5e100")
        
        @JsonProperty("transact_to")
        private String transactTo; // Address as hex string (null for contract creation)
        
        @JsonProperty("value")
        private String value; // Hex string (e.g., "0x0", "0x1bc16d674ec80000")
        
        @JsonProperty("data")
        private String data; // Bytes as hex string
        
        @JsonProperty("nonce")
        private Long nonce; // u64 -> Long
        
        @JsonProperty("chain_id")
        private Long chainId; // u64 -> Long
        
        @JsonProperty("access_list")
        private List<AccessListEntry> accessList; // AccessList
        
        // Constructors
        public TxEnv() {
            this.accessList = new ArrayList<>();
        }
        
        public TxEnv(String caller, Long gasLimit, String gasPrice, String transactTo,
                    String value, String data, Long nonce, Long chainId, List<AccessListEntry> accessList) {
            this.caller = caller;
            this.gasLimit = gasLimit;
            this.gasPrice = gasPrice;
            this.transactTo = transactTo;
            this.value = value;
            this.data = data;
            this.nonce = nonce;
            this.chainId = chainId;
            this.accessList = accessList != null ? accessList : new ArrayList<>();
        }
        
        // Getters and Setters
        public String getCaller() { return caller; }
        public void setCaller(String caller) { this.caller = caller; }
        
        public Long getGasLimit() { return gasLimit; }
        public void setGasLimit(Long gasLimit) { this.gasLimit = gasLimit; }
        
        public String getGasPrice() { return gasPrice; }
        public void setGasPrice(String gasPrice) { this.gasPrice = gasPrice; }
        
        public String getTransactTo() { return transactTo; }
        public void setTransactTo(String transactTo) { this.transactTo = transactTo; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
        
        public Long getNonce() { return nonce; }
        public void setNonce(Long nonce) { this.nonce = nonce; }
        
        public Long getChainId() { return chainId; }
        public void setChainId(Long chainId) { this.chainId = chainId; }
        
        public List<AccessListEntry> getAccessList() { return accessList; }
        public void setAccessList(List<AccessListEntry> accessList) { 
            this.accessList = accessList != null ? accessList : new ArrayList<>(); 
        }
        
        @Override
        public String toString() {
            return String.format("TxEnv{caller='%s', gasLimit=%d, gasPrice='%s', transactTo='%s', value='%s', data='%s', nonce=%d, chainId=%d}",
                    caller, gasLimit, gasPrice, transactTo, value, data, nonce, chainId);
        }
    }
        
    // AccessList Entry
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AccessListEntry {
        @JsonProperty("address")
        private String address;
        
        @JsonProperty("storage_keys")
        private List<String> storageKeys;
        
        public AccessListEntry() {}
        
        public AccessListEntry(String address, List<String> storageKeys) {
            this.address = address;
            this.storageKeys = storageKeys != null ? storageKeys : new ArrayList<>();
        }
        
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        
        public List<String> getStorageKeys() { return storageKeys; }
        public void setStorageKeys(List<String> storageKeys) { 
            this.storageKeys = storageKeys != null ? storageKeys : new ArrayList<>(); 
        }
        
        @Override
        public String toString() { 
            return "AccessListEntry{address='" + address + "', storageKeys=" + storageKeys + "}"; 
        }
    }

    // ==================== REQUEST MODELS ====================

    /**
     * Request model for sendTransactions endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SendTransactionsRequest {
        @JsonProperty("transactions")
        private List<TransactionWithHash> transactions;
        
        public SendTransactionsRequest() {}
        
        public SendTransactionsRequest(List<TransactionWithHash> transactions) {
            this.transactions = transactions;
        }
        
        public List<TransactionWithHash> getTransactions() { return transactions; }
        public void setTransactions(List<TransactionWithHash> transactions) { this.transactions = transactions; }
    }

    /**
     * Request model for getTransactions endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GetTransactionsRequest {
        @JsonProperty("hashes")
        private List<String> hashes;
        
        public GetTransactionsRequest() {}
        
        public GetTransactionsRequest(List<String> hashes) {
            this.hashes = hashes;
        }
        
        public List<String> getHashes() { return hashes; }
        public void setHashes(List<String> hashes) { this.hashes = hashes; }
    }

    /**
     * Request model for sendBlockEnv endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SendBlockEnvRequest {
        @JsonProperty("number")
        private Long number;
        
        @JsonProperty("coinbase")
        private String coinbase;
        
        @JsonProperty("timestamp")
        private Long timestamp;
        
        @JsonProperty("gas_limit")
        private Long gasLimit;
        
        @JsonProperty("base_fee")
        private String baseFee;
        
        @JsonProperty("difficulty")
        private String difficulty;
        
        @JsonProperty("prevrandao")
        private String prevrandao;
        
        public SendBlockEnvRequest() {}

        public SendBlockEnvRequest(Long number, String coinbase, Long timestamp, Long gasLimit, String baseFee, String difficulty, String prevrandao) {
            this.number = number;
            this.coinbase = coinbase;
            this.timestamp = timestamp;
            this.gasLimit = gasLimit;
            this.baseFee = baseFee;
            this.difficulty = difficulty;
            this.prevrandao = prevrandao;
        }
        
        // Getters and setters
        public Long getNumber() { return number; }
        public void setNumber(Long number) { this.number = number; }
        
        public String getCoinbase() { return coinbase; }
        public void setCoinbase(String coinbase) { this.coinbase = coinbase; }
        
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        
        public Long getGasLimit() { return gasLimit; }
        public void setGasLimit(Long gasLimit) { this.gasLimit = gasLimit; }
        
        public String getBaseFee() { return baseFee; }
        public void setBaseFee(String baseFee) { this.baseFee = baseFee; }
        
        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
        
        public String getPrevrandao() { return prevrandao; }
        public void setPrevrandao(String prevrandao) { this.prevrandao = prevrandao; }
    }

    // ==================== RESPONSE MODELS ====================

    /**
     * Response model for sendTransactions endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SendTransactionsResponse {
        @JsonProperty("queued")
        private List<String> queued;
        
        @JsonProperty("failed")
        private List<String> failed;
        
        public SendTransactionsResponse() {}
        
        public SendTransactionsResponse(List<String> queued, List<String> failed) {
            this.queued = queued;
            this.failed = failed;
        }
        
        public List<String> getQueued() { return queued; }
        public void setQueued(List<String> queued) { this.queued = queued; }
        
        public List<String> getFailed() { return failed; }
        public void setFailed(List<String> failed) { this.failed = failed; }
    }

    /**
     * Response model for getTransactions endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GetTransactionsResponse {
        @JsonProperty("results")
        private List<TransactionResult> results;
        
        @JsonProperty("not_found")
        private List<String> notFound;
        
        public GetTransactionsResponse() {}
        
        public GetTransactionsResponse(List<TransactionResult> results, List<String> notFound) {
            this.results = results;
            this.notFound = notFound;
        }
        
        public List<TransactionResult> getResults() { return results; }
        public void setResults(List<TransactionResult> results) { this.results = results; }
        
        public List<String> getNotFound() { return notFound; }
        public void setNotFound(List<String> notFound) { this.notFound = notFound; }
    }

    /**
     * Response model for sendBlockEnv endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SendBlockEnvResponse {
        @JsonProperty("success")
        private Boolean success;
        
        @JsonProperty("error")
        private String error;
        
        public SendBlockEnvResponse() {}
        
        public SendBlockEnvResponse(Boolean success, String error) {
            this.success = success;
            this.error = error;
        }
        
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    // ==================== NESTED MODELS ====================

    /**
     * Individual transaction result in getTransactions response
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionResult {
        @JsonProperty("hash")
        private String hash;
        
        @JsonProperty("status")
        private String status; // "success", "assertion_failed", "failed"
        
        @JsonProperty("gas_used")
        private Long gasUsed;
        
        @JsonProperty("error")
        private String error;
        
        public TransactionResult() {}
        
        public TransactionResult(String hash, String status, Long gasUsed, String error) {
            this.hash = hash;
            this.status = status;
            this.gasUsed = gasUsed;
            this.error = error;
        }
        
        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Long getGasUsed() { return gasUsed; }
        public void setGasUsed(Long gasUsed) { this.gasUsed = gasUsed; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    /**
     * Transaction with hash wrapper for sendTransactions
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionWithHash {
        @JsonProperty("txEnv")
        private TxEnv txEnv;
        
        @JsonProperty("hash")
        private String hash;
        
        public TransactionWithHash() {}
        
        public TransactionWithHash(TxEnv txEnv, String hash) {
            this.txEnv = txEnv;
            this.hash = hash;
        }
        
        public TxEnv getTxEnv() { return txEnv; }
        public void setTxEnv(TxEnv txEnv) { this.txEnv = txEnv; }
        
        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
    }

    // ==================== ENUMS & CONSTANTS ====================

    /**
     * Transaction status constants
     */
    public static class TransactionStatus {
        public static final String SUCCESS = "success";
        public static final String ASSERTION_FAILED = "assertion_failed";
        public static final String FAILED = "failed";
        
        private TransactionStatus() {} // Utility class
    }

    /**
     * JSON-RPC method names
     */
    public static class CredibleLayerMethods {
        public static final String SEND_TRANSACTIONS = "sendTransactions";
        public static final String GET_TRANSACTIONS = "getTransactions";
        public static final String SEND_BLOCK_ENV = "sendBlockEnv";
        
        private CredibleLayerMethods() {} // Utility class
    }
}