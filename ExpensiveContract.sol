pragma solidity ^0.8.29;

contract ModuleOverflow {
    // Trigger PRECOMPILE_ECRECOVER_EFFECTIVE_CALLS overflow (limit: 128)
    function triggerEcrecoverOverflow() external {
        bytes32 hash = keccak256("test");
        uint8 v = 27;
        bytes32 r = bytes32(uint256(1));
        bytes32 s = bytes32(uint256(2));
        
        // Call ecrecover 150+ times to exceed limit of 128
        for (uint256 i = 0; i < 150; i++) {
            ecrecover(hash, v, r, s);
        }
    }
    
    // Trigger PRECOMPILE_ECMUL_EFFECTIVE_CALLS overflow (limit: 32)
    function triggerEcmulOverflow() external {
        // EC point multiplication precompile at 0x07
        bytes memory input = abi.encode(
            uint256(1),  // x
            uint256(2),  // y
            uint256(3)   // scalar
        );
        
        for (uint256 i = 0; i < 50; i++) {
            (bool success,) = address(0x07).staticcall(input);
        }
    }
    
    // Trigger EXP module overflow
    function triggerExpOverflow() external {
        uint256 result = 1;
        for (uint256 i = 0; i < 10000; i++) {
            result = 2 ** (i % 256);
        }
    }
    
    // Trigger excessive keccak operations
    function triggerKeccakOverflow() external {
        bytes32 hash = keccak256("start");
        for (uint256 i = 0; i < 10000; i++) {
            hash = keccak256(abi.encodePacked(hash, i));
        }
    }
}

contract CalldataOverflow {
    function triggerCalldataOverflow() external {
        // Create a transaction with >70KB calldata
        bytes memory largeData = new bytes(71000);
        // This will be rejected at pre-processing
    }
}

contract GasHog {
    mapping(uint256 => uint256) public data;
    
    function expensiveOperation() external {
        // Write to storage ~1500 times (each SSTORE costs ~20k gas)
        for (uint256 i = 0; i < 10; i++) {
            data[i] = i;
        }
    }

    function checkExpensiveResult() external {
        for (uint256 i = 0; i < 10; i++) {
            if (data[i] != i) {
                revert();
            }
        }
    }
}

contract CodehashCheck {
    function checkCodehash(address target, bytes32 checkHash) external view returns (bool) {
        bytes32 codehash;
        assembly {
            codehash := extcodehash(target)
        }
        if (codehash == checkHash) {
            return true;
        } else {
            revert();
        }
    }
}