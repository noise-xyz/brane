package io.brane.rpc;

import io.brane.core.types.Address;
import io.brane.internal.web3j.crypto.Credentials;
import io.brane.internal.web3j.crypto.RawTransaction;
import io.brane.internal.web3j.crypto.TransactionEncoder;
import io.brane.internal.web3j.utils.Numeric;

/**
 * TransactionSigner backed by a raw private key.
 */
public final class PrivateKeyTransactionSigner implements TransactionSigner {

    private final Credentials credentials;
    private final Address address;

    public PrivateKeyTransactionSigner(final String privateKeyHex) {
        this.credentials = Credentials.create(privateKeyHex);
        this.address = new Address(credentials.getAddress());
    }

    public Address address() {
        return address;
    }

    @Override
    public String sign(final RawTransaction tx) {
        final byte[] signed = TransactionEncoder.signMessage(tx, credentials);
        return Numeric.toHexString(signed);
    }
}
