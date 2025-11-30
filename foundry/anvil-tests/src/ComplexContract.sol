// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract ComplexContract {
    struct User {
        uint256 id;
        string name;
    }

    event DataProcessed(uint256 indexed id, bytes32 indexed hash, uint256[] values);

    function processArray(uint256[] memory inputs) public pure returns (uint256[] memory) {
        uint256[] memory outputs = new uint256[](inputs.length);
        for (uint i = 0; i < inputs.length; i++) {
            outputs[i] = inputs[i] * 2;
        }
        return outputs;
    }

    function processStruct(User memory user) public pure returns (User memory) {
        return User(user.id + 1, string(abi.encodePacked("Hello, ", user.name)));
    }

    function processFixedBytes(bytes32 input) public pure returns (bytes32) {
        return input;
    }
    
    function emitComplexEvent(uint256 id, bytes32 hash, uint256[] memory values) public {
        emit DataProcessed(id, hash, values);
    }
}
