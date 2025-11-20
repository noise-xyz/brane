package io.brane.contract;

import io.brane.core.types.Address;
import io.brane.internal.web3j.crypto.Credentials;
import io.brane.internal.web3j.crypto.RawTransaction;
import io.brane.internal.web3j.crypto.TransactionEncoder;
import io.brane.internal.web3j.utils.Numeric;

public final class PrivateKeySigner implements Signer {

    private final Credentials credentials;

    public PrivateKeySigner(final String privateKeyHex) {
        this.credentials = Credentials.create(privateKeyHex);
    }

    @Override
    public Address address() {
        return new Address(credentials.getAddress());
    }

    @Override
    public String signTransaction(final RawTransaction tx) {
        final byte[] signed = TransactionEncoder.signMessage(tx, credentials);
        return Numeric.toHexString(signed);
    }
}
