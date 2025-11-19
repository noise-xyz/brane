package io.brane.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.brane.core.RpcException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public final class HttpClient implements Client {

    private final URI endpoint;
    private final java.net.http.HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpClient(final URI endpoint) {
        this.endpoint = endpoint;
        this.http = java.net.http.HttpClient.newHttpClient();
    }

    @Override
    public <T> T call(final String method, final Class<T> responseType, final Object... params)
            throws RpcException {
        try {
            final Map<String, Object> body =
                    Map.of(
                            "jsonrpc", "2.0",
                            "id", 1,
                            "method", method,
                            "params", params == null ? new Object[0] : params);

            final String json = mapper.writeValueAsString(body);

            final HttpRequest req =
                    HttpRequest.newBuilder(endpoint)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

            final HttpResponse<String> resp =
                    http.send(req, HttpResponse.BodyHandlers.ofString());

            final Map<?, ?> map = mapper.readValue(resp.body(), Map.class);

            if (map.containsKey("error")) {
                final Map<?, ?> err = (Map<?, ?>) map.get("error");
                final int code = ((Number) err.get("code")).intValue();
                final String message = (String) err.get("message");
                final Object dataValue = err.get("data");
                final String data = dataValue != null ? dataValue.toString() : null;
                throw new RpcException(code, message, data, null);
            }

            final Object result = map.get("result");
            final String resultJson = mapper.writeValueAsString(result);
            return mapper.readValue(resultJson, responseType);

        } catch (RpcException e) {
            throw e;
        } catch (Exception e) {
            throw new RpcException(-1, "RPC serialization error", null, e);
        }
    }
}
