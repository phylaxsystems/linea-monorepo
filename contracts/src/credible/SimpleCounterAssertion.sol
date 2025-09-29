// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.11;

import {Assertion} from "credible-std/Assertion.sol";

contract Counter {
    uint256 public number;

    function increment() public {
        number++;
    }
}

contract SimpleCounterAssertion is Assertion {
    event RunningAssertion(uint256 count);

    function assertCount() public {
        // FIXME: hardcoded address of the counter contract from above, needs changing
        // generated with cast compute-address 0x1B9AbEeC3215D8AdE8a33607f2cF0f4F60e5F0D0 --nonce 0
        uint256 count = Counter(0x729409FAD88CafdA895E41f9ED00Ef4094F8d130).number();
        emit RunningAssertion(count);
        if (count > 1) {
            revert("Counter cannot be greater than 1");
        }
    }

    function triggers() external view override {
        registerCallTrigger(this.assertCount.selector);
    }
}
