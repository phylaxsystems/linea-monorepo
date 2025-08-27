/*
 * Copyright Consensys Software Inc.
 *
 * This file is dual-licensed under either the MIT license or Apache License 2.0.
 * See the LICENSE-MIT and LICENSE-APACHE files in the repository root for details.
 *
 * SPDX-License-Identifier: MIT OR Apache-2.0
 */

package net.consensys.linea.sequencer.txselection.selectors;

import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;
import net.consensys.linea.credible.SidecarClient;
import net.consensys.linea.credible.TransactionConverter;
import net.consensys.linea.credible.SidecarApiModels.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.List;

public class CredibleLayerTransactionSelector implements PluginTransactionSelector {
  private static final Logger LOG = LoggerFactory.getLogger(CredibleLayerTransactionSelector.class);
  
  private final String rpcUrl;
  private SidecarClient sidecarClient;
  
  // Store pending getTransactions futures by transaction hash
  private final Map<String, CompletableFuture<GetTransactionsResponse>> pendingTxRequests = 
        new ConcurrentHashMap<>();

  public CredibleLayerTransactionSelector(final String rpcUrl) {
    this.rpcUrl = rpcUrl;
    this.sidecarClient = new SidecarClient.Builder()
        .baseUrl(rpcUrl)
        .build();
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext txContext) {

    var tx = txContext.getPendingTransaction().getTransaction();
    String txHash = tx.getHash().toHexString();

    try {
        TxEnv txEnv = TransactionConverter.convertToTxEnv(tx);
        
        // Create request with proper models
        SendTransactionsRequest sendRequest = new SendTransactionsRequest();
        sendRequest.setTransactions(List.of(new TransactionWithHash(txEnv, txHash)));
        
        // 1. Send transaction synchronously via sendTransactions
        SendTransactionsResponse sendResponse = sidecarClient.call(
          CredibleLayerMethods.SEND_TRANSACTIONS, 
          sendRequest, 
          SendTransactionsResponse.class
        );

        // Check if transaction was queued successfully
        if (sendResponse.getFailed() != null && sendResponse.getFailed().contains(txHash)) {
            LOG.warn("Transaction {} failed to queue", txHash);
        }
        
        // 2. Start long-polling getTransactions async
        GetTransactionsRequest getRequest = new GetTransactionsRequest(List.of(txHash));
        
        CompletableFuture<GetTransactionsResponse> future = sidecarClient.callAsync(
          CredibleLayerMethods.GET_TRANSACTIONS,
          getRequest,
          GetTransactionsResponse.class
        );
        
        // Store future for postprocessing
        pendingTxRequests.put(txHash, future);
        
        LOG.debug("Started async transaction processing for {}", txHash);
    } catch (Exception e) {
        LOG.error("Error in transaction preprocessing for {}: {}", txHash, e.getMessage());
    }
    
    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext txContext,
      final TransactionProcessingResult transactionProcessingResult) {

      var tx = txContext.getPendingTransaction().getTransaction();
      String txHash = tx.getHash().toHexString();
      CompletableFuture<GetTransactionsResponse> future = pendingTxRequests.remove(txHash);
      
      if (future == null) {
          LOG.warn("No pending request found for transaction {}, allowing", txHash);
          return TransactionSelectionResult.SELECTED;
      }
      
      try {
          LOG.debug("Awaiting result for {}", txHash);
          
          // Wait for long-polling response
          GetTransactionsResponse response = future.get(1, TimeUnit.SECONDS);
          
          // Process the response to determine if transaction is valid
          if (response.getResults() == null || response.getResults().isEmpty()) {
              LOG.warn("No results for transaction {} but allowing", txHash);
              return TransactionSelectionResult.SELECTED;
          }
          
          // Find the result for our specific transaction hash
          for (TransactionResult txResult : response.getResults()) {
              if (txHash.equals(txResult.getHash())) {
                  String status = txResult.getStatus();
                  
                  if (TransactionStatus.ASSERTION_FAILED.equals(status) || 
                      TransactionStatus.FAILED.equals(status)) {
                      LOG.info("Transaction {} excluded due to status: {}", txHash, status);
                      return TransactionSelectionResult.invalid("tx rejected by sidecar");
                  } else {
                      LOG.debug("Transaction {} included with status: {}", txHash, status);
                      return TransactionSelectionResult.SELECTED;
                  }
              }
          }
          
          LOG.warn("Transaction {} not found in results, but allowing", txHash);
          return TransactionSelectionResult.SELECTED;
      } catch (Exception e) {
          LOG.error("Error in transaction postprocessing for {}: {}", txHash, e.getMessage());
          return TransactionSelectionResult.SELECTED;
      }
  }
}