package net.consensys.linea.credible;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import org.hyperledger.besu.datatypes.*;
import org.hyperledger.besu.datatypes.AccessListEntry;

/**
 * Java equivalent of Rust TxEnv struct
 * Complete standalone class for transaction environment
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TxEnv {
    
    @JsonProperty("txType")
    private Integer txType; // u8 -> Integer
    
    @JsonProperty("caller")
    private String caller; // Address -> hex string
    
    @JsonProperty("gasLimit")
    @JsonSerialize(using = LongHexSerializer.class)
    @JsonDeserialize(using = LongHexDeserializer.class)
    private Long gasLimit; // u64 -> Long
    
    @JsonProperty("gasPrice")
    @JsonSerialize(using = BigIntegerHexSerializer.class)
    @JsonDeserialize(using = BigIntegerHexDeserializer.class)
    private BigInteger gasPrice; // u128 -> BigInteger
    
    @JsonProperty("kind")
    private TxKind kind; // TxKind enum
    
    @JsonProperty("value")
    @JsonSerialize(using = BigIntegerHexSerializer.class)
    @JsonDeserialize(using = BigIntegerHexDeserializer.class)
    private BigInteger value; // Uint<256, 4> -> BigInteger
    
    @JsonProperty("data")
    private String data; // Bytes -> hex string
    
    @JsonProperty("nonce")
    @JsonSerialize(using = LongHexSerializer.class)
    @JsonDeserialize(using = LongHexDeserializer.class)
    private Long nonce; // u64 -> Long
    
    @JsonProperty("chainId")
    @JsonSerialize(using = LongHexSerializer.class)
    @JsonDeserialize(using = LongHexDeserializer.class)
    private Long chainId; // Option<u64> -> Long (nullable)
    
    @JsonProperty("accessList")
    private AccessList accessList; // AccessList
    
    @JsonProperty("gasPriorityFee")
    @JsonSerialize(using = BigIntegerHexSerializer.class)
    @JsonDeserialize(using = BigIntegerHexDeserializer.class)
    private BigInteger gasPriorityFee; // Option<u128> -> BigInteger (nullable)
    
    @JsonProperty("blobHashes")
    private List<String> blobHashes; // Vec<FixedBytes<32>> -> List<String>
    
    @JsonProperty("maxFeePerBlobGas")
    @JsonSerialize(using = BigIntegerHexSerializer.class)
    @JsonDeserialize(using = BigIntegerHexDeserializer.class)
    private BigInteger maxFeePerBlobGas; // u128 -> BigInteger
    
    @JsonProperty("authorizationList")
    private List<Authorization> authorizationList; // Simplified to single Authorization type
    
    // Constructors
    public TxEnv() {}
    
    public TxEnv(Integer txType, String caller, Long gasLimit, BigInteger gasPrice,
                TxKind kind, BigInteger value, String data, Long nonce, Long chainId,
                AccessList accessList, BigInteger gasPriorityFee, List<String> blobHashes,
                BigInteger maxFeePerBlobGas, List<Authorization> authorizationList) {
        this.txType = txType;
        this.caller = caller;
        this.gasLimit = gasLimit;
        this.gasPrice = gasPrice;
        this.kind = kind;
        this.value = value;
        this.data = data;
        this.nonce = nonce;
        this.chainId = chainId;
        this.accessList = accessList;
        this.gasPriorityFee = gasPriorityFee;
        this.blobHashes = blobHashes;
        this.maxFeePerBlobGas = maxFeePerBlobGas;
        this.authorizationList = authorizationList;
    }
    
    // Getters and Setters
    public Integer getTxType() { return txType; }
    public void setTxType(Integer txType) { this.txType = txType; }
    
    public String getCaller() { return caller; }
    public void setCaller(String caller) { this.caller = caller; }
    
    public Long getGasLimit() { return gasLimit; }
    public void setGasLimit(Long gasLimit) { this.gasLimit = gasLimit; }
    
    public BigInteger getGasPrice() { return gasPrice; }
    public void setGasPrice(BigInteger gasPrice) { this.gasPrice = gasPrice; }
    
    public TxKind getKind() { return kind; }
    public void setKind(TxKind kind) { this.kind = kind; }
    
    public BigInteger getValue() { return value; }
    public void setValue(BigInteger value) { this.value = value; }
    
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    
    public Long getNonce() { return nonce; }
    public void setNonce(Long nonce) { this.nonce = nonce; }
    
    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    
    public AccessList getAccessList() { return accessList; }
    public void setAccessList(AccessList accessList) { this.accessList = accessList; }
    
    public BigInteger getGasPriorityFee() { return gasPriorityFee; }
    public void setGasPriorityFee(BigInteger gasPriorityFee) { this.gasPriorityFee = gasPriorityFee; }
    
    public List<String> getBlobHashes() { return blobHashes; }
    public void setBlobHashes(List<String> blobHashes) { this.blobHashes = blobHashes; }
    
    public BigInteger getMaxFeePerBlobGas() { return maxFeePerBlobGas; }
    public void setMaxFeePerBlobGas(BigInteger maxFeePerBlobGas) { this.maxFeePerBlobGas = maxFeePerBlobGas; }
    
    public List<Authorization> getAuthorizationList() { return authorizationList; }
    public void setAuthorizationList(List<Authorization> authorizationList) { this.authorizationList = authorizationList; }
    
    // Builder pattern
    public static class Builder {
        private Integer txType;
        private String caller;
        private Long gasLimit;
        private BigInteger gasPrice;
        private TxKind kind;
        private BigInteger value;
        private String data;
        private Long nonce;
        private Long chainId;
        private AccessList accessList;
        private BigInteger gasPriorityFee;
        private List<String> blobHashes = new ArrayList<>();
        private BigInteger maxFeePerBlobGas;
        private List<Authorization> authorizationList = new ArrayList<>();
        
        public Builder txType(Integer txType) { this.txType = txType; return this; }
        public Builder txType(int txType) { this.txType = txType; return this; }
        
        public Builder caller(String caller) { 
            this.caller = caller != null && !caller.startsWith("0x") ? "0x" + caller : caller;
            return this; 
        }
        
        public Builder gasLimit(Long gasLimit) { this.gasLimit = gasLimit; return this; }
        public Builder gasLimit(long gasLimit) { this.gasLimit = gasLimit; return this; }
        
        public Builder gasPrice(BigInteger gasPrice) { this.gasPrice = gasPrice; return this; }
        public Builder gasPrice(long gasPrice) { this.gasPrice = BigInteger.valueOf(gasPrice); return this; }
        public Builder gasPrice(String hexGasPrice) {
            this.gasPrice = new BigInteger(hexGasPrice.startsWith("0x") ? hexGasPrice.substring(2) : hexGasPrice, 16);
            return this;
        }
        
        public Builder kind(TxKind kind) { this.kind = kind; return this; }
        public Builder kindCall(String to) { this.kind = new TxKind.Call(to); return this; }
        public Builder kindCreate() { this.kind = new TxKind.Create(); return this; }
        
        public Builder value(BigInteger value) { this.value = value; return this; }
        public Builder value(long value) { this.value = BigInteger.valueOf(value); return this; }
        
        public Builder data(String data) { 
            this.data = data != null && !data.startsWith("0x") ? "0x" + data : data;
            return this; 
        }
        
        public Builder nonce(Long nonce) { this.nonce = nonce; return this; }
        public Builder nonce(long nonce) { this.nonce = nonce; return this; }
        
        public Builder chainId(Long chainId) { this.chainId = chainId; return this; }
        public Builder chainId(long chainId) { this.chainId = chainId; return this; }
        
        public Builder accessList(AccessList accessList) { this.accessList = accessList; return this; }
        
        public Builder gasPriorityFee(BigInteger gasPriorityFee) { this.gasPriorityFee = gasPriorityFee; return this; }
        public Builder gasPriorityFee(long gasPriorityFee) { this.gasPriorityFee = BigInteger.valueOf(gasPriorityFee); return this; }
        
        public Builder blobHash(String blobHash) { 
            if (this.blobHashes == null) this.blobHashes = new ArrayList<>();
            this.blobHashes.add(blobHash.startsWith("0x") ? blobHash : "0x" + blobHash);
            return this; 
        }
        
        public Builder blobHashes(List<String> blobHashes) { this.blobHashes = blobHashes; return this; }
        
        public Builder maxFeePerBlobGas(BigInteger maxFeePerBlobGas) { this.maxFeePerBlobGas = maxFeePerBlobGas; return this; }
        public Builder maxFeePerBlobGas(long maxFeePerBlobGas) { this.maxFeePerBlobGas = BigInteger.valueOf(maxFeePerBlobGas); return this; }
        
        public Builder authorization(Authorization auth) {
            if (this.authorizationList == null) this.authorizationList = new ArrayList<>();
            this.authorizationList.add(auth);
            return this;
        }
        
        public Builder authorizationList(List<Authorization> authorizationList) { 
            this.authorizationList = authorizationList; 
            return this; 
        }
        
        public TxEnv build() {
            return new TxEnv(txType, caller, gasLimit, gasPrice, kind, value, data, nonce,
                           chainId, accessList, gasPriorityFee, blobHashes, maxFeePerBlobGas, authorizationList);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public String toString() {
        return String.format("TxEnv{txType=%d, caller='%s', gasLimit=%d, gasPrice=%s, kind=%s, value=%s, data='%s', nonce=%d, chainId=%d}",
            txType, caller, gasLimit, gasPrice, kind, value, data, nonce, chainId);
    }
    
    // TxKind enum/class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static abstract class TxKind {
        @JsonProperty("type")
        private String type;
        
        protected TxKind(String type) {
            this.type = type;
        }
        
        public String getType() { return type; }
        
        // Call transaction
        public static class Call extends TxKind {
            @JsonProperty("to")
            private String to;
            
            public Call() { super("call"); }
            public Call(String to) { 
                super("call"); 
                this.to = to;
            }
            
            public String getTo() { return to; }
            public void setTo(String to) { this.to = to; }
            
            @Override
            public String toString() { return "Call{to='" + to + "'}"; }
        }
        
        // Create transaction
        public static class Create extends TxKind {
            public Create() { super("create"); }
            
            @Override
            public String toString() { return "Create{}"; }
        }
    }
    
    // AccessList class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AccessList {
        @JsonProperty("entries")
        private List<AccessListEntry> entries;
        
        public AccessList() { 
            this.entries = new ArrayList<>(); 
        }
        
        public AccessList(List<AccessListEntry> entries) {
            this.entries = entries;
        }
        
        public List<AccessListEntry> getEntries() { return entries; }
        public void setEntries(List<AccessListEntry> entries) { this.entries = entries; }
        
        public void addEntry(String address, List<String> storageKeys) {
            if (entries == null) entries = new ArrayList<>();
            entries.add(new AccessListEntry(address, storageKeys));
        }
        
        @Override
        public String toString() { return "AccessList{entries=" + entries + "}"; }
    }
    
    // AccessList Entry
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AccessListEntry {
        @JsonProperty("address")
        private String address;
        
        @JsonProperty("storageKeys")
        private List<String> storageKeys;
        
        public AccessListEntry() {}
        
        public AccessListEntry(String address, List<String> storageKeys) {
            this.address = address;
            this.storageKeys = storageKeys;
        }
        
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        
        public List<String> getStorageKeys() { return storageKeys; }
        public void setStorageKeys(List<String> storageKeys) { this.storageKeys = storageKeys; }
        
        @Override
        public String toString() { return "AccessListEntry{address='" + address + "', storageKeys=" + storageKeys + "}"; }
    }
    
    // Authorization class (simplified - represents either SignedAuthorization or RecoveredAuthorization)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Authorization {
        @JsonProperty("chainId")
        @JsonSerialize(using = LongHexSerializer.class)
        @JsonDeserialize(using = LongHexDeserializer.class)
        private Long chainId;
        
        @JsonProperty("address")
        private String address;
        
        @JsonProperty("nonce")
        @JsonSerialize(using = LongHexSerializer.class)
        @JsonDeserialize(using = LongHexDeserializer.class)
        private Long nonce;
        
        @JsonProperty("signature")
        private String signature; // Signature as hex
        
        @JsonProperty("recovered")
        private Boolean recovered; // Flag to indicate if this is recovered
        
        public Authorization() {}
        
        public Authorization(Long chainId, String address, Long nonce, String signature, Boolean recovered) {
            this.chainId = chainId;
            this.address = address;
            this.nonce = nonce;
            this.signature = signature;
            this.recovered = recovered;
        }
        
        // Getters and setters
        public Long getChainId() { return chainId; }
        public void setChainId(Long chainId) { this.chainId = chainId; }
        
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        
        public Long getNonce() { return nonce; }
        public void setNonce(Long nonce) { this.nonce = nonce; }
        
        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
        
        public Boolean getRecovered() { return recovered; }
        public void setRecovered(Boolean recovered) { this.recovered = recovered; }
        
        @Override
        public String toString() {
            return String.format("Authorization{chainId=%d, address='%s', nonce=%d, recovered=%s}", 
                chainId, address, nonce, recovered);
        }
    }
    
    // Custom serializers (same as BlockEnv)
    public static class BigIntegerHexSerializer extends JsonSerializer<BigInteger> {
        @Override
        public void serialize(BigInteger value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString("0x" + value.toString(16));
            }
        }
    }
    
    public static class BigIntegerHexDeserializer extends JsonDeserializer<BigInteger> {
        @Override
        public BigInteger deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            if (value == null || value.isEmpty()) {
                return null;
            }
            if (value.startsWith("0x")) {
                return new BigInteger(value.substring(2), 16);
            }
            return new BigInteger(value, 16);
        }
    }
    
    public static class LongHexSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString("0x" + Long.toHexString(value));
            }
        }
    }
    
    public static class LongHexDeserializer extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            if (value == null || value.isEmpty()) {
                return null;
            }
            if (value.startsWith("0x")) {
                return Long.parseUnsignedLong(value.substring(2), 16);
            }
            return Long.parseUnsignedLong(value, 16);
        }
    }
}