package net.consensys.linea.credible;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.ArrayList;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TxBatchRequest {
    @JsonProperty("transactions")
    private List<TxWithHash> transactions;
    
    public TxBatchRequest() {
        transactions = new ArrayList<TxWithHash>();
    }
    
    public TxBatchRequest(List<TxWithHash> transactions) {
        this.transactions = transactions;
    }
    
    public List<TxWithHash> getTransactions() { return transactions; }

    public void setTransactions(List<TxWithHash> transactions) { 
        this.transactions = transactions; 
    }

    public void addTransaction(TxWithHash transaction) {
        this.transactions.add(transaction);
    }
}