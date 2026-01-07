package io.brane.examples;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.brane.contract.BraneContract;
import io.brane.core.abi.Abi;
import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.AbiEncodingException;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.rpc.Brane;
import io.brane.rpc.CallRequest;

/**
 * ERC-20 read-only demo using the new Brane.connect() API.
 *
 * <p>Demonstrates two paths:
 * <ol>
 *   <li>Brane.connect() + Abi.FunctionCall + CallRequest (raw eth_call)</li>
 *   <li>Brane.connect() + BraneContract.bindReadOnly (type-safe interface)</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *   -PmainClass=io.brane.examples.Erc20Example \
 *   -Dbrane.examples.erc20.rpc=http://127.0.0.1:8545 \
 *   -Dbrane.examples.erc20.contract=0x... \
 *   -Dbrane.examples.erc20.holder=0x...
 * </pre>
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

    public static void main(final String[] args) throws Exception {
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

        runWithBraneCall(rpcUrl, token, holder, abi);
        runWithBraneContract(rpcUrl, token, holder);
    }

    /**
     * Path 1: Brane.connect() + Abi.FunctionCall + CallRequest.
     */
    private static void runWithBraneCall(
            final String rpcUrl, final Address token, final Address holder, final Abi abi)
            throws Exception {

        Brane client = Brane.connect(rpcUrl);
        try {
            final Abi.FunctionCall decimalsCall = abi.encodeFunction("decimals");
            final Abi.FunctionCall balanceCall = abi.encodeFunction("balanceOf", holder);

            // Use type-safe CallRequest instead of raw Map
            final CallRequest decimalsRequest = CallRequest.of(token, new HexData(decimalsCall.data()));
            final CallRequest balanceRequest = CallRequest.of(token, new HexData(balanceCall.data()));

            final HexData decimalsRaw = client.call(decimalsRequest);
            final HexData balanceRaw = client.call(balanceRequest);

            final BigInteger decimals = decimalsCall.decode(decimalsRaw.value(), BigInteger.class);
            final BigInteger balance = balanceCall.decode(balanceRaw.value(), BigInteger.class);
            final BigDecimal human =
                    new BigDecimal(balance).divide(BigDecimal.TEN.pow(decimals.intValue()));

            System.out.println("[Brane.call] decimals  = " + decimals);
            System.out.println("[Brane.call] balanceOf = " + balance + " (raw), " + human);
        } catch (final RpcException e) {
            System.err.println("[Brane.call] RPC error: " + e.getMessage());
        } catch (final AbiEncodingException | AbiDecodingException e) {
            System.err.println("[Brane.call] ABI error: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * Path 2: Brane.connect() + BraneContract.bindReadOnly (type-safe interface).
     */
    private static void runWithBraneContract(
            final String rpcUrl, final Address token, final Address holder)
            throws Exception {

        // Define a read-only interface for the ERC-20 contract
        interface Erc20ReadOnly {
            BigInteger decimals();
            BigInteger balanceOf(Address account);
        }

        Brane client = Brane.connect(rpcUrl);
        try {
            // Bind using the new Brane API
            final Erc20ReadOnly contract = BraneContract.bindReadOnly(
                    token, ERC20_ABI, client, Erc20ReadOnly.class);

            final BigInteger decimals = contract.decimals();
            final BigInteger balance = contract.balanceOf(holder);
            final BigDecimal human =
                    new BigDecimal(balance).divide(BigDecimal.TEN.pow(decimals.intValue()));

            System.out.println("[BraneContract] decimals  = " + decimals);
            System.out.println("[BraneContract] balanceOf = " + balance + " (raw), " + human);
        } catch (final RevertException e) {
            System.err.println("[BraneContract] Revert: " + e.revertReason());
        } catch (final RpcException e) {
            System.err.println("[BraneContract] RPC error: " + e.getMessage());
        } catch (final AbiEncodingException | AbiDecodingException e) {
            System.err.println("[BraneContract] ABI error: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
