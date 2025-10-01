import { beforeAll, describe, expect, it } from "@jest/globals";
import { ethers, TransactionRequest } from "ethers";
import type { Signer } from "ethers";
import { config } from "./config/tests-config";
import { AssertionDaClient, AssertionDaError, pollForBlockNumber } from "./common/utils";
import { STATE_ORACLE_ADDRESS } from "./common/constants";
import CounterArtifact from "../../contracts/out/SimpleCounterAssertion.sol/Counter.json";
import SimpleCounterAssertionArtifact from "../../contracts/out/SimpleCounterAssertion.sol/SimpleCounterAssertion.json";

const ADMIN_VERIFIER_OWNER_ADDRESS = "0x3e06372d794a48552203069915eA91b223297736" as const;
const EXPECTED_COUNTER_ADDRESS = "0x3bAF7216467522Bb7cfd48f7f216867384296feB" as const;
const stateOracleAbi = [
  "function ASSERTION_TIMELOCK_BLOCKS() view returns (uint128)",
  "function MAX_ASSERTIONS_PER_AA() view returns (uint128)",
  "function DA_VERIFIER() view returns (address)",
  "function owner() view returns (address)",
  "function registerAssertionAdopter(address contractAddress, address adminVerifier, bytes data)",
  "function addAssertion(address contractAddress, bytes32 assertionId, bytes metadata, bytes proof)",
  "function hasAssertion(address contractAddress, bytes32 assertionId) view returns (bool)",
  "function getAssertionWindow(address contractAddress, bytes32 assertionId) view returns (uint128 activationBlock, uint128 deactivationBlock)",
  "function getManager(address contractAddress) view returns (address)",
  "function isAdminVerifierRegistered(address adminVerifier) view returns (bool)",
];
const adminVerifierAbi = [
  "function verifyAdmin(address contractAddress, address requester, bytes data) view returns (bool)",
];

const assertionDaEndpoint = config.getAssertionDaEndpoint();

const formatNullableBigInt = (value: bigint | null | undefined) => (value == null ? "null" : value.toString());

