package io.brane.examples;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import io.brane.contract.ReadOnlyContract;
import io.brane.core.abi.Abi;
import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.AbiEncodingException;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;

/**
 * ERC-20 read-only demo using two paths:
 *
 *  1) PublicClient + Abi.FunctionCall (raw eth_call)
 *  2) ReadOnlyContract + PublicClient (read-only facade)
 *
 * Usage:
 *
 *   ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=io.brane.examples.Erc20Example \
 *     -Dbrane.examples.erc20.rpc=http://127.0.0.1:8545 \
 *     -Dbrane.examples.erc20.contract=0x... \
 *     -Dbrane.examples.erc20.holder=0x...
 */
public final class Erc20Example {

    private static final String ERC20_ABI =
            """
            [
              {
                "inputs": [],
                "name": "decimals",
                "outputs": [{ "internalType": "uint8", "name": "", "type": "uint8" }],
                "stateMutability": "view",
                "type": "function"
              },
              {
                "inputs": [{ "internalType": "address", "name": "account", "type": "address" }],
                "name": "balanceOf",
                "outputs": [{ "internalType": "uint256", "name": "", "type": "uint256" }],
                "stateMutability": "view",
                "type": "function"
              }
            ]
            """;

    private Erc20Example() {}

    public static void main(final String[] args) {
        final String rpcUrl = System.getProperty("brane.examples.erc20.rpc");
        final String tokenAddress = System.getProperty("brane.examples.erc20.contract");
        final String holderAddress = System.getProperty("brane.examples.erc20.holder");

        if (isBlank(rpcUrl) || isBlank(tokenAddress) || isBlank(holderAddress)) {
            System.out.println(
                    """
                    Please set the following system properties:
                      -Dbrane.examples.erc20.rpc=<RPC URL>
                      -Dbrane.examples.erc20.contract=<ERC-20 contract address>
                      -Dbrane.examples.erc20.holder=<holder address>
                    """);
            return;
        }

        final Address token = new Address(tokenAddress);
        final Address holder = new Address(holderAddress);
        final Abi abi = Abi.fromJson(ERC20_ABI);

        runWithPublicClient(rpcUrl, token, holder, abi);
        runWithReadOnlyContract(rpcUrl, token, holder, abi);
    }

    /**
     * Path 1: PublicClient + Abi.FunctionCall + raw eth_call.
     */
    private static void runWithPublicClient(
            final String rpcUrl, final Address token, final Address holder, final Abi abi) {

        try {
            final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
            final PublicClient publicClient = PublicClient.from(provider);

            final Abi.FunctionCall decimalsCall = abi.encodeFunction("decimals");
            final Abi.FunctionCall balanceCall = abi.encodeFunction("balanceOf", holder);

            final Map<String, Object> decimalsCallObj = new LinkedHashMap<>();
            decimalsCallObj.put("to", token.value());
            decimalsCallObj.put("data", decimalsCall.data());

            final Map<String, Object> balanceCallObj = new LinkedHashMap<>();
            balanceCallObj.put("to", token.value());
            balanceCallObj.put("data", balanceCall.data());

            final String decimalsRaw = publicClient.call(decimalsCallObj, "latest");
            final String balanceRaw = publicClient.call(balanceCallObj, "latest");

            final BigInteger decimals = decimalsCall.decode(decimalsRaw, BigInteger.class);
            final BigInteger balance = balanceCall.decode(balanceRaw, BigInteger.class);
            final BigDecimal human =
                    new BigDecimal(balance).divide(BigDecimal.TEN.pow(decimals.intValue()));

            System.out.println("[PublicClient] decimals  = " + decimals);
            System.out.println("[PublicClient] balanceOf = " + balance + " (raw), " + human);
        } catch (final RpcException e) {
            System.err.println("[PublicClient] RPC error: " + e.getMessage());
        } catch (final AbiEncodingException | AbiDecodingException e) {
            System.err.println("[PublicClient] ABI error: " + e.getMessage());
        }
    }

    /**
     * Path 2: ReadOnlyContract facade over PublicClient.
     */
    private static void runWithReadOnlyContract(
            final String rpcUrl, final Address token, final Address holder, final Abi abi) {

        try {
            final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
            final PublicClient publicClient = PublicClient.from(provider);

            final ReadOnlyContract contract = ReadOnlyContract.from(token, abi, publicClient);

            final BigInteger decimals = contract.call("decimals", BigInteger.class);
            final BigInteger balance = contract.call("balanceOf", BigInteger.class, holder);
            final BigDecimal human =
                    new BigDecimal(balance).divide(BigDecimal.TEN.pow(decimals.intValue()));

            System.out.println("[ReadOnlyContract] decimals  = " + decimals);
            System.out.println("[ReadOnlyContract] balanceOf = " + balance + " (raw), " + human);
        } catch (final RevertException e) {
            System.err.println("[ReadOnlyContract] Revert: " + e.revertReason());
        } catch (final RpcException e) {
            System.err.println("[ReadOnlyContract] RPC error: " + e.getMessage());
        } catch (final AbiEncodingException | AbiDecodingException e) {
            System.err.println("[ReadOnlyContract] ABI error: " + e.getMessage());
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }
}
