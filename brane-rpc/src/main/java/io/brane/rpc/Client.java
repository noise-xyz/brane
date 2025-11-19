package io.brane.rpc;

import io.brane.core.RpcException;

public interface Client {
    <T> T call(String method, Class<T> responseType, Object... params) throws RpcException;
}
