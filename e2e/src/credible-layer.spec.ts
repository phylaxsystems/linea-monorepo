import { beforeAll, describe, expect, it } from "@jest/globals";
import { ethers, TransactionRequest } from "ethers";
import { config } from "./config/tests-config";
import { AssertionDaClient, AssertionDaError } from "./common/utils";
import { STATE_ORACLE_ADDRESS } from "./common/constants";
import CounterArtifact from "../../contracts/out/SimpleCounterAssertion.sol/Counter.json" assert { type: "json" };

const ADMIN_VERIFIER_OWNER_ADDRESS = "0x3e06372d794a48552203069915eA91b223297736" as const;
const stateOracleAbi = [
  "function ASSERTION_TIMELOCK_BLOCKS() view returns (uint128)",
  "function MAX_ASSERTIONS_PER_AA() view returns (uint128)",
  "function DA_VERIFIER() view returns (address)",
  "function owner() view returns (address)",
];
const adminVerifierAbi = [
  "function verifyAdmin(address contractAddress, address requester, bytes data) view returns (bool)",
];

const assertionDaEndpoint = config.getAssertionDaEndpoint();

// Skip the entire suite when the Credible stack is not running.
const describeCredible = assertionDaEndpoint ? describe : describe.skip;

// Gold Path:
// 1. When pcl store is executed
//    a. when the pcl store succeeds
//       i. the assertion should be retrievable from the assertion da
//          1. when a user submits a transaction to register the transaction on-chain
//             a. It should revert if the sender is not the AA manager
//             b. when the transaction is included
//                i. The assertion should be retrievable from the smart contract
//                ii. When block.number >= timelock
//                    1. A transaction incrementing the Counter contract should be included
//                       a. because the invariants only allow to increment it once
//                iii. When the block.number >= timelock
//                     1. An invalidating transaction should not be included in the block
//                        a. because the assertion invariant is triggered

