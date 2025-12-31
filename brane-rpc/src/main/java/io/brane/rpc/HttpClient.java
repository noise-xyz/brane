package io.brane.rpc;

import static io.brane.rpc.internal.RpcUtils.MAPPER;

import io.brane.core.error.RpcException;
import java.math.BigInteger;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * A typed JSON-RPC client that wraps a {@link BraneProvider} for convenient RPC calls.
 *
 * <p>This class provides a simplified API for making JSON-RPC calls where the response
 * is automatically deserialized to the requested type. It handles common type conversions
 * including hex string to BigInteger conversion.
 *
 * <h2>Example usage:</h2>
 * <pre>{@code
 * // Using URI directly
 * HttpClient client = new HttpClient(URI.create("http://localhost:8545"));
 * BigInteger blockNumber = client.call("eth_blockNumber", BigInteger.class);
 *
 * // Using existing provider
 * BraneProvider provider = HttpBraneProvider.create("http://localhost:8545");
 * HttpClient client = new HttpClient(provider);
 * String chainId = client.call("eth_chainId", String.class);
 * }</pre>
 *
 * <h2>Type Conversion:</h2>
 * <ul>
 *   <li>{@code String.class} - Returns the raw result as a string</li>
 *   <li>{@code BigInteger.class} - Parses hex (0x-prefixed) or decimal strings</li>
 *   <li>Other types - Uses Jackson ObjectMapper for conversion</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe if the underlying {@link BraneProvider}
 * is thread-safe. The standard providers ({@link HttpBraneProvider}, {@link WebSocketProvider})
 * are thread-safe.
 *
 * <p><b>Note:</b> For most Ethereum operations, prefer using {@link PublicClient} or
 * {@link WalletClient} which provide type-safe, documented methods for standard operations.
 * Use this class when you need direct access to custom or non-standard RPC methods.
 *
 * @see Client
 * @see BraneProvider
 * @see PublicClient
 */
public final class HttpClient implements Client {

    private final BraneProvider provider;

    /**
     * Creates a new client connected to the specified endpoint.
     *
     * <p>This constructor creates an internal {@link HttpBraneProvider} for the endpoint.
     *
     * @param endpoint the JSON-RPC endpoint URI
     */
    public HttpClient(final URI endpoint) {
        this(BraneProvider.http(endpoint.toString()));
    }

    /**
     * Creates a new client using the specified provider.
     *
     * <p>The provider is used for all RPC calls. This allows sharing a provider
     * across multiple clients or using custom provider implementations.
     *
     * @param provider the RPC provider to use for calls
     */
    public HttpClient(final BraneProvider provider) {
        this.provider = provider;
    }

    @Override
    public <T> T call(final String method, final Class<T> responseType, final Object... params)
            throws RpcException {
        final List<?> safeParams =
                (params == null || params.length == 0) ? List.of() : Arrays.asList(params);
        final JsonRpcResponse response = provider.send(method, safeParams);
        final Object result = response.result();
        if (result == null) {
            return null;
        }
        try {
            return mapResult(result, responseType);
        } catch (IllegalArgumentException e) {
            throw new RpcException(-32700, "Unable to map RPC result", null, e);
        }
    }

    private <T> T mapResult(final Object result, final Class<T> responseType) {
        if (responseType == String.class) {
            return responseType.cast(result.toString());
        }
        if (responseType == BigInteger.class) {
            return responseType.cast(convertToBigInteger(result));
        }
        return MAPPER.convertValue(result, responseType);
    }

    private BigInteger convertToBigInteger(final Object value) {
        return switch (value) {
            case BigInteger bigInteger -> bigInteger;
            case Number number -> BigInteger.valueOf(number.longValue());
            case String s when s.startsWith("0x") || s.startsWith("0X") ->
                    new BigInteger(s.substring(2), 16);
            case String s -> new BigInteger(s, 10);
            case null -> throw new IllegalArgumentException("Cannot convert value to BigInteger: null");
            default -> throw new IllegalArgumentException(
                    "Cannot convert value to BigInteger: " + value);
        };
    }
}
