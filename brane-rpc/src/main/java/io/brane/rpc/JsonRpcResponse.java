package io.brane.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcResponse(String jsonrpc, Object result, JsonRpcError error, String id) {

    public boolean hasError() {
        return error != null;
    }
}
