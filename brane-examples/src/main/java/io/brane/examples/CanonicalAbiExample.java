package io.brane.examples;

import java.math.BigInteger;

import io.brane.contract.BraneContract;
import io.brane.core.AnsiColors;
import io.brane.core.BraneDebug;
import io.brane.core.abi.Abi;
import io.brane.core.builder.TxBuilder;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.Brane;
import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.AbiEncodingException;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;

/**
 * Canonical example of Brane's "Minimal but Solid" ABI Wrapper.
 *
 * <p>
 * This example demonstrates:
 * 1. Type-safe binding of Java interfaces to Smart Contracts
 * 2. Support for simple scalar types (Address, BigInteger, bool)
 * 3. Automatic routing of view vs. write functions
 * 4. Explicit failure on unsupported features (e.g., overloads)
 */
public final class CanonicalAbiExample {

    // Minimal ERC20 ABI (Functions only, no events/structs)
    private static final String ERC20_ABI = """
            [
              {
                "type": "constructor",
                "inputs": [{"name": "initialSupply", "type": "uint256"}]
              },
              {
                "name": "balanceOf",
                "type": "function",
                "stateMutability": "view",
                "inputs": [{"name": "account", "type": "address"}],
                "outputs": [{"name": "", "type": "uint256"}]
              },
              {
                "name": "transfer",
                "type": "function",
                "stateMutability": "nonpayable",
                "inputs": [
                  {"name": "to", "type": "address"},
                  {"name": "value", "type": "uint256"}
                ],
                "outputs": [{"name": "", "type": "bool"}]
              }
            ]
            """;

