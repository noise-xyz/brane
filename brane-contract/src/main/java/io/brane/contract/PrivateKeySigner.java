package io.brane.contract;

import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.Signature;
import io.brane.core.tx.UnsignedTransaction;
import io.brane.core.types.Address;
import io.brane.primitives.Hex;

public final class PrivateKeySigner implements Signer {

    private final PrivateKey privateKey;

    public PrivateKeySigner(final String privateKeyHex) {
        this.privateKey = PrivateKey.fromHex(privateKeyHex);
    }

    @Override
    public Address address() {
        return privateKey.toAddress();
    }

    @Override
    public String signTransaction(final UnsignedTransaction tx, final long chainId) {
        final byte[] encodedForSigning = tx.encodeForSigning(chainId);
        final byte[] messageHash = Keccak256.hash(encodedForSigning);
        final Signature signature = privateKey.sign(messageHash);
        final byte[] envelope = tx.encodeAsEnvelope(signature);
        return Hex.encode(envelope);
    }
}
