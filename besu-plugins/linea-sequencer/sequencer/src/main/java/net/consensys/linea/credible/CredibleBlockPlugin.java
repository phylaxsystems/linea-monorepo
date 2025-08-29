package net.consensys.linea.credible;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.auto.service.AutoService;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import net.consensys.linea.credible.SidecarClient;
import net.consensys.linea.credible.SidecarApiModels.*;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * Plugin for sending BlockEnv to the Credible Layer sidecar
 */
@AutoService(BesuPlugin.class)
@Slf4j
public class CredibleBlockPlugin implements BesuPlugin, BesuEvents.BlockAddedListener {
  private static final String PLUGIN_NAME = "credible-sidecar";

  private ServiceManager context;
  private BesuEvents besuEvents;
  private SidecarClient sidecarClient;

  @CommandLine.Command(
    name = PLUGIN_NAME,
    description = "Configuration options for CredibleBlockPlugin",
    mixinStandardHelpOptions = false
  )
  public static class CrediblePluginConfiguration {
      @CommandLine.Option(
          names = {"--plugin-credible-sidecar-enabled"},
          description = "Enable the plugin (default: ${DEFAULT-VALUE})",
          defaultValue = "true",
          arity = "0..1"
      )
      private Boolean enabled = true;
      
      @CommandLine.Option(
          names = {"--plugin-credible-sidecar-rpc-endpoint"},
          description = "RPC endpoint URL for external calls",
          paramLabel = "<url>"
      )
      private String rpcEndpoint;

      @CommandLine.Option(
          names = {"--plugin-credible-sidecar-processing-timeout-ms"},
          description = "Timeout in ms for the Sidecar RPC when waiting for the processing of getTransactions",
          defaultValue = "50"
      )
      private int processingTimeout = 50;

      public String getRpcEndpoint() { return rpcEndpoint; }
      public int getProcessingTimeout() { return processingTimeout; }
  }

  private static CrediblePluginConfiguration config = null;

  @Override
  public void register(final ServiceManager context) {
      this.context = context;

      config = new CrediblePluginConfiguration();
        
      // Register CLI options
      Optional<PicoCLIOptions> cmdlineOptions = context.getService(PicoCLIOptions.class);
      if (cmdlineOptions.isPresent()) {
          cmdlineOptions.get().addPicoCLIOptions(PLUGIN_NAME, config);
          log.info("CLI options are available");
      } else {
          log.error("PicoCLI not available");
      } 
  }

  public static Optional<CrediblePluginConfiguration> pluginConfiguration() {
      return config == null ? Optional.empty() : Optional.of(config);
  }
    
  @Override
  public void start() {
      log.info("Starting plugin with connection to RPC {}", config.getRpcEndpoint());

      context
        .getService(BesuEvents.class)
        .ifPresentOrElse(this::startEvents, () -> log.error("BesuEvents service not available"));

      this.sidecarClient = new SidecarClient.Builder()
        .baseUrl(config.getRpcEndpoint())
        .build();
  }

  private long listenerIdentifier;

  private void startEvents(final BesuEvents events) {
    listenerIdentifier = events.addBlockAddedListener(this::onBlockAdded);
  }

  private void stopEvents(final BesuEvents events) {
    events.removeBlockAddedListener(listenerIdentifier);
  }

  @Override
    public void stop() {
      context
        .getService(BesuEvents.class)
        .ifPresentOrElse(this::stopEvents, () -> log.error("Error retrieving BesuEvents service"));
    }
    
  @Override
  public void onBlockAdded(final AddedBlockContext block) {
      var blockHeader = block.getBlockHeader();

      String blockHash = blockHeader.getBlockHash().toHexString();
      long blockNumber = blockHeader.getNumber();
      
      log.debug("Processing new block - Hash: {}, Number: {}", blockHash, blockNumber);
      
      // NOTE: maybe move to some converter
      SendBlockEnvRequest blockEnv = new SendBlockEnvRequest(
        blockHeader.getNumber(),
        blockHeader.getCoinbase().toHexString(),
        blockHeader.getTimestamp(),
        blockHeader.getGasLimit(),
        blockHeader.getBaseFee().map(quantity -> quantity.getAsBigInteger().longValue()).orElse(1L), // 1 Gwei
        blockHeader.getDifficulty().toString(),
        blockHeader.getMixHash().toHexString(),
        new BlobExcessGasAndPrice(0L, 1L)
      );

      try {
          Map<String, Object> response = this.sidecarClient.call("sendBlockEnv", blockEnv, new TypeReference<Map<String, Object>>() {});
          log.debug("Sidecar response {}", response);
        } catch (SidecarClient.JsonRpcException e) {
          log.debug("JsonRpcException for {}: {}: {}", blockHash, e.getMessage(), e.getError());
        } catch (Exception e) {
          log.error("Error handling sendBlockEnv {}", e.getMessage());
        }
  }
}