describeCredible("Credible layer e2e test suite", () => {
  let assertionDaClient: AssertionDaClient;

  const knownAssertionId = process.env.CREDIBLE_TEST_ASSERTION_ID;
  const testAssertionArtifact = process.env.CREDIBLE_ASSERTION_ARTIFACT;
  const ctorArgsEnv = process.env.CREDIBLE_ASSERTION_CONSTRUCTOR_ARGS;
  const pclBinaryPath = process.env.CREDIBLE_PCL_PATH ?? "pcl";
  const pclWorkingDir = process.env.CREDIBLE_ASSERTION_WORKDIR;
  const parsedTimeout = process.env.CREDIBLE_ASSERTION_TIMEOUT_MS
    ? Number.parseInt(process.env.CREDIBLE_ASSERTION_TIMEOUT_MS, 10)
    : undefined;
  const pclTimeoutMs = Number.isNaN(parsedTimeout) ? undefined : parsedTimeout;

  const constructorArgs = ctorArgsEnv
    ? ctorArgsEnv
        .split(",")
        .map((arg) => arg.trim())
        .filter(Boolean)
    : [STATE_ORACLE_ADDRESS];

  beforeAll(() => {
    if (!assertionDaEndpoint) {
      throw new AssertionDaError("Assertion DA endpoint not configured");
    }

    assertionDaClient = new AssertionDaClient(assertionDaEndpoint, pclBinaryPath);
  });

  const readAssertion = async () => {
    if (!knownAssertionId) {
      throw new AssertionDaError("CREDIBLE_TEST_ASSERTION_ID is not configured");
    }

    expect.assertions(2);

    const assertion = await assertionDaClient.getAssertion(knownAssertionId);

    expect(assertion.bytecode).toBeDefined();
    expect(assertion.signature).toBeDefined();
  };

  if (knownAssertionId) {
    it("reads metadata for an existing assertion", readAssertion);
  } else {
    it.skip("reads metadata for an existing assertion", readAssertion);
  }

  const storeAssertion = async () => {
    if (!testAssertionArtifact) {
      throw new AssertionDaError("CREDIBLE_ASSERTION_ARTIFACT is not configured");
    }

    expect.assertions(2);

    const { assertionId, signature } = await assertionDaClient.storeAssertion(testAssertionArtifact, constructorArgs, {
      cwd: pclWorkingDir,
      timeoutMs: pclTimeoutMs,
    });

    expect(assertionId).toMatch(/^0x[0-9a-fA-F]+$/);
    expect(signature).toMatch(/^0x[0-9a-fA-F]+$/);
  };

  if (testAssertionArtifact) {
    it("stores a new assertion via the PCL CLI", storeAssertion);
  } else {
    it.skip("stores a new assertion via the PCL CLI", storeAssertion);
  }

  it("deploys the Counter contract after verifying Credible Layer core deployments", async () => {
    const logger = global.logger;
    const l2Provider = config.getL2Provider();
    const deployer = config.getL2AccountManager().whaleAccount(0);
    const deployerAddress = await deployer.getAddress();

    logger.info(
      `Using whale account to verify Credible Layer core contracts. deployer=${deployerAddress} stateOracle=${STATE_ORACLE_ADDRESS} adminVerifier=${ADMIN_VERIFIER_OWNER_ADDRESS}`,
    );

    const [stateOracleCode, adminVerifierCode] = await Promise.all([
      l2Provider.getCode(STATE_ORACLE_ADDRESS),
      l2Provider.getCode(ADMIN_VERIFIER_OWNER_ADDRESS),
    ]);

    expect(stateOracleCode).not.toEqual("0x");
    expect(adminVerifierCode).not.toEqual("0x");

    const stateOracle = new ethers.Contract(STATE_ORACLE_ADDRESS, stateOracleAbi, l2Provider);
    const adminVerifier = new ethers.Contract(ADMIN_VERIFIER_OWNER_ADDRESS, adminVerifierAbi, l2Provider);

    const [timelockBlocks, maxAssertionsPerAa, daVerifierAddress, stateOracleOwner] = await Promise.all([
      stateOracle.ASSERTION_TIMELOCK_BLOCKS(),
      stateOracle.MAX_ASSERTIONS_PER_AA(),
      stateOracle.DA_VERIFIER(),
      stateOracle.owner(),
    ]);

    expect(timelockBlocks > 0n).toBe(true);
    expect(maxAssertionsPerAa > 0n).toBe(true);
    expect(daVerifierAddress).not.toEqual(ethers.ZeroAddress);
    expect(stateOracleOwner).not.toEqual(ethers.ZeroAddress);

    const adminVerifierResult = await adminVerifier.verifyAdmin(STATE_ORACLE_ADDRESS, stateOracleOwner, "0x");
    expect(adminVerifierResult).toBe(true);

    const counterBytecode =
      typeof CounterArtifact.bytecode === "string" ? CounterArtifact.bytecode : CounterArtifact.bytecode.object;

    const counterFactory = new ethers.ContractFactory(CounterArtifact.abi, counterBytecode, deployer);
    logger.info(
      `Counter artifact info. bytecodeBytes=${counterBytecode ? (counterBytecode.length - 2) / 2 : 0} abiEntries=${CounterArtifact.abi.length}`,
    );

    const formatNullableBigInt = (value: bigint | null | undefined) => (value == null ? "null" : value.toString());

    const counterDeployOverrides: TransactionRequest = {};

    const feeData = await l2Provider.getFeeData();
    if (feeData.maxPriorityFeePerGas && feeData.maxFeePerGas) {
      counterDeployOverrides.maxPriorityFeePerGas = feeData.maxPriorityFeePerGas;
      counterDeployOverrides.maxFeePerGas = feeData.maxFeePerGas;
    } else if (feeData.gasPrice) {
      counterDeployOverrides.gasPrice = feeData.gasPrice;
    }

    const deployTx = await counterFactory.getDeployTransaction();
    const deployDataBytes = deployTx.data ? (deployTx.data.length - 2) / 2 : 0;
    logger.info(
      `Prepared Counter deployment transaction request. to=${deployTx.to ?? "<constructor>"} dataBytes=${deployDataBytes} value=${formatNullableBigInt(deployTx.value)}`,
    );
    if (deployDataBytes === 0) {
      logger.error(
        "Counter deployment transaction missing init code bytes; the artifact bytecode may be empty or not wired correctly.",
      );
    }
    const estimationRequest: TransactionRequest = {
      ...counterDeployOverrides,
      from: deployerAddress,
      to: deployTx.to,
      data: deployTx.data,
      value: deployTx.value,
    };
    const estimatedGas = await deployer.estimateGas(estimationRequest);
    const bufferedGas = (estimatedGas * 12n) / 10n;
    counterDeployOverrides.gasLimit = counterDeployOverrides.gasLimit
      ? counterDeployOverrides.gasLimit > bufferedGas
        ? counterDeployOverrides.gasLimit
        : bufferedGas
      : bufferedGas;

    const counterConstructorGas = await l2Provider.estimateGas(estimationRequest);
    logger.info(
      `Counter gas estimation. rawEstimate=${formatNullableBigInt(counterConstructorGas)} buffered=${formatNullableBigInt(
        (counterConstructorGas * 12n) / 10n,
      )}`,
    );
    const deploymentNonceBefore = await l2Provider.getTransactionCount(deployerAddress, "latest");
    const counterDeployTx = await deployer.sendTransaction({
      ...estimationRequest,
      gasLimit: (counterConstructorGas * 12n) / 10n,
    });
    logger.info(
      `Submitted Counter deployment transaction. hash=${counterDeployTx.hash} nonce=${counterDeployTx.nonce} from=${counterDeployTx.from} gasLimit=${formatNullableBigInt(counterDeployTx.gasLimit)} maxFeePerGas=${formatNullableBigInt(counterDeployTx.maxFeePerGas)} maxPriorityFeePerGas=${formatNullableBigInt(counterDeployTx.maxPriorityFeePerGas)} gasPrice=${formatNullableBigInt(counterDeployTx.gasPrice)} chainId=${formatNullableBigInt(counterDeployTx.chainId)}`,
    );
    const deploymentReceipt = await counterDeployTx.wait();
    expect(deploymentReceipt?.status).toEqual(1);

    const deploymentNonce = counterDeployTx.nonce ?? -1;
    expect(deploymentNonce).toBe(deploymentNonceBefore);

    const counterAddress = deploymentReceipt?.contractAddress ?? ethers.ZeroAddress;
    const confirmations =
      typeof deploymentReceipt?.confirmations === "function"
        ? await deploymentReceipt.confirmations()
        : deploymentReceipt?.confirmations ?? "null";
    logger.info(
      `Counter deployment receipt. txHash=${counterDeployTx.hash} status=${deploymentReceipt?.status ?? "null"} contractAddress=${counterAddress} blockNumber=${
        deploymentReceipt?.blockNumber ?? "null"
      } confirmations=${confirmations} gasUsed=${formatNullableBigInt(
        deploymentReceipt?.gasUsed,
      )} cumulativeGasUsed=${formatNullableBigInt(
        deploymentReceipt?.cumulativeGasUsed,
      )} effectiveGasPrice=${formatNullableBigInt(deploymentReceipt?.effectiveGasPrice)} type=${
        deploymentReceipt?.type ?? "null"
      }`,
    );
    if (counterAddress === ethers.ZeroAddress) {
      logger.error(
        `Counter deployment returned zero address. txHash=${counterDeployTx.hash} logsLength=${deploymentReceipt?.logs?.length ?? 0}`,
      );
    }
    expect(counterAddress).not.toEqual(ethers.ZeroAddress);

    const txOnChain = await l2Provider.getTransaction(counterDeployTx.hash);
    logger.info(
      `Counter deployment transaction on-chain. blockNumber=${txOnChain?.blockNumber ?? "null"} gasLimit=${formatNullableBigInt(
        txOnChain?.gasLimit,
      )} gasPrice=${formatNullableBigInt(txOnChain?.gasPrice)} maxFeePerGas=${formatNullableBigInt(
        txOnChain?.maxFeePerGas,
      )} maxPriorityFeePerGas=${formatNullableBigInt(txOnChain?.maxPriorityFeePerGas)} dataBytes=${
        txOnChain?.data ? (txOnChain.data.length - 2) / 2 : 0
      } value=${formatNullableBigInt(txOnChain?.value)}`,
    );

    if (deploymentReceipt) {
      const receiptLogSummary = (deploymentReceipt.logs ?? []).map((log) => ({
        index: log.index,
        address: log.address,
        topics: log.topics,
        dataBytes: (log.data.length - 2) / 2,
      }));
      logger.info(
        `Counter deployment receipt logs summary. logsCount=${receiptLogSummary.length} details=${JSON.stringify(receiptLogSummary)}`,
      );

      const deploymentBlock = deploymentReceipt.blockNumber
        ? await l2Provider.getBlock(deploymentReceipt.blockNumber)
        : null;
      if (deploymentBlock) {
        logger.info(
          `Counter deployment block info. blockNumber=${deploymentBlock.number} hash=${deploymentBlock.hash} parentHash=${deploymentBlock.parentHash} stateRoot=${deploymentBlock.stateRoot} txCount=${deploymentBlock.transactions.length}`,
        );
      }
    }

    logger.info(
      `Counter deployed for Credible Layer tests. address=${counterAddress} nonce=${deploymentNonce} deployer=${deployerAddress} gasUsed=${deploymentReceipt?.gasUsed}`,
    );
    if (counterAddress !== ethers.ZeroAddress) {
      const counterCodeAtBlock = deploymentReceipt?.blockNumber
        ? await l2Provider.getCode(counterAddress, deploymentReceipt.blockNumber)
        : null;
      if (counterCodeAtBlock != null) {
        logger.info(
          `Counter contract code at deployment block. blockNumber=${deploymentReceipt?.blockNumber ?? "null"} byteLength=${
            counterCodeAtBlock === "0x" ? 0 : (counterCodeAtBlock.length - 2) / 2
          }`,
        );
      }
      const counterCode = await l2Provider.getCode(counterAddress);
      if (counterCode === "0x") {
        logger.error(
          `Counter deployment produced empty code. address=${counterAddress} txHash=${counterDeployTx.hash} blockNumber=${deploymentReceipt?.blockNumber ?? "null"}`,
        );
      } else {
        logger.info(
          `Counter contract code detected. address=${counterAddress} byteLength=${(counterCode.length - 2) / 2}`,
        );
      }
      expect(counterCode).not.toEqual("0x");
    }
  });
});
