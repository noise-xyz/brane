// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract RevertExample {
    function alwaysRevert() external pure {
        revert("simple reason");
    }

    function echo(uint256 x) external pure returns (uint256) {
        return x;
    }

    function triggerPanic() external pure {
        uint256 a = 1;
        uint256 b = 0;
        uint256 c = a / b; // Panic(0x12)
    }
}
