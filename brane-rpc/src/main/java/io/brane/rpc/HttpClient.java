package io.brane.rpc;

import static io.brane.rpc.internal.RpcUtils.MAPPER;
import io.brane.core.error.RpcException;
import java.math.BigInteger;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

public final class HttpClient implements Client {

    private final BraneProvider provider;

    public HttpClient(final URI endpoint) {
        this(BraneProvider.http(endpoint.toString()));
    }

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