    private static final String ERC20_BYTECODE = "0x60806040526040518060400160405280600b81526020017f4272616e6520546f6b656e0000000000000000000000000000000000000000008152505f908161004791906103bf565b506040518060400160405280600581526020017f4252414e450000000000000000000000000000000000000000000000000000008152506001908161008c91906103bf565b50601260025f6101000a81548160ff021916908360ff1602179055503480156100b3575f5ffd5b50604051610cdb380380610cdb83398181016040528101906100d591906104bc565b8060035f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20819055503373ffffffffffffffffffffffffffffffffffffffff165f73ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef8360405161017491906104f6565b60405180910390a35061050f565b5f81519050919050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52604160045260245ffd5b7f4e487b71000000000000000000000000000000000000000000000000000000005f52602260045260245ffd5b5f60028204905060018216806101fd57607f821691505b6020821081036102105761020f6101b9565b5b50919050565b5f819050815f5260205f209050919050565b5f6020601f8301049050919050565b5f82821b905092915050565b5f600883026102727fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff82610237565b61027c8683610237565b95508019841693508086168417925050509392505050565b5f819050919050565b5f819050919050565b5f6102c06102bb6102b684610294565b61029d565b610294565b9050919050565b5f819050919050565b6102d9836102a6565b6102ed6102e5826102c7565b848454610243565b825550505050565b5f5f905090565b6103046102f5565b61030f8184846102d0565b505050565b5b81811015610332576103275f826102fc565b600181019050610315565b5050565b601f8211156103775761034881610216565b61035184610228565b81016020851015610360578190505b61037461036c85610228565b830182610314565b50505b505050565b5f82821c905092915050565b5f6103975f198460080261037c565b1980831691505092915050565b5f6103af8383610388565b9150826002028217905092915050565b6103c882610182565b67ffffffffffffffff8111156103e1576103e061018c565b5b6103eb82546101e6565b6103f6828285610336565b5f60209050601f831160018114610427575f8415610415578287015190505b61041f85826103a4565b865550610486565b601f19841661043586610216565b5f5b8281101561045c57848901518255600182019150602085019450602081019050610437565b868310156104795784890151610475601f891682610388565b8355505b6001600288020188555050505b505050505050565b5f5ffd5b61049b81610294565b81146104a5575f5ffd5b50565b5f815190506104b681610492565b92915050565b5f602082840312156104d1576104d061048e565b5b5f6104de848285016104a8565b91505092915050565b6104f081610294565b82525050565b5f6020820190506105095f8301846104e7565b92915050565b6107bf8061051c5f395ff3fe608060405234801561000f575f5ffd5b5060043610610055575f3560e01c806306fdde0314610059578063313ce5671461007757806370a082311461009557806395d89b41146100c5578063a9059cbb146100e3575b5f5ffd5b610061610113565b60405161006e9190610488565b60405180910390f35b61007f61019e565b60405161008c91906104c3565b60405180910390f35b6100af60048036038101906100aa919061053a565b6101b0565b6040516100bc919061057d565b60405180910390f35b6100cd6101f6565b6040516100da9190610488565b60405180910390f35b6100fd60048036038101906100f891906105c0565b610282565b60405161010a9190610618565b60405180910390f35b5f805461011f9061065e565b80601f016020809104026020016040519081016040528092919081815260200182805461014b9061065e565b80156101965780601f1061016d57610100808354040283529160200191610196565b820191905f5260205f20905b81548152906001019060200180831161017957829003601f168201915b505050505081565b60025f9054906101000a900460ff1681565b5f60035f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20549050919050565b600180546102039061065e565b80601f016020809104026020016040519081016040528092919081815260200182805461022f9061065e565b801561027a5780601f106102515761010080835404028352916020019161027a565b820191905f5260205f20905b81548152906001019060200180831161025d57829003601f168201915b505050505081565b5f8160035f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20541015610303576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016102fa906106d8565b60405180910390fd5b8160035f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f82825461034f9190610723565b925050819055508160035f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8282546103a29190610756565b925050819055508273ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef84604051610406919061057d565b60405180910390a36001905092915050565b5f81519050919050565b5f82825260208201905092915050565b8281835e5f83830152505050565b5f601f19601f8301169050919050565b5f61045a82610418565b6104648185610422565b9350610474818560208601610432565b61047d81610440565b840191505092915050565b5f6020820190508181035f8301526104a08184610450565b905092915050565b5f60ff82169050919050565b6104bd816104a8565b82525050565b5f6020820190506104d65f8301846104b4565b92915050565b5f5ffd5b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f610509826104e0565b9050919050565b610519816104ff565b8114610523575f5ffd5b50565b5f8135905061053481610510565b92915050565b5f6020828403121561054f5761054e6104dc565b5b5f61055c84828501610526565b91505092915050565b5f819050919050565b61057781610565565b82525050565b5f6020820190506105905f83018461056e565b92915050565b61059f81610565565b81146105a9575f5ffd5b50565b5f813590506105ba81610596565b92915050565b5f5f604083850312156105d6576105d56104dc565b5b5f6105e385828601610526565b92505060206105f4858286016105ac565b9150509250929050565b5f8115159050919050565b610612816105fe565b82525050565b5f60208201905061062b5f830184610609565b92915050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52602260045260245ffd5b5f600282049050600182168061067557607f821691505b60208210810361068857610687610631565b5b50919050565b7f696e73756666696369656e742062616c616e63650000000000000000000000005f82015250565b5f6106c2601483610422565b91506106cd8261068e565b602082019050919050565b5f6020820190508181035f8301526106ef816106b6565b9050919050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52601160045260245ffd5b5f61072d82610565565b915061073883610565565b92508282039050818111156107505761074f6106f6565b5b92915050565b5f61076082610565565b915061076b83610565565b9250828201905080821115610783576107826106f6565b5b9291505056fea2646970667358221220ffab9c5a0e00b481a31fc0b0e61c0be5518d5e10ddcd1fc144850017a6c8e73a64736f6c634300081e0033";

    // 1. Define Java interface (Must match ABI exactly)
    public interface Erc20 {
        // View function: uint256 -> BigInteger
        BigInteger balanceOf(Address account);

        // Write function: returns TransactionReceipt for confirmation
        TransactionReceipt transfer(Address to, BigInteger amount);
    }

    // Invalid Interface (Demonstrates explicit failure)
    public interface InvalidErc20 {
        // Overloads are NOT supported
        BigInteger balanceOf(Address account);

        BigInteger balanceOf(String account);
    }

