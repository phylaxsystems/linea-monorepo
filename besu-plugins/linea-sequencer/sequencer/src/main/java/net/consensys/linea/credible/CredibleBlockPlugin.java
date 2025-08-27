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
import net.consensys.linea.credible.BlockEnv;
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

      public String getRpcEndpoint() { return rpcEndpoint; }
  }

  private CrediblePluginConfiguration config;

  @Override
  public void register(final ServiceManager context) {
      this.context = context;

      config = new CrediblePluginConfiguration();
        
      // Register CLI options
      Optional<PicoCLIOptions> cmdlineOptions = context.getService(PicoCLIOptions.class);
      if (cmdlineOptions.isPresent()) {
          cmdlineOptions.get().addPicoCLIOptions(PLUGIN_NAME, config);
          log.atInfo().setMessage("CLI options are available").log();
      } else {
          log.atError().setMessage("PicoCLI not available").log();
      } 
  }
    
  @Override
  public void start() {
      log.atInfo().setMessage("Starting connection to RPC: " + config.getRpcEndpoint()).log();

      context
        .getService(BesuEvents.class)
        .ifPresentOrElse(this::startEvents, () -> log.atError().setMessage("BesuEvents service not available").log());

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
        .ifPresentOrElse(this::stopEvents, () -> log.atError().setMessage("Error retrieving BesuEvents service").log());
    }
    
  @Override
  public void onBlockAdded(final AddedBlockContext block) {
      String blockHash = block.getBlockHeader().getBlockHash().toHexString();
      long blockNumber = block.getBlockHeader().getNumber();
      
      log.atDebug().setMessage("Processing new block - Hash: " + blockHash + ", Number: " + blockNumber).log();
      var blockHeader = block.getBlockHeader();
      var blockBody = block.getBlockBody();

      BlockEnv blockEnv = BlockEnv.builder()
        .number(blockHeader.getNumber())
        .beneficiary(blockHeader.getCoinbase().toHexString())
        .timestamp(blockHeader.getTimestamp())
        .gasLimit(blockHeader.getGasLimit())
        .baseFee(blockHeader.getBaseFee().map(quantity -> quantity.getAsBigInteger().longValue()).orElse(1L)) // 1 Gwei
        .difficulty(blockHeader.getDifficulty().getAsBigInteger())
        .prevrandao(blockHeader.getMixHash().toHexString())
        .blobExcessGasAndPrice(new BlockEnv.BlobExcessGasAndPrice(100000L, 1000000L))
        .build();

      try {
          Map<String, Object> response = this.sidecarClient.call("sendBlockEnv", blockEnv, new TypeReference<Map<String, Object>>() {});
          log.atInfo().setMessage("Sidecar response: " + response).log(); 
        } catch (SidecarClient.JsonRpcException e) {
          log.atError().setMessage("JsonRpcException: " + e.getMessage()).log();
        } catch (Exception e) {
          log.atError().setMessage("Exception: " + e.getMessage()).log();
        }
  }
}
