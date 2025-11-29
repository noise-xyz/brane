package io.brane.core.tx;

import io.brane.core.crypto.Signature;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.primitives.rlp.Rlp;
import io.brane.primitives.rlp.RlpItem;
import io.brane.primitives.rlp.RlpNumeric;
import io.brane.primitives.rlp.RlpString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * EIP-155 legacy transaction with gasPrice.
 * 
 * <p>
 * This transaction type uses the traditional gas pricing model with a single
 * {@code gasPrice} field. The signature includes chain ID protection per
 * EIP-155.
 * 
 * <h2>EIP-155 Encoding</h2>
 * <p>
 * Signing preimage:
 * {@code RLP([nonce, gasPrice, gasLimit, to, value, data, chainId, 0, 0])}
 * <br>
 * Signed envelope:
 * {@code RLP([nonce, gasPrice, gasLimit, to, value, data, v, r, s])}
 * <br>
 * where {@code v = chainId * 2 + 35 + yParity}
 * 
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * LegacyTransaction tx = new LegacyTransaction(
 *         0L, // nonce
 *         Wei.gwei(20), // gasPrice
 *         21000L, // gasLimit
 *         Address.fromHex("0x..."), // to
 *         Wei.ether("0.1"), // value
 *         HexData.EMPTY // data
 * );
 * 
 * byte[] preimage = tx.encodeForSigning(1); // chainId = 1 (mainnet)
 * Signature sig = privateKey.sign(Keccak256.hash(preimage));
 * byte[] envelope = tx.encodeAsEnvelope(sig);
 * }</pre>
 * 
 * @param nonce    the transaction nonce
 * @param gasPrice the gas price in wei
 * @param gasLimit the maximum gas to use
 * @param to       the recipient address (null for contract creation)
 * @param value    the amount of ether to send
 * @param data     the transaction data (calldata or contract bytecode)
 * @since 0.2.0
 */
public record LegacyTransaction(
        long nonce,
        Wei gasPrice,
        long gasLimit,
        Address to,
        Wei value,
        HexData data) implements UnsignedTransaction {

    public LegacyTransaction {
        if (nonce < 0) {
            throw new IllegalArgumentException("Nonce cannot be negative");
        }
        Objects.requireNonNull(gasPrice, "gasPrice cannot be null");
        if (gasLimit <= 0) {
            throw new IllegalArgumentException("gasLimit must be positive");
        }
        // to can be null for contract creation
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
    }

    @Override
    public byte[] encodeForSigning(final long chainId) {
        final List<RlpItem> items = new ArrayList<>(9);

        items.add(RlpNumeric.encodeLongUnsignedItem(nonce));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(gasPrice.value()));
        items.add(RlpNumeric.encodeLongUnsignedItem(gasLimit));
        items.add(new RlpString(to != null ? to.toBytes() : new byte[0]));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(value.value()));
        items.add(new RlpString(data.toBytes()));

        // EIP-155: append chainId, 0, 0 for signing
        items.add(RlpNumeric.encodeLongUnsignedItem(chainId));
        items.add(RlpNumeric.encodeLongUnsignedItem(0));
        items.add(RlpNumeric.encodeLongUnsignedItem(0));

        return Rlp.encodeList(items);
    }

    @Override
    public byte[] encodeAsEnvelope(final Signature signature) {
        final List<RlpItem> items = new ArrayList<>(9);

        items.add(RlpNumeric.encodeLongUnsignedItem(nonce));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(gasPrice.value()));
        items.add(RlpNumeric.encodeLongUnsignedItem(gasLimit));
        items.add(new RlpString(to != null ? to.toBytes() : new byte[0]));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(value.value()));
        items.add(new RlpString(data.toBytes()));

        // Add signature components
        // v is already EIP-155 encoded (chainId * 2 + 35 + yParity)
        items.add(RlpNumeric.encodeLongUnsignedItem(signature.v()));
        items.add(new RlpString(signature.r()));
        items.add(new RlpString(signature.s()));

        return Rlp.encodeList(items);
    }
}