const normalizeAddress = (address: string) => ethers.getAddress(address);
const addressesEqual = (a: string, b: string) => normalizeAddress(a) === normalizeAddress(b);

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
  let latestAssertionId: string | null = null;

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
    : [EXPECTED_COUNTER_ADDRESS];

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

    const storeOptions: { cwd?: string; timeoutMs?: number } = {};
    if (pclWorkingDir) {
      storeOptions.cwd = pclWorkingDir;
    }
    if (pclTimeoutMs !== undefined) {
      storeOptions.timeoutMs = pclTimeoutMs;
    }

    const { assertionId, signature } = await assertionDaClient.storeAssertion(
      testAssertionArtifact,
      constructorArgs,
      storeOptions,
    );

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

    const counterAddress = await ensureCounterDeployment({ logger, l2Provider, deployer, deployerAddress });
    expect(counterAddress).toEqual(EXPECTED_COUNTER_ADDRESS);
  });

  it("registers the SimpleCounter assertion and approves it on the StateOracle", async () => {
    if (!assertionDaClient) {
      throw new AssertionDaError("Assertion DA client not initialized");
    }

    const logger = global.logger;
    const l2Provider = config.getL2Provider();
    const accountManager = config.getL2AccountManager();
    const defaultManagerSigner = accountManager.whaleAccount(0);
    const defaultManagerAddress = normalizeAddress(await defaultManagerSigner.getAddress());

    const stateOracleReadonly = new ethers.Contract(STATE_ORACLE_ADDRESS, stateOracleAbi, l2Provider);

    const adminVerifierRegistered = await stateOracleReadonly.isAdminVerifierRegistered(ADMIN_VERIFIER_OWNER_ADDRESS);
    expect(adminVerifierRegistered).toBe(true);

    const initialManager = await stateOracleReadonly.getManager(EXPECTED_COUNTER_ADDRESS);
    let managerSigner: Signer = defaultManagerSigner;
    let managerAddress = defaultManagerAddress;

    if (initialManager !== ethers.ZeroAddress && !addressesEqual(initialManager, defaultManagerAddress)) {
      const normalizedManager = normalizeAddress(initialManager);
      logger.info(
        `Counter already registered with manager ${normalizedManager}. Attempting to use RPC signer for transactions.`,
      );
      try {
        managerSigner = await l2Provider.getSigner(normalizedManager);
        managerAddress = normalizeAddress(await managerSigner.getAddress());
      } catch (error) {
        throw new Error(
          `Unable to obtain signer for registered counter manager ${normalizedManager}: ${(error as Error).message}`,
        );
      }
    } else if (initialManager === ethers.ZeroAddress) {
      logger.info(
        `Counter not yet registered with StateOracle. Using whale account ${defaultManagerAddress} as manager.`,
      );
    } else {
      logger.info(
        `Using existing counter manager ${defaultManagerAddress} from whale account for assertion registration.`,
      );
    }

    const stateOracle = stateOracleReadonly.connect(managerSigner);

    if (initialManager === ethers.ZeroAddress) {
      const registerTx = await stateOracle.registerAssertionAdopter(
        EXPECTED_COUNTER_ADDRESS,
        ADMIN_VERIFIER_OWNER_ADDRESS,
        "0x",
      );
      const registerReceipt = await registerTx.wait();
      expect(registerReceipt?.status).toEqual(1);

      const registeredManager = await stateOracleReadonly.getManager(EXPECTED_COUNTER_ADDRESS);
      expect(addressesEqual(registeredManager, managerAddress)).toBe(true);
      logger.info(
        `Registered counter contract with StateOracle. counter=${EXPECTED_COUNTER_ADDRESS} manager=${managerAddress} txHash=${registerTx.hash}`,
      );
    }

    const simpleAssertionBytecode =
      typeof SimpleCounterAssertionArtifact.bytecode === "string"
        ? SimpleCounterAssertionArtifact.bytecode
        : SimpleCounterAssertionArtifact.bytecode.object;

    if (!simpleAssertionBytecode || simpleAssertionBytecode === "0x") {
      throw new Error("SimpleCounterAssertion artifact bytecode is missing");
    }

    const simpleAssertionFactory = new ethers.ContractFactory(
      SimpleCounterAssertionArtifact.abi,
      simpleAssertionBytecode,
      managerSigner,
    );
    const simpleAssertionDeployRequest = await simpleAssertionFactory.getDeployTransaction(EXPECTED_COUNTER_ADDRESS);
    const simpleAssertionInitCode = simpleAssertionDeployRequest.data;

    if (!simpleAssertionInitCode || simpleAssertionInitCode === "0x") {
      throw new Error("SimpleCounterAssertion init code is missing");
    }

    const { assertionId, signature } = await assertionDaClient.submitAssertionBytecode(simpleAssertionInitCode);
    latestAssertionId = assertionId;
    expect(assertionId).toMatch(/^0x[0-9a-fA-F]{64}$/);
    expect(signature).toMatch(/^0x[0-9a-fA-F]+$/);

    logger.info(
      `Stored SimpleCounter assertion on DA. assertionId=${assertionId} signatureLength=${(signature.length - 2) / 2}`,
    );

    const assertionAlreadyTracked = await stateOracleReadonly.hasAssertion(EXPECTED_COUNTER_ADDRESS, assertionId);

    if (!assertionAlreadyTracked) {
      const addTx = await stateOracle.addAssertion(EXPECTED_COUNTER_ADDRESS, assertionId, "0x", signature);
      const addReceipt = await addTx.wait();
      expect(addReceipt?.status).toEqual(1);
      logger.info(
        `Assertion approved on-chain. assertionId=${assertionId} txHash=${addTx.hash} blockNumber=${
          addReceipt?.blockNumber ?? "null"
        }`,
      );
    } else {
      logger.info(`Assertion ${assertionId} already active for counter ${EXPECTED_COUNTER_ADDRESS}, skipping add.`);
    }

    const [activationBlock, deactivationBlock] = await stateOracleReadonly.getAssertionWindow(
      EXPECTED_COUNTER_ADDRESS,
      assertionId,
    );

    expect(activationBlock).toBeGreaterThan(0n);
    expect(deactivationBlock).toEqual(0n);

    logger.info(
      `Assertion window for counter ${EXPECTED_COUNTER_ADDRESS}. activationBlock=${activationBlock} deactivationBlock=${deactivationBlock}`,
    );
  });

  it("allows a single increment but rejects a second one once the assertion is active", async () => {
    if (!latestAssertionId) {
      throw new AssertionDaError(
        "SimpleCounter assertion id not captured. Ensure the registration test ran successfully.",
      );
    }

    const logger = global.logger;
    const l2Provider = config.getL2Provider();
    const accountManager = config.getL2AccountManager();
    const incrementSigner = accountManager.whaleAccount(1);
    const incrementAddress = normalizeAddress(await incrementSigner.getAddress());

    const stateOracleReadonly = new ethers.Contract(STATE_ORACLE_ADDRESS, stateOracleAbi, l2Provider);
    const [activationBlock] = await stateOracleReadonly.getAssertionWindow(EXPECTED_COUNTER_ADDRESS, latestAssertionId);
    const activationBlockNumber = Number(activationBlock);
    const currentBlockNumber = await l2Provider.getBlockNumber();

    if (currentBlockNumber < activationBlockNumber) {
      logger.info(
        `Waiting for SimpleCounter assertion activation. activationBlock=${activationBlockNumber} currentBlock=${currentBlockNumber}`,
      );
      const hasReachedActivation = await pollForBlockNumber(l2Provider, activationBlockNumber);
      expect(hasReachedActivation).toBe(true);
    }

    const counterReadonly = new ethers.Contract(EXPECTED_COUNTER_ADDRESS, CounterArtifact.abi, l2Provider);
    const counter = counterReadonly.connect(incrementSigner);

    const initialCounterValue = await counterReadonly.number();
    logger.info(
      `Attempting counter increments. signer=${incrementAddress} initialValue=${initialCounterValue} assertionId=${latestAssertionId}`,
    );

    const firstTx = await counter.increment();
    logger.info(`Submitted first increment transaction. txHash=${firstTx.hash}`);
    const firstReceipt = await firstTx.wait();
    expect(firstReceipt?.status).toEqual(1);

    const afterFirstIncrement = await counterReadonly.number();
    expect(afterFirstIncrement).toEqual(initialCounterValue + 1n);

    const secondTx = await counter.increment();
    logger.info(`Submitted second increment transaction. txHash=${secondTx.hash}`);

    let secondReceipt: ethers.TransactionReceipt | null = null;
    let secondErrorMessage: string | null = null;

    try {
      secondReceipt = await secondTx.wait();
    } catch (error) {
      secondErrorMessage = error instanceof Error ? error.message : String(error);
      if (
        typeof error === "object" &&
        error !== null &&
        "receipt" in error &&
        (error as { receipt?: ethers.TransactionReceipt | null }).receipt
      ) {
        secondReceipt = (error as { receipt?: ethers.TransactionReceipt | null }).receipt ?? null;
      }
      if (secondErrorMessage) {
        logger.info(`Second increment failed as expected. signer=${incrementAddress} error=${secondErrorMessage}`);
      }
    }

    if (secondReceipt) {
      expect(secondReceipt.status).not.toEqual(1);
    } else {
      expect(secondErrorMessage).toBeTruthy();
    }

    if (secondErrorMessage) {
      expect(secondErrorMessage.toLowerCase()).toContain("counter cannot be greater than 1");
    }

    const finalCounterValue = await counterReadonly.number();
    expect(finalCounterValue).toEqual(afterFirstIncrement);
  });
});

