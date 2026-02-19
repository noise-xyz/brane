// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import sh.brane.core.abi.Abi;
import sh.brane.core.builder.TxBuilder;
import sh.brane.core.crypto.PrivateKeySigner;
import sh.brane.core.crypto.Signature;
import sh.brane.core.crypto.eip3009.Eip3009;
import sh.brane.core.crypto.eip712.Eip712Domain;
import sh.brane.core.error.RevertException;
import sh.brane.core.model.TransactionReceipt;
import sh.brane.core.model.TransactionRequest;
import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;
import sh.brane.core.types.Wei;
import sh.brane.rpc.test.BraneTestExtension;

/**
 * Integration tests for EIP-3009 authorization signing against a live Anvil node.
 *
 * <p>Deploys a mock EIP-3009 token contract, signs authorizations using the
 * Brane EIP-3009 library, and submits them on-chain to verify correctness.
 *
 * <p>Requires Anvil running on localhost:8545.
 *
 * @see <a href="https://eips.ethereum.org/EIPS/eip-3009">EIP-3009</a>
 */
@ExtendWith(BraneTestExtension.class)
@EnabledIfSystemProperty(named = "brane.integration.tests", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("EIP-3009 Integration Tests")
class Eip3009IntegrationTest {

    // Anvil's default funded account
    private static final String PRIVATE_KEY =
        "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    // Second Anvil account (for receiving)
    private static final String RECIPIENT_KEY =
        "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";

    private static final String TOKEN_NAME = "USD Coin";
    private static final String TOKEN_VERSION = "2";
    private static final BigInteger INITIAL_SUPPLY = BigInteger.valueOf(1_000_000_000); // 1000 USDC (6 decimals)

    // @formatter:off
    private static final String MOCK_TOKEN_ABI = """
        [
          {"type":"constructor","inputs":[{"name":"_name","type":"string"},{"name":"_version","type":"string"},{"name":"initialSupply","type":"uint256"}],"stateMutability":"nonpayable"},
          {"type":"function","name":"balanceOf","inputs":[{"name":"account","type":"address"}],"outputs":[{"name":"","type":"uint256"}],"stateMutability":"view"},
          {"type":"function","name":"authorizationState","inputs":[{"name":"authorizer","type":"address"},{"name":"nonce","type":"bytes32"}],"outputs":[{"name":"","type":"bool"}],"stateMutability":"view"},
          {"type":"function","name":"transferWithAuthorization","inputs":[{"name":"from","type":"address"},{"name":"to","type":"address"},{"name":"value","type":"uint256"},{"name":"validAfter","type":"uint256"},{"name":"validBefore","type":"uint256"},{"name":"nonce","type":"bytes32"},{"name":"v","type":"uint8"},{"name":"r","type":"bytes32"},{"name":"s","type":"bytes32"}],"outputs":[],"stateMutability":"nonpayable"},
          {"type":"function","name":"receiveWithAuthorization","inputs":[{"name":"from","type":"address"},{"name":"to","type":"address"},{"name":"value","type":"uint256"},{"name":"validAfter","type":"uint256"},{"name":"validBefore","type":"uint256"},{"name":"nonce","type":"bytes32"},{"name":"v","type":"uint8"},{"name":"r","type":"bytes32"},{"name":"s","type":"bytes32"}],"outputs":[],"stateMutability":"nonpayable"},
          {"type":"function","name":"cancelAuthorization","inputs":[{"name":"authorizer","type":"address"},{"name":"nonce","type":"bytes32"},{"name":"v","type":"uint8"},{"name":"r","type":"bytes32"},{"name":"s","type":"bytes32"}],"outputs":[],"stateMutability":"nonpayable"}
        ]
        """;

    private static final String MOCK_TOKEN_BYTECODE =
        "0x608060405234801561000f575f5ffd5b50604051611f36380380611f36833981810160405281019061003191906102e2565b825f908161003f9190610582565b50816001908161004f9190610582565b508060025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20819055503373ffffffffffffffffffffffffffffffffffffffff165f73ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef836040516100ef9190610660565b60405180910390a37f8b73c3c69bb8fe3d512ecc4cf759cc79239f7b179b0ffacaa9a75d522b39400f83805190602001208380519060200120463060405160200161013e9594939291906106d0565b60405160208183030381529060405280519060200120600481905550505050610721565b5f604051905090565b5f5ffd5b5f5ffd5b5f5ffd5b5f5ffd5b5f601f19601f8301169050919050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52604160045260245ffd5b6101c18261017b565b810181811067ffffffffffffffff821117156101e0576101df61018b565b5b80604052505050565b5f6101f2610162565b90506101fe82826101b8565b919050565b5f67ffffffffffffffff82111561021d5761021c61018b565b5b6102268261017b565b9050602081019050919050565b8281835e5f83830152505050565b5f61025361024e84610203565b6101e9565b90508281526020810184848401111561026f5761026e610177565b5b61027a848285610233565b509392505050565b5f82601f83011261029657610295610173565b5b81516102a6848260208601610241565b91505092915050565b5f819050919050565b6102c1816102af565b81146102cb575f5ffd5b50565b5f815190506102dc816102b8565b92915050565b5f5f5f606084860312156102f9576102f861016b565b5b5f84015167ffffffffffffffff8111156103165761031561016f565b5b61032286828701610282565b935050602084015167ffffffffffffffff8111156103435761034261016f565b5b61034f86828701610282565b9250506040610360868287016102ce565b9150509250925092565b5f81519050919050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52602260045260245ffd5b5f60028204905060018216806103b857607f821691505b6020821081036103cb576103ca610374565b5b50919050565b5f819050815f5260205f209050919050565b5f6020601f8301049050919050565b5f82821b905092915050565b5f6008830261042d7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff826103f2565b61043786836103f2565b95508019841693508086168417925050509392505050565b5f819050919050565b5f61047261046d610468846102af565b61044f565b6102af565b9050919050565b5f819050919050565b61048b83610458565b61049f61049782610479565b8484546103fe565b825550505050565b5f5f905090565b6104b66104a7565b6104c1818484610482565b505050565b5f5b828110156104e7576104dc5f8284016104ae565b6001810190506104c8565b505050565b601f82111561053a578282111561053957610506816103d1565b61050f846103e3565b610518856103e3565b6020861015610525575f90505b808301610534828403826104c6565b505050505b5b505050565b5f82821c905092915050565b5f61055a5f198460080261053f565b1980831691505092915050565b5f610572838361054b565b9150826002028217905092915050565b61058b8261036a565b67ffffffffffffffff8111156105a4576105a361018b565b5b6105ae82546103a1565b6105b98282856104ec565b5f60209050601f8311600181146105ea575f84156105d8578287015190505b6105e28582610567565b865550610649565b601f1984166105f8866103d1565b5f5b8281101561061f578489015182556001820191506020850194506020810190506105fa565b8683101561063c5784890151610638601f89168261054b565b8355505b6001600288020188555050505b505050505050565b61065a816102af565b82525050565b5f6020820190506106735f830184610651565b92915050565b5f819050919050565b61068b81610679565b82525050565b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f6106ba82610691565b9050919050565b6106ca816106b0565b82525050565b5f60a0820190506106e35f830188610682565b6106f06020830187610682565b6106fd6040830186610682565b61070a6060830185610651565b61071760808301846106c1565b9695505050505050565b6118088061072e5f395ff3fe608060405234801561000f575f5ffd5b5060043610610091575f3560e01c80635a049a70116100645780635a049a701461010d57806370a0823114610129578063e3ee160e14610159578063e94a010214610175578063ef55bec6146101a557610091565b806306fdde0314610095578063313ce567146100b35780633644e515146100d157806354fd4d50146100ef575b5f5ffd5b61009d6101c1565b6040516100aa9190610fd3565b60405180910390f35b6100bb61024c565b6040516100c8919061100e565b60405180910390f35b6100d9610251565b6040516100e6919061103f565b60405180910390f35b6100f7610257565b6040516101049190610fd3565b60405180910390f35b6101276004803603810190610122919061110a565b6102e3565b005b610143600480360381019061013e9190611181565b61059f565b60405161015091906111c4565b60405180910390f35b610173600480360381019061016e9190611207565b6105e5565b005b61018f600480360381019061018a91906112cb565b610a3c565b60405161019c9190611323565b60405180910390f35b6101bf60048036038101906101ba9190611207565b610a9e565b005b5f80546101cd90611369565b80601f01602080910402602001604051908101604052809291908181526020018280546101f990611369565b80156102445780601f1061021b57610100808354040283529160200191610244565b820191905f5260205f20905b81548152906001019060200180831161022757829003601f168201915b505050505081565b600681565b60045481565b6001805461026490611369565b80601f016020809104026020016040519081016040528092919081815260200182805461029090611369565b80156102db5780601f106102b2576101008083540402835291602001916102db565b820191905f5260205f20905b8154815290600101906020018083116102be57829003601f168201915b505050505081565b60035f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8581526020019081526020015f205f9054906101000a900460ff161561037c576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610373906113e3565b60405180910390fd5b5f7f158b0a9edf7a828aad02f63cd515c68ef2f50ba807396f6d12842833a159742986866040516020016103b293929190611410565b6040516020818303038152906040528051906020012090505f600454826040516020016103e09291906114b9565b6040516020818303038152906040528051906020012090505f6001828787876040515f815260200160405260405161041b94939291906114ef565b6020604051602081039080840390855afa15801561043b573d5f5f3e3d5ffd5b5050506020604051035190505f73ffffffffffffffffffffffffffffffffffffffff168173ffffffffffffffffffffffffffffffffffffffff16141580156104ae57508773ffffffffffffffffffffffffffffffffffffffff168173ffffffffffffffffffffffffffffffffffffffff16145b6104ed576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016104e49061157c565b60405180910390fd5b600160035f8a73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8981526020019081526020015f205f6101000a81548160ff021916908315150217905550868873ffffffffffffffffffffffffffffffffffffffff167f1cdd46ff242716cdaa72d159d339a485b3438398348d68f09d7c8c0a59353d8160405160405180910390a35050505050505050565b5f60025f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20549050919050565b854211610627576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161061e906115e4565b60405180910390fd5b844210610669576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016106609061164c565b60405180910390fd5b60035f8a73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8581526020019081526020015f205f9054906101000a900460ff1615610702576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016106f9906113e3565b60405180910390fd5b5f7f7c7c6cdb67a18743f49ec6fa9b35f50d52ed05cbed4cc592e13b44501c1a22678a8a8a8a8a8a604051602001610740979695949392919061166a565b6040516020818303038152906040528051906020012090505f6004548260405160200161076e9291906114b9565b6040516020818303038152906040528051906020012090505f6001828787876040515f81526020016040526040516107a994939291906114ef565b6020604051602081039080840390855afa1580156107c9573d5f5f3e3d5ffd5b5050506020604051035190505f73ffffffffffffffffffffffffffffffffffffffff168173ffffffffffffffffffffffffffffffffffffffff161415801561083c57508b73ffffffffffffffffffffffffffffffffffffffff168173ffffffffffffffffffffffffffffffffffffffff16145b61087b576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016108729061157c565b60405180910390fd5b600160035f8e73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8981526020019081526020015f205f6101000a81548160ff0219169083151502179055508960025f8e73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f82825461092b9190611704565b925050819055508960025f8d73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f82825461097e9190611737565b925050819055508a73ffffffffffffffffffffffffffffffffffffffff168c73ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef8c6040516109e291906111c4565b60405180910390a3868c73ffffffffffffffffffffffffffffffffffffffff167f98de503528ee59b575ef0c0a2576a82497bfc029a5685b209e9ec333479b10a560405160405180910390a3505050505050505050505050565b5f60035f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8381526020019081526020015f205f9054906101000a900460ff16905092915050565b3373ffffffffffffffffffffffffffffffffffffffff168873ffffffffffffffffffffffffffffffffffffffff1614610b0c576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610b03906117b4565b60405180910390fd5b854211610b4e576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610b45906115e4565b60405180910390fd5b844210610b90576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610b879061164c565b60405180910390fd5b60035f8a73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8581526020019081526020015f205f9054906101000a900460ff1615610c29576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610c20906113e3565b60405180910390fd5b5f7fd099cc98ef71107a616c4f0f941f04c322d8e254fe26b3c6668db87aae413de88a8a8a8a8a8a604051602001610c67979695949392919061166a565b6040516020818303038152906040528051906020012090505f60045482604051602001610c959291906114b9565b6040516020818303038152906040528051906020012090505f6001828787876040515f8152602001604052604051610cd094939291906114ef565b6020604051602081039080840390855afa158015610cf0573d5f5f3e3d5ffd5b5050506020604051035190505f73ffffffffffffffffffffffffffffffffffffffff168173ffffffffffffffffffffffffffffffffffffffff1614158015610d6357508b73ffffffffffffffffffffffffffffffffffffffff168173ffffffffffffffffffffffffffffffffffffffff16145b610da2576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610d999061157c565b60405180910390fd5b600160035f8e73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8981526020019081526020015f205f6101000a81548160ff0219169083151502179055508960025f8e73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f828254610e529190611704565b925050819055508960025f8d73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f828254610ea59190611737565b925050819055508a73ffffffffffffffffffffffffffffffffffffffff168c73ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef8c604051610f0991906111c4565b60405180910390a3868c73ffffffffffffffffffffffffffffffffffffffff167f98de503528ee59b575ef0c0a2576a82497bfc029a5685b209e9ec333479b10a560405160405180910390a3505050505050505050505050565b5f81519050919050565b5f82825260208201905092915050565b8281835e5f83830152505050565b5f601f19601f8301169050919050565b5f610fa582610f63565b610faf8185610f6d565b9350610fbf818560208601610f7d565b610fc881610f8b565b840191505092915050565b5f6020820190508181035f830152610feb8184610f9b565b905092915050565b5f60ff82169050919050565b61100881610ff3565b82525050565b5f6020820190506110215f830184610fff565b92915050565b5f819050919050565b61103981611027565b82525050565b5f6020820190506110525f830184611030565b92915050565b5f5ffd5b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f6110858261105c565b9050919050565b6110958161107b565b811461109f575f5ffd5b50565b5f813590506110b08161108c565b92915050565b6110bf81611027565b81146110c9575f5ffd5b50565b5f813590506110da816110b6565b92915050565b6110e981610ff3565b81146110f3575f5ffd5b50565b5f81359050611104816110e0565b92915050565b5f5f5f5f5f60a0868803121561112357611122611058565b5b5f611130888289016110a2565b9550506020611141888289016110cc565b9450506040611152888289016110f6565b9350506060611163888289016110cc565b9250506080611174888289016110cc565b9150509295509295909350565b5f6020828403121561119657611195611058565b5b5f6111a3848285016110a2565b91505092915050565b5f819050919050565b6111be816111ac565b82525050565b5f6020820190506111d75f8301846111b5565b92915050565b6111e6816111ac565b81146111f0575f5ffd5b50565b5f81359050611201816111dd565b92915050565b5f5f5f5f5f5f5f5f5f6101208a8c03121561122557611224611058565b5b5f6112328c828d016110a2565b99505060206112438c828d016110a2565b98505060406112548c828d016111f3565b97505060606112658c828d016111f3565b96505060806112768c828d016111f3565b95505060a06112878c828d016110cc565b94505060c06112988c828d016110f6565b93505060e06112a98c828d016110cc565b9250506101006112bb8c828d016110cc565b9150509295985092959850929598565b5f5f604083850312156112e1576112e0611058565b5b5f6112ee858286016110a2565b92505060206112ff858286016110cc565b9150509250929050565b5f8115159050919050565b61131d81611309565b82525050565b5f6020820190506113365f830184611314565b92915050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52602260045260245ffd5b5f600282049050600182168061138057607f821691505b6020821081036113935761139261133c565b5b50919050565b7f417574686f72697a6174696f6e20616c726561647920757365640000000000005f82015250565b5f6113cd601a83610f6d565b91506113d882611399565b602082019050919050565b5f6020820190508181035f8301526113fa816113c1565b9050919050565b61140a8161107b565b82525050565b5f6060820190506114235f830186611030565b6114306020830185611401565b61143d6040830184611030565b949350505050565b5f81905092915050565b7f19010000000000000000000000000000000000000000000000000000000000005f82015250565b5f611483600283611445565b915061148e8261144f565b600282019050919050565b5f819050919050565b6114b36114ae82611027565b611499565b82525050565b5f6114c382611477565b91506114cf82856114a2565b6020820191506114df82846114a2565b6020820191508190509392505050565b5f6080820190506115025f830187611030565b61150f6020830186610fff565b61151c6040830185611030565b6115296060830184611030565b95945050505050565b7f496e76616c6964207369676e61747572650000000000000000000000000000005f82015250565b5f611566601183610f6d565b915061157182611532565b602082019050919050565b5f6020820190508181035f8301526115938161155a565b9050919050565b7f417574686f72697a6174696f6e206e6f74207965742076616c696400000000005f82015250565b5f6115ce601b83610f6d565b91506115d98261159a565b602082019050919050565b5f6020820190508181035f8301526115fb816115c2565b9050919050565b7f417574686f72697a6174696f6e206578706972656400000000000000000000005f82015250565b5f611636601583610f6d565b915061164182611602565b602082019050919050565b5f6020820190508181035f8301526116638161162a565b9050919050565b5f60e08201905061167d5f83018a611030565b61168a6020830189611401565b6116976040830188611401565b6116a460608301876111b5565b6116b160808301866111b5565b6116be60a08301856111b5565b6116cb60c0830184611030565b98975050505050505050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52601160045260245ffd5b5f61170e826111ac565b9150611719836111ac565b9250828203905081811115611731576117306116d7565b5b92915050565b5f611741826111ac565b915061174c836111ac565b9250828201905080821115611764576117636116d7565b5b92915050565b7f43616c6c6572206d7573742062652074686520706179656500000000000000005f82015250565b5f61179e601883610f6d565b91506117a98261176a565b602082019050919050565b5f6020820190508181035f8301526117cb81611792565b905091905056fea2646970667358221220bb176b439f132955b64fe6e82a399597edd3038ae5000a536e23b227620e04c364736f6c63430008210033";
    // @formatter:on

    private PrivateKeySigner signer;
    private PrivateKeySigner recipientSigner;
    private Address tokenAddress;
    private Eip712Domain domain;
    private Abi abi;

    @BeforeAll
    void deployToken(Brane.Signer client) {
        signer = new PrivateKeySigner(PRIVATE_KEY);
        recipientSigner = new PrivateKeySigner(RECIPIENT_KEY);

        abi = Abi.fromJson(MOCK_TOKEN_ABI);
        HexData encodedArgs = abi.encodeConstructor(TOKEN_NAME, TOKEN_VERSION, INITIAL_SUPPLY);
        String data = MOCK_TOKEN_BYTECODE + encodedArgs.value().substring(2);

        TransactionRequest request = TxBuilder.eip1559()
            .data(new HexData(data))
            .value(Wei.ZERO)
            .build();

        TransactionReceipt receipt = client.sendTransactionAndWait(request, 30_000, 500);
        assertTrue(receipt.status(), "Token deployment failed");
        tokenAddress = receipt.contractAddress();

        // Anvil uses chain ID 31337
        domain = Eip3009.tokenDomain(TOKEN_NAME, TOKEN_VERSION, 31337L, tokenAddress);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════════

    private BigInteger getBalance(Brane client, Address account) {
        Abi.FunctionCall call = abi.encodeFunction("balanceOf", account);
        HexData result = client.call(CallRequest.of(tokenAddress, new HexData(call.data())));
        return call.decode(result.value(), BigInteger.class);
    }

    private boolean getAuthorizationState(Brane client, Address authorizer, byte[] nonce) {
        Abi.FunctionCall call = abi.encodeFunction("authorizationState", authorizer, nonce);
        HexData result = client.call(CallRequest.of(tokenAddress, new HexData(call.data())));
        return call.decode(result.value(), Boolean.class);
    }

    // ═══════════════════════════════════════════════════════════════════
    // transferWithAuthorization
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("transferWithAuthorization: sign and submit on-chain, verify balance changed")
    void transferWithAuthorization_works(Brane.Signer client) {
        Address from = signer.address();
        Address to = recipientSigner.address();
        BigInteger value = BigInteger.valueOf(1_000_000); // 1 USDC

        BigInteger fromBefore = getBalance(client, from);
        BigInteger toBefore = getBalance(client, to);

        byte[] nonce = Eip3009.randomNonce();
        var auth = Eip3009.transferAuthorization(
            from, to, value, BigInteger.ZERO, BigInteger.valueOf(2_000_000_000L), nonce);
        Signature sig = Eip3009.sign(auth, domain, signer);

        // Submit via any account (relayer pattern)
        Abi.FunctionCall call = abi.encodeFunction("transferWithAuthorization",
            from, to, value,
            BigInteger.ZERO, BigInteger.valueOf(2_000_000_000L), nonce,
            BigInteger.valueOf(sig.v()), sig.r(), sig.s());

        TransactionRequest txRequest = TxBuilder.eip1559()
            .to(tokenAddress)
            .data(new HexData(call.data()))
            .build();

        TransactionReceipt receipt = client.sendTransactionAndWait(txRequest, 30_000, 500);
        assertTrue(receipt.status(), "transferWithAuthorization failed");

        BigInteger fromAfter = getBalance(client, from);
        BigInteger toAfter = getBalance(client, to);
        assertEquals(fromBefore.subtract(value), fromAfter);
        assertEquals(toBefore.add(value), toAfter);
    }

    @Test
    @Order(2)
    @DisplayName("authorizationState returns true after transferWithAuthorization")
    void authorizationState_trueAfterTransfer(Brane.Signer client) {
        Address from = signer.address();
        Address to = recipientSigner.address();
        BigInteger value = BigInteger.valueOf(100);

        byte[] nonce = Eip3009.randomNonce();
        var auth = Eip3009.transferAuthorization(
            from, to, value, BigInteger.ZERO, BigInteger.valueOf(2_000_000_000L), nonce);
        Signature sig = Eip3009.sign(auth, domain, signer);

        Abi.FunctionCall call = abi.encodeFunction("transferWithAuthorization",
            from, to, value,
            BigInteger.ZERO, BigInteger.valueOf(2_000_000_000L), nonce,
            BigInteger.valueOf(sig.v()), sig.r(), sig.s());

        TransactionRequest txRequest = TxBuilder.eip1559()
            .to(tokenAddress)
            .data(new HexData(call.data()))
            .build();

        client.sendTransactionAndWait(txRequest, 30_000, 500);

        assertTrue(getAuthorizationState(client, from, nonce));
    }

    // ═══════════════════════════════════════════════════════════════════
    // receiveWithAuthorization
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("receiveWithAuthorization: payee submits on-chain, verify balance changed")
    void receiveWithAuthorization_works(Brane.Tester tester) {
        Address from = signer.address();
        Address to = recipientSigner.address();
        BigInteger value = BigInteger.valueOf(500_000); // 0.5 USDC

        BigInteger fromBefore = getBalance(tester, from);
        BigInteger toBefore = getBalance(tester, to);

        byte[] nonce = Eip3009.randomNonce();
        var auth = Eip3009.receiveAuthorization(
            from, to, value, BigInteger.ZERO, BigInteger.valueOf(2_000_000_000L), nonce);
        Signature sig = Eip3009.sign(auth, domain, signer);

        Abi.FunctionCall call = abi.encodeFunction("receiveWithAuthorization",
            from, to, value,
            BigInteger.ZERO, BigInteger.valueOf(2_000_000_000L), nonce,
            BigInteger.valueOf(sig.v()), sig.r(), sig.s());

        // Must be submitted by the payee (msg.sender == to)
        try (ImpersonationSession session = tester.impersonate(to)) {
            TransactionRequest txRequest = TxBuilder.eip1559()
                .to(tokenAddress)
                .data(new HexData(call.data()))
                .build();

            TransactionReceipt receipt = session.sendTransactionAndWait(txRequest);
            assertTrue(receipt.status(), "receiveWithAuthorization failed");
        }

        BigInteger fromAfter = getBalance(tester, from);
        BigInteger toAfter = getBalance(tester, to);
        assertEquals(fromBefore.subtract(value), fromAfter);
        assertEquals(toBefore.add(value), toAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    // cancelAuthorization
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("cancelAuthorization: cancel then verify transfer fails")
    void cancelAuthorization_preventsTransfer(Brane.Signer client) {
        Address from = signer.address();
        Address to = recipientSigner.address();
        BigInteger value = BigInteger.valueOf(100);

        byte[] nonce = Eip3009.randomNonce();

        // Sign a cancel for this nonce
        var cancel = Eip3009.cancelAuthorization(from, nonce);
        Signature cancelSig = Eip3009.sign(cancel, domain, signer);

        Abi.FunctionCall cancelCall = abi.encodeFunction("cancelAuthorization",
            from, nonce,
            BigInteger.valueOf(cancelSig.v()), cancelSig.r(), cancelSig.s());

        TransactionRequest cancelTx = TxBuilder.eip1559()
            .to(tokenAddress)
            .data(new HexData(cancelCall.data()))
            .build();

        TransactionReceipt cancelReceipt = client.sendTransactionAndWait(cancelTx, 30_000, 500);
        assertTrue(cancelReceipt.status(), "cancelAuthorization failed");

        // Verify nonce is consumed
        assertTrue(getAuthorizationState(client, from, nonce));

        // Now try to use the same nonce for a transfer — should revert
        var auth = Eip3009.transferAuthorization(
            from, to, value, BigInteger.ZERO, BigInteger.valueOf(2_000_000_000L), nonce);
        Signature transferSig = Eip3009.sign(auth, domain, signer);

        Abi.FunctionCall transferCall = abi.encodeFunction("transferWithAuthorization",
            from, to, value,
            BigInteger.ZERO, BigInteger.valueOf(2_000_000_000L), nonce,
            BigInteger.valueOf(transferSig.v()), transferSig.r(), transferSig.s());

        TransactionRequest transferTx = TxBuilder.eip1559()
            .to(tokenAddress)
            .data(new HexData(transferCall.data()))
            .build();

        assertThrows(RevertException.class,
            () -> client.sendTransactionAndWait(transferTx, 30_000, 500),
            "Transfer should have reverted after cancel");
    }
}