    public static void main(final String[] args) {
        // Enable granular logging for clean output
        BraneDebug.setTxLogging(true);
        BraneDebug.setRpcLogging(false);

        System.out.println("=== Canonical ABI Wrapper Example ===\n");

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        // Default Anvil key (Account #0)
        final String privateKey = System.getProperty("brane.examples.pk",
                "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
        String contractAddr = System.getProperty("brane.examples.contract");

        try {
            // Setup Client
            final var signer = new io.brane.core.crypto.PrivateKeySigner(privateKey);
            final Brane.Signer client = Brane.connect(rpcUrl, signer);

            // Deploy if needed
            if (contractAddr == null || contractAddr.isBlank()
                    || "0x0000000000000000000000000000000000000000".equals(contractAddr)) {
                System.out.println("[0] Deploying ERC-20 Contract...");
                final BigInteger initialSupply = BigInteger.valueOf(1_000_000);
                contractAddr = deployErc20(client, initialSupply);
                System.out.println("    " + AnsiColors.success("Deployed at: " + contractAddr));
            }

            // ---------------------------------------------------------
            // Feature 1: Type-Safe Binding
            // ---------------------------------------------------------
            System.out.println("\n[1] Binding Interface...");
            final Address tokenAddress = new Address(contractAddr);
            final Erc20 token = BraneContract.bind(
                    tokenAddress,
                    ERC20_ABI,
                    client,
                    Erc20.class);
            System.out.println("    Bound " + Erc20.class.getSimpleName() + " to " + tokenAddress.value());

            // ---------------------------------------------------------
            // Feature 2: View Functions (Read)
            // ---------------------------------------------------------
            System.out.println("\n[2] Calling View Function (balanceOf)...");
            final Address owner = signer.address();
            final BigInteger balance = token.balanceOf(owner);
            System.out.println("    Owner: " + owner.value());
            System.out.println("    Balance: " + balance);

            // ---------------------------------------------------------
            // Feature 3: Write Functions (Transaction)
            // ---------------------------------------------------------
            System.out.println("\n[3] Calling Write Function (transfer)...");
            final Address recipient = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");
            final BigInteger amount = BigInteger.valueOf(500);

            System.out.println("    Transferring " + amount + " tokens...");
            final TransactionReceipt receipt = token.transfer(recipient, amount);

            System.out.println("    " + AnsiColors.success("Tx Hash: " + receipt.transactionHash().value()));
            System.out.println("    " + AnsiColors.success("Status: " + (receipt.status() ? "SUCCESS" : "FAILED")));

            // ---------------------------------------------------------
            // Feature 4: Explicit Failure (Validation)
            // ---------------------------------------------------------
            System.out.println("\n[4] Demonstrating Explicit Failure (Overloads)...");
            try {
                BraneContract.bind(
                        tokenAddress,
                        ERC20_ABI,
                        client,
                        InvalidErc20.class);
            } catch (IllegalArgumentException e) {
                System.out.println("    " + AnsiColors.success("Caught expected error: " + e.getMessage()));
            }

            System.out.println("\n✅ Canonical ABI Example Completed Successfully.");

        } catch (final RpcException e) {
            System.err.println("❌ RPC error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (final RevertException e) {
            System.err.println("❌ Revert error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (final AbiEncodingException | AbiDecodingException e) {
            System.err.println("❌ ABI error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String deployErc20(final Brane.Signer client, final BigInteger initialSupply) {
        // Use Abi helper to encode constructor arguments
        final Abi abi = Abi.fromJson(ERC20_ABI);
        final HexData encodedArgs = abi.encodeConstructor(initialSupply);
        final String data = ERC20_BYTECODE + encodedArgs.value().substring(2); // remove 0x prefix

        final TransactionRequest request = TxBuilder.eip1559()
                .data(new HexData(data))
                .value(Wei.of(0))
                .build();

        // Wait for deployment
        final TransactionReceipt receipt = client.sendTransactionAndWait(request, 20_000, 500);

        if (receipt.contractAddress().value().isEmpty()) {
            throw new RuntimeException("Deployment failed: No contract address returned");
        }
        return receipt.contractAddress().value();
    }
}
