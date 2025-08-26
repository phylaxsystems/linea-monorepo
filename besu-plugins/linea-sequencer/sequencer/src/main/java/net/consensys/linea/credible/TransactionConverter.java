package net.consensys.linea.credible;

import org.hyperledger.besu.datatypes.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TransactionConverter {
    
    /**
     * Convert Besu Transaction to Reth TxEnv
     */
    public static TxEnv convertToTxEnv(Transaction transaction) {
        TxEnv.Builder builder = TxEnv.builder();
        
        // Transaction type (0=Legacy, 1=EIP-2930, 2=EIP-1559, 3=EIP-4844)
        builder.txType(transaction.getType().getEthSerializedType());
        
        // Caller (sender address)
        builder.caller(transaction.getSender().toHexString());
        
        // Gas limit
        builder.gasLimit(transaction.getGasLimit());
        
        // Gas price handling based on transaction type
        if (transaction.getType() == TransactionType.EIP1559 || 
            transaction.getType() == TransactionType.BLOB) {
            // EIP-1559: Use maxFeePerGas as gasPrice
            transaction.getMaxFeePerGas().ifPresent(maxFee -> 
                builder.gasPrice(maxFee.getAsBigInteger()));
            
            // Priority fee for EIP-1559
            transaction.getMaxPriorityFeePerGas().ifPresent(priorityFee ->
                builder.gasPriorityFee(priorityFee.getAsBigInteger()));
        } else {
            // Legacy: Use gasPrice
            transaction.getGasPrice().ifPresent(gasPrice ->
                builder.gasPrice(gasPrice.getAsBigInteger()));
        }
        
        // Transaction kind (Call vs Create)
        if (transaction.getTo().isPresent()) {
            // Contract call
            builder.kindCall(transaction.getTo().get().toHexString());
            builder.data(transaction.getData().get().toHexString());
        } else {
            // Contract creation
            builder.kindCreate();
            builder.data(transaction.getPayload().toHexString());
        }
        
        // Value
        builder.value(transaction.getValue().getAsBigInteger());
        
        // Nonce
        builder.nonce(transaction.getNonce());
        
        // Chain ID
        transaction.getChainId().ifPresent(chainId ->
            builder.chainId(chainId.longValue()));
        
        // Access List (EIP-2930 and later)
        if (transaction.getAccessList().isPresent()) {
            TxEnv.AccessList accessList = convertAccessList(transaction.getAccessList().get());
            builder.accessList(accessList);
        } else {
            builder.accessList(new TxEnv.AccessList()); // Empty access list
        }
        
        // Blob-specific fields (EIP-4844)
        if (transaction.getType() == TransactionType.BLOB) {
            // Max fee per blob gas
            transaction.getMaxFeePerBlobGas().ifPresent(maxFee ->
                builder.maxFeePerBlobGas(maxFee.getAsBigInteger()));
            
            // Blob versioned hashes
            transaction.getVersionedHashes().ifPresent(hashes -> {
                List<String> blobHashes = hashes.stream()
                    .map(versionedHash -> versionedHash.toString())
                    .collect(Collectors.toList());
                builder.blobHashes(blobHashes);
            });
        } else {
            builder.maxFeePerBlobGas(BigInteger.ZERO);
            builder.blobHashes(new ArrayList<>());
        }
        
        // Authorization list (for EIP-7702 when available)
        // Note: This might not be available in current Besu versions
        // builder.authorizationList(new ArrayList<>());
        
        return builder.build();
    }
    
    /**
     * Convert Besu AccessList to TxEnv AccessList
     */
    private static TxEnv.AccessList convertAccessList(
            List<AccessListEntry> besuAccessList) {
        
        List<TxEnv.AccessListEntry> entries = besuAccessList.stream()
            .map(TransactionConverter::convertAccessListEntry)
            .collect(Collectors.toList());
        
        return new TxEnv.AccessList(entries);
    }
    
    /**
     * Convert individual AccessListEntry
     */
    private static TxEnv.AccessListEntry convertAccessListEntry(
            AccessListEntry besuEntry) {
        
        String address = besuEntry.getAddressString();
        
        List<String> storageKeys = besuEntry.getStorageKeysString().stream()
            .collect(Collectors.toList());
        
        return new TxEnv.AccessListEntry(address, storageKeys);
    }
    
    /**
     * Helper method to get transaction type name
     */
    public static String getTransactionTypeName(Transaction transaction) {
        switch (transaction.getType()) {
            case FRONTIER: return "Legacy";
            case ACCESS_LIST: return "EIP-2930";
            case EIP1559: return "EIP-1559";
            case BLOB: return "EIP-4844";
            default: return "Unknown";
        }
    }
    
    /**
     * Comprehensive conversion with error handling
     */
    public static TxEnv safeConvertToTxEnv(Transaction transaction) {
        try {
            return convertToTxEnv(transaction);
        } catch (Exception e) {
            // Log error and return basic TxEnv
            System.err.println("Error converting transaction: " + e.getMessage());
            
            // Return minimal TxEnv with available data
            return TxEnv.builder()
                .txType(0) // Default to legacy
                .caller(transaction.getSender().toHexString())
                .gasLimit(transaction.getGasLimit())
                .gasPrice(transaction.getGasPrice()
                    .map(price -> price.getAsBigInteger())
                    .orElse(BigInteger.ZERO))
                .kindCall(transaction.getTo()
                    .map(addr -> addr.toHexString())
                    .orElse("0x"))
                .value(transaction.getValue().getAsBigInteger())
                .data(transaction.getPayload().toHexString())
                .nonce(transaction.getNonce())
                .chainId(transaction.getChainId()
                    .map(id -> id.longValue())
                    .orElse(1L))
                .accessList(new TxEnv.AccessList())
                .maxFeePerBlobGas(BigInteger.ZERO)
                .blobHashes(new ArrayList<>())
                .authorizationList(new ArrayList<>())
                .build();
        }
    }
    
    /**
     * Convert with additional metadata
     */
    public static class TxEnvWithMetadata {
        private final TxEnv txEnv;
        private final String transactionHash;
        private final String typeName;
        private final boolean isContractCreation;
        
        public TxEnvWithMetadata(TxEnv txEnv, String transactionHash, 
                               String typeName, boolean isContractCreation) {
            this.txEnv = txEnv;
            this.transactionHash = transactionHash;
            this.typeName = typeName;
            this.isContractCreation = isContractCreation;
        }
        
        // Getters
        public TxEnv getTxEnv() { return txEnv; }
        public String getTransactionHash() { return transactionHash; }
        public String getTypeName() { return typeName; }
        public boolean isContractCreation() { return isContractCreation; }
    }
    
    public static TxEnvWithMetadata convertWithMetadata(Transaction transaction) {
        TxEnv txEnv = convertToTxEnv(transaction);
        String hash = transaction.getHash().toHexString();
        String typeName = getTransactionTypeName(transaction);
        boolean isCreate = transaction.getTo().isEmpty();
        
        return new TxEnvWithMetadata(txEnv, hash, typeName, isCreate);
    }
    
}
