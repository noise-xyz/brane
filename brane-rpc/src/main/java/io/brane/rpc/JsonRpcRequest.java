package io.brane.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcRequest(String jsonrpc, String method, List<?> params, String id) {}
