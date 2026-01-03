package io.brane.core.tx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.brane.core.crypto.Signature;
import io.brane.core.model.AccessListEntry;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.primitives.Hex;
import io.brane.primitives.rlp.Rlp;
import io.brane.primitives.rlp.RlpItem;
import io.brane.primitives.rlp.RlpList;
import io.brane.primitives.rlp.RlpNumeric;
import io.brane.primitives.rlp.RlpString;

/**
 * EIP-1559 transaction with dynamic fee market.
 *
 * <p>
 * This transaction type uses the EIP-1559 fee market with separate
 * {@code maxFeePerGas} and {@code maxPriorityFeePerGas} fields. It is encoded
 * using EIP-2718 typed transaction envelopes with type byte {@code 0x02}.
 *
 * <h2>EIP-2718/1559 Encoding</h2>
 * <p>
 * Signing preimage:
 *
 * <pre>
 * 0x02 || RLP([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList])
 * </pre>
 *
 * <p>
 * Signed envelope:
 *
 * <pre>
 * 0x02 || RLP([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList, yParity, r, s])
 * </pre>
 *
 * <p>
 * Note: The {@code v} value in the signature is just the yParity (0 or 1), not
 * EIP-155 encoded.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Eip1559Transaction tx = new Eip1559Transaction(
 *         1L, // chainId
 *         0L, // nonce
 *         Wei.gwei(2), // maxPriorityFeePerGas
 *         Wei.gwei(100), // maxFeePerGas
 *         21000L, // gasLimit
 *         Address.fromHex("0x..."), // to
 *         Wei.ether("0.1"), // value
 *         HexData.EMPTY, // data
 *         List.of() // accessList
 * );
 *
 * byte[] preimage = tx.encodeForSigning(1);
 * Signature sig = privateKey.sign(Keccak256.hash(preimage));
 * byte[] envelope = tx.encodeAsEnvelope(sig);
 * }</pre>
 *
 * @param chainId              the chain ID
 * @param nonce                the transaction nonce
 * @param maxPriorityFeePerGas the maximum priority fee (miner tip)
 * @param maxFeePerGas         the maximum total fee per gas
 * @param gasLimit             the maximum gas to use
 * @param to                   the recipient address (null for contract
 *                             creation)
 * @param value                the amount of ether to send
 * @param data                 the transaction data (calldata or contract
 *                             bytecode)
 * @param accessList           the EIP-2930 access list
 * @since 0.2.0
 */
public record Eip1559Transaction(
        long chainId,
        long nonce,
        Wei maxPriorityFeePerGas,
        Wei maxFeePerGas,
        long gasLimit,
        Address to,
        Wei value,
        HexData data,
        List<AccessListEntry> accessList) implements UnsignedTransaction {

    private static final byte EIP1559_TYPE = (byte) 0x02;

    public Eip1559Transaction {
        if (chainId <= 0) {
            throw new IllegalArgumentException("Chain ID must be positive");
        }
        if (nonce < 0) {
            throw new IllegalArgumentException("Nonce cannot be negative");
        }
        Objects.requireNonNull(maxPriorityFeePerGas, "maxPriorityFeePerGas cannot be null");
        Objects.requireNonNull(maxFeePerGas, "maxFeePerGas cannot be null");
        if (gasLimit <= 0) {
            throw new IllegalArgumentException("gasLimit must be positive");
        }
        // to can be null for contract creation
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(data, "data cannot be null");

        // Make defensive copy of access list
        accessList = accessList != null ? List.copyOf(accessList) : List.of();
    }

    @Override
    public byte[] encodeForSigning(final long chainId) {
        if (chainId != this.chainId) {
            throw new IllegalArgumentException(
                    "chainId parameter (" + chainId + ") must match transaction chainId (" + this.chainId + ")");
        }
        final byte[] rlpPayload = encodePayload(false, null);

        // Prepend type byte
        final byte[] result = new byte[rlpPayload.length + 1];
        result[0] = EIP1559_TYPE;
        System.arraycopy(rlpPayload, 0, result, 1, rlpPayload.length);

        return result;
    }

    @Override
    public byte[] encodeAsEnvelope(final Signature signature) {
        final byte[] rlpPayload = encodePayload(true, signature);

        // Prepend type byte
        final byte[] result = new byte[rlpPayload.length + 1];
        result[0] = EIP1559_TYPE;
        System.arraycopy(rlpPayload, 0, result, 1, rlpPayload.length);

        return result;
    }

    /**
     * Encodes the RLP payload (without type byte prefix).
     *
     * @param includeSig whether to include signature fields
     * @param signature  the signature (required if includeSig is true)
     * @return RLP-encoded payload
     */
    private byte[] encodePayload(final boolean includeSig, final Signature signature) {
        final List<RlpItem> items = new ArrayList<>(12);

        items.add(RlpNumeric.encodeLongUnsignedItem(chainId));
        items.add(RlpNumeric.encodeLongUnsignedItem(nonce));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(maxPriorityFeePerGas.value()));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(maxFeePerGas.value()));
        items.add(RlpNumeric.encodeLongUnsignedItem(gasLimit));
        items.add(new RlpString(to != null ? to.toBytes() : new byte[0]));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(value.value()));
        items.add(new RlpString(data.toBytes()));

        // Encode access list
        items.add(encodeAccessList());

        if (includeSig) {
            Objects.requireNonNull(signature, "signature is required");
            // For EIP-1559, v is just yParity (0 or 1), not EIP-155 encoded
            final int yParity = signature.v();
            if (yParity != 0 && yParity != 1) {
                throw new IllegalArgumentException(
                        "EIP-1559 signature v must be yParity (0 or 1), got: " + yParity);
            }
            items.add(RlpNumeric.encodeLongUnsignedItem(yParity));
            items.add(RlpNumeric.encodeBigIntegerUnsignedItem(new java.math.BigInteger(1, signature.r())));
            items.add(RlpNumeric.encodeBigIntegerUnsignedItem(new java.math.BigInteger(1, signature.s())));
        }

        return Rlp.encodeList(items);
    }

    /**
     * Encodes the access list as RLP.
     *
     * @return RLP list of access list entries
     */
    private RlpItem encodeAccessList() {
        if (accessList.isEmpty()) {
            return new RlpList(List.of());
        }

        final List<RlpItem> entries = new ArrayList<>(accessList.size());
        for (AccessListEntry entry : accessList) {
            final List<RlpItem> entryItems = new ArrayList<>(2);
            entryItems.add(new RlpString(entry.address().toBytes()));

            // Encode storage keys
            final List<RlpItem> storageKeys = new ArrayList<>(entry.storageKeys().size());
            for (io.brane.core.types.Hash key : entry.storageKeys()) {
                // Storage keys are 32-byte hashes
                final byte[] keyBytes = Hex.decode(key.value());
                storageKeys.add(new RlpString(keyBytes));
            }
            entryItems.add(new RlpList(storageKeys));

            entries.add(new RlpList(entryItems));
        }

        return new RlpList(entries);
    }
}
