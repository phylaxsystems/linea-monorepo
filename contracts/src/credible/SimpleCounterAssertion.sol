// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.11;

import {Assertion} from "credible-std/Assertion.sol";

contract Counter {
    uint256 public number;
    address public owner = address(0x1B9AbEeC3215D8AdE8a33607f2cF0f4F60e5F0D0);

    function increment() public {
        number++;
    }
}

contract SimpleCounterAssertion is Assertion {
    event RunningAssertion(uint256 count);

    Counter public immutable counter;

    constructor(address counterAddress) {
        require(counterAddress != address(0), "Counter address cannot be zero");
        counter = Counter(counterAddress);
    }

    function assertCount() public {
        uint256 count = counter.number();
        emit RunningAssertion(count);
        if (count > 1) {
            revert("Counter cannot be greater than 1");
        }
    }

    function triggers() external view override {
        registerCallTrigger(this.assertCount.selector);
    }
}
