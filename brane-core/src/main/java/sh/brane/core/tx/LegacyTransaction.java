// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.tx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import sh.brane.core.crypto.Signature;
import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;
import sh.brane.core.types.Wei;
import sh.brane.primitives.rlp.Rlp;
import sh.brane.primitives.rlp.RlpItem;
import sh.brane.primitives.rlp.RlpNumeric;
import sh.brane.primitives.rlp.RlpString;

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

    /**
     * Encodes the signed transaction as a network-ready envelope.
     *
     * <p>
     * The signature's {@code v} value must be EIP-155 encoded:
     * <pre>
     * v = chainId * 2 + 35 + yParity
     * </pre>
     * where {@code yParity} is 0 or 1 (the recovery ID from signing).
     *
     * <p>
     * <b>Important:</b> The caller is responsible for computing the correct v value.
     * The raw signing operation returns yParity (0 or 1), which must be transformed:
     * <pre>{@code
     * Signature rawSig = privateKey.sign(messageHash);
     * int eip155V = (int) (chainId * 2 + 35 + rawSig.v());
     * Signature eip155Sig = new Signature(rawSig.r(), rawSig.s(), eip155V);
     * byte[] envelope = tx.encodeAsEnvelope(eip155Sig);
     * }</pre>
     *
     * @param signature the signature with EIP-155 encoded v value (must be &ge; 35)
     * @return bytes ready for eth_sendRawTransaction
     * @throws NullPointerException     if signature is null
     * @throws IllegalArgumentException if signature.v() &lt; 35 (not EIP-155 encoded)
     */
    @Override
    public byte[] encodeAsEnvelope(final Signature signature) {
        Objects.requireNonNull(signature, "signature is required");

        // EIP-155: v = chainId * 2 + 35 + yParity, so minimum v is 35 (chainId=0, yParity=0)
        // In practice chainId is always >= 1, so v should be >= 37
        if (signature.v() < 35) {
            throw new IllegalArgumentException(
                    "Legacy transaction signature v must be EIP-155 encoded (>= 35), got: " + signature.v()
                            + ". Did you forget to encode: v = chainId * 2 + 35 + yParity?");
        }

        final List<RlpItem> items = new ArrayList<>(9);

        items.add(RlpNumeric.encodeLongUnsignedItem(nonce));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(gasPrice.value()));
        items.add(RlpNumeric.encodeLongUnsignedItem(gasLimit));
        items.add(new RlpString(to != null ? to.toBytes() : new byte[0]));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(value.value()));
        items.add(new RlpString(data.toBytes()));

        // Add signature components (v is already EIP-155 encoded)
        items.add(RlpNumeric.encodeLongUnsignedItem(signature.v()));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(signature.rAsBigInteger()));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(signature.sAsBigInteger()));

        return Rlp.encodeList(items);
    }
}
