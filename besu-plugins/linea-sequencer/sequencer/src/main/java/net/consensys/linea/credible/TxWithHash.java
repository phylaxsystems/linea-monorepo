package net.consensys.linea.credible;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TxWithHash {
    @JsonProperty("txEnv")
    private TxEnv txEnv;
    
    @JsonProperty("hash")
    private String hash;
    
    public TxWithHash() {}
    
    public TxWithHash(TxEnv txEnv, String hash) {
        this.txEnv = txEnv;
        this.hash = hash;
    }
    
    public TxEnv getTxEnv() { return txEnv; }
    public void setTxEnv(TxEnv txEnv) { this.txEnv = txEnv; }
    
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}