package io.brane.internal.web3j.abi.datatypes.generated;

import java.math.BigInteger;
import io.brane.internal.web3j.abi.datatypes.Uint;

/**
 * Auto generated code.
 * <p><strong>Do not modifiy!</strong>
 * <p>Please use io.brane.internal.web3j.codegen.AbiTypesGenerator in the 
 * <a href="https://github.com/hyperledger/web3j/tree/main/codegen">codegen module</a> to update.
 */
public class Uint56 extends Uint {
    public static final Uint56 DEFAULT = new Uint56(BigInteger.ZERO);

    public Uint56(BigInteger value) {
        super(56, value);
    }

    public Uint56(long value) {
        this(BigInteger.valueOf(value));
    }
}
