// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract RevertExample {
    function alwaysRevert() external pure {
        revert("simple reason");
    }

    function echo(uint256 x) external pure returns (uint256) {
        return x;
    }
}
