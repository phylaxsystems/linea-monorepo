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

/**
 * Java equivalent of Reth BlockEnv struct
 * Complete standalone class with all dependencies included
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BlockEnv {
    
    @JsonProperty("number")
    @JsonSerialize(using = BigIntegerHexSerializer.class)
    @JsonDeserialize(using = BigIntegerHexDeserializer.class)
    private BigInteger number;
    
    @JsonProperty("beneficiary")
    private String beneficiary; // 20-byte address as hex string
    
    @JsonProperty("timestamp")
    @JsonSerialize(using = BigIntegerHexSerializer.class)
    @JsonDeserialize(using = BigIntegerHexDeserializer.class)
    private BigInteger timestamp;
    
    @JsonProperty("gasLimit")
    @JsonSerialize(using = LongHexSerializer.class)
    @JsonDeserialize(using = LongHexDeserializer.class)
    private Long gasLimit;
    
    @JsonProperty("baseFee")
    @JsonSerialize(using = LongHexSerializer.class)
    @JsonDeserialize(using = LongHexDeserializer.class)
    private Long baseFee;
    
    @JsonProperty("difficulty")
    @JsonSerialize(using = BigIntegerHexSerializer.class)
    @JsonDeserialize(using = BigIntegerHexDeserializer.class)
    private BigInteger difficulty;
    
    @JsonProperty("prevrandao")
    private String prevrandao; // Optional 32-byte hash as hex string
    
    @JsonProperty("blobExcessGasAndPrice")
    private BlobExcessGasAndPrice blobExcessGasAndPrice; // Optional nested object
    
    // Constructors
    public BlockEnv() {}
    
    public BlockEnv(BigInteger number, String beneficiary, BigInteger timestamp, 
                   Long gasLimit, Long baseFee, BigInteger difficulty,
                   String prevrandao, BlobExcessGasAndPrice blobExcessGasAndPrice) {
        this.number = number;
        this.beneficiary = beneficiary;
        this.timestamp = timestamp;
        this.gasLimit = gasLimit;
        this.baseFee = baseFee;
        this.difficulty = difficulty;
        this.prevrandao = prevrandao;
        this.blobExcessGasAndPrice = blobExcessGasAndPrice;
    }
    
    // Getters and Setters
    public BigInteger getNumber() { return number; }
    public void setNumber(BigInteger number) { this.number = number; }
    
    public String getBeneficiary() { return beneficiary; }
    public void setBeneficiary(String beneficiary) { this.beneficiary = beneficiary; }
    
    public BigInteger getTimestamp() { return timestamp; }
    public void setTimestamp(BigInteger timestamp) { this.timestamp = timestamp; }
    
    public Long getGasLimit() { return gasLimit; }
    public void setGasLimit(Long gasLimit) { this.gasLimit = gasLimit; }
    
    public Long getBaseFee() { return baseFee; }
    public void setBaseFee(Long baseFee) { this.baseFee = baseFee; }
    
    public BigInteger getDifficulty() { return difficulty; }
    public void setDifficulty(BigInteger difficulty) { this.difficulty = difficulty; }
    
    public String getPrevrandao() { return prevrandao; }
    public void setPrevrandao(String prevrandao) { this.prevrandao = prevrandao; }
    
    public BlobExcessGasAndPrice getBlobExcessGasAndPrice() { return blobExcessGasAndPrice; }
    public void setBlobExcessGasAndPrice(BlobExcessGasAndPrice blobExcessGasAndPrice) { 
        this.blobExcessGasAndPrice = blobExcessGasAndPrice; 
    }
    
    // Builder pattern for easier construction
    public static class Builder {
        private BigInteger number;
        private String beneficiary;
        private BigInteger timestamp;
        private Long gasLimit;
        private Long baseFee;
        private BigInteger difficulty;
        private String prevrandao;
        private BlobExcessGasAndPrice blobExcessGasAndPrice;
        
        public Builder number(BigInteger number) { this.number = number; return this; }
        public Builder number(long number) { this.number = BigInteger.valueOf(number); return this; }
        public Builder number(String hexNumber) { 
            this.number = new BigInteger(hexNumber.startsWith("0x") ? hexNumber.substring(2) : hexNumber, 16); 
            return this; 
        }
        
        public Builder beneficiary(String beneficiary) { 
            this.beneficiary = beneficiary != null && !beneficiary.startsWith("0x") ? "0x" + beneficiary : beneficiary;
            return this; 
        }
        
        public Builder timestamp(BigInteger timestamp) { this.timestamp = timestamp; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = BigInteger.valueOf(timestamp); return this; }
        
        public Builder gasLimit(Long gasLimit) { this.gasLimit = gasLimit; return this; }
        public Builder gasLimit(long gasLimit) { this.gasLimit = gasLimit; return this; }
        
        public Builder baseFee(Long baseFee) { this.baseFee = baseFee; return this; }
        public Builder baseFee(long baseFee) { this.baseFee = baseFee; return this; }
        
        public Builder difficulty(BigInteger difficulty) { this.difficulty = difficulty; return this; }
        public Builder difficulty(long difficulty) { this.difficulty = BigInteger.valueOf(difficulty); return this; }
        
        public Builder prevrandao(String prevrandao) { 
            this.prevrandao = prevrandao != null && !prevrandao.startsWith("0x") ? "0x" + prevrandao : prevrandao;
            return this; 
        }
        
        public Builder blobExcessGasAndPrice(BlobExcessGasAndPrice blobExcessGasAndPrice) { 
            this.blobExcessGasAndPrice = blobExcessGasAndPrice; 
            return this; 
        }
        
        public Builder blobExcessGasAndPrice(Long excessBlobGas, Long blobGasPrice) {
            this.blobExcessGasAndPrice = new BlobExcessGasAndPrice(excessBlobGas, blobGasPrice);
            return this;
        }
        
        public BlockEnv build() {
            return new BlockEnv(number, beneficiary, timestamp, gasLimit, baseFee, 
                difficulty, prevrandao, blobExcessGasAndPrice);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public String toString() {
        return String.format("BlockEnv{number=%s, beneficiary='%s', timestamp=%s, gasLimit=%d, baseFee=%d, difficulty=%s, prevrandao='%s', blobExcessGasAndPrice=%s}",
            number, beneficiary, timestamp, gasLimit, baseFee, difficulty, prevrandao, blobExcessGasAndPrice);
    }
    
    // Nested class for BlobExcessGasAndPrice
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BlobExcessGasAndPrice {
        @JsonProperty("excessBlobGas")
        @JsonSerialize(using = LongHexSerializer.class)
        @JsonDeserialize(using = LongHexDeserializer.class)
        private Long excessBlobGas;
        
        @JsonProperty("blobGasPrice")
        @JsonSerialize(using = LongHexSerializer.class)
        @JsonDeserialize(using = LongHexDeserializer.class)
        private Long blobGasPrice;
        
        public BlobExcessGasAndPrice() {}
        
        public BlobExcessGasAndPrice(Long excessBlobGas, Long blobGasPrice) {
            this.excessBlobGas = excessBlobGas;
            this.blobGasPrice = blobGasPrice;
        }
        
        public Long getExcessBlobGas() { return excessBlobGas; }
        public void setExcessBlobGas(Long excessBlobGas) { this.excessBlobGas = excessBlobGas; }
        
        public Long getBlobGasPrice() { return blobGasPrice; }
        public void setBlobGasPrice(Long blobGasPrice) { this.blobGasPrice = blobGasPrice; }
        
        @Override
        public String toString() {
            return String.format("BlobExcessGasAndPrice{excessBlobGas=%d, blobGasPrice=%d}", excessBlobGas, blobGasPrice);
        }
    }

    public static class BlockEnvResponse {
        private boolean success;
        
        public BlockEnvResponse() {}
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        @Override
        public String toString() {
            return "BlockEnvResponse{success=" + success + "}";
        }
    }
    
    // Custom serializers for hex formatting (common in Ethereum JSON-RPC)
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