// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.model;

import java.util.Objects;

import io.brane.core.types.Address;

/**
 * Represents a token in an asset change trace.
 * <p>
 * Used in {@link AssetChange} when {@code traceAssetChanges=true}.
 * <p>
 * The {@code decimals} and {@code symbol} fields are populated only when
 * {@code fetchTokenMetadata=true} in the simulation request. If metadata
 * fetching is disabled or fails, these fields will be {@code null}.
 * <p>
 * For native ETH, the address is {@code 0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee}.
 *
 * @param address the token contract address (or 0xeee...eee for ETH)
 * @param decimals the token decimals (null if not fetched or fetch failed)
 * @param symbol the token symbol (null if not fetched or fetch failed)
 * @since 0.1.0-alpha
 */
public record AssetToken(
        Address address,
        Integer decimals,
        String symbol
) {

    /**
     * Compact constructor with validation.
     */
    public AssetToken {
        Objects.requireNonNull(address, "address cannot be null");
    }
}
