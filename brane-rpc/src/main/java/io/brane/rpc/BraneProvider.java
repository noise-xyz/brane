package io.brane.rpc;

import io.brane.core.error.RpcException;
import java.util.List;

public interface BraneProvider {

    JsonRpcResponse send(String method, List<?> params) throws RpcException;

    static BraneProvider http(final String url) {
        return HttpBraneProvider.builder(url).build();
    }
}
