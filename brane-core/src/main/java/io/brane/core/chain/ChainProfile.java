package io.brane.core.chain;
 
 public record ChainProfile(long chainId, String defaultRpcUrl, boolean supportsEip1559) {
 
     public static ChainProfile of(final long chainId, final String defaultRpcUrl, final boolean supportsEip1559) {
         return new ChainProfile(chainId, defaultRpcUrl, supportsEip1559);
     }
 }
