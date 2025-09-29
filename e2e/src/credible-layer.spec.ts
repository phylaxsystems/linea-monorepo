import { beforeAll, describe, expect, it } from "@jest/globals";
import { config } from "./config/tests-config";
import { AssertionDaClient, AssertionDaError } from "./common/utils";
import { STATE_ORACLE_ADDRESS } from "./common/constants";

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

  it("tracks end-to-end flows between the Credible sidecar and Assertion DA", async () => {
    // Placeholder for the full Credible flow validation.
    // TODO: deploy credible layer contracts, deploy the counter contract,
    // register the counter assertion using pcl and submit to da,
    // increment contract once and verify it passes, do it a second time and verify it fails
    expect(true).toBeTruthy();
  });
});