type CounterDeploymentContext = {
  logger: typeof global.logger;
  l2Provider: ReturnType<typeof config.getL2Provider>;
  deployer: ReturnType<ReturnType<typeof config.getL2AccountManager>["whaleAccount"]>;
  deployerAddress: string;
};

const ensureCounterDeployment = async ({ logger, l2Provider, deployer, deployerAddress }: CounterDeploymentContext) => {
  const existingCode = await l2Provider.getCode(EXPECTED_COUNTER_ADDRESS);
  if (existingCode !== "0x") {
    logger.info(
      `Counter already deployed at expected address. address=${EXPECTED_COUNTER_ADDRESS} byteLength=${(existingCode.length - 2) / 2}`,
    );
    return EXPECTED_COUNTER_ADDRESS;
  }

  logger.info(
    `No counter code detected at expected address. proceeding with deployment. address=${EXPECTED_COUNTER_ADDRESS} deployer=${deployerAddress}`,
  );

  const counterBytecode =
    typeof CounterArtifact.bytecode === "string" ? CounterArtifact.bytecode : CounterArtifact.bytecode.object;

  const counterFactory = new ethers.ContractFactory(CounterArtifact.abi, counterBytecode, deployer);
  logger.info(
    `Counter artifact info. bytecodeBytes=${counterBytecode ? (counterBytecode.length - 2) / 2 : 0} abiEntries=${CounterArtifact.abi.length}`,
  );

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
    `Prepared Counter deployment transaction request. target=<constructor> dataBytes=${deployDataBytes} value=${formatNullableBigInt(
      deployTx.value ?? null,
    )}`,
  );
  if (deployDataBytes === 0) {
    logger.error(
      "Counter deployment transaction missing init code bytes; the artifact bytecode may be empty or not wired correctly.",
    );
  }
  const estimationRequest: TransactionRequest = {
    ...counterDeployOverrides,
    from: deployerAddress,
    data: deployTx.data,
  };
  if (deployTx.value != null) {
    estimationRequest.value = deployTx.value;
  }
  const estimatedGas = await deployer.estimateGas(estimationRequest);
  const bufferedGas = (estimatedGas * 12n) / 10n;
  counterDeployOverrides.gasLimit = bufferedGas;

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
    )} effectiveGasPrice=${formatNullableBigInt(
      (deploymentReceipt as { effectiveGasPrice?: bigint } | null)?.effectiveGasPrice,
    )} type=${deploymentReceipt?.type ?? "null"}`,
  );
  if (counterAddress === ethers.ZeroAddress) {
    logger.error(
      `Counter deployment returned zero address. txHash=${counterDeployTx.hash} logsLength=${deploymentReceipt?.logs?.length ?? 0}`,
    );
  }
  expect(counterAddress).not.toEqual(ethers.ZeroAddress);
  if (counterAddress !== EXPECTED_COUNTER_ADDRESS) {
    throw new Error(
      `Counter deployed at unexpected address. expected=${EXPECTED_COUNTER_ADDRESS} actual=${counterAddress} txHash=${counterDeployTx.hash}`,
    );
  }

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
  const counterCode = await l2Provider.getCode(EXPECTED_COUNTER_ADDRESS);
  if (counterCode === "0x") {
    logger.error(
      `Counter deployment produced empty code. address=${EXPECTED_COUNTER_ADDRESS} txHash=${counterDeployTx.hash} blockNumber=${deploymentReceipt?.blockNumber ?? "null"}`,
    );
  } else {
    logger.info(
      `Counter contract code detected. address=${EXPECTED_COUNTER_ADDRESS} byteLength=${(counterCode.length - 2) / 2}`,
    );
  }
  expect(counterCode).not.toEqual("0x");

  return EXPECTED_COUNTER_ADDRESS;
};
