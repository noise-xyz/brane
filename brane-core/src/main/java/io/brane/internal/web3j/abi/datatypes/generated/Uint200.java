package io.brane.internal.web3j.abi.datatypes.generated;

import java.math.BigInteger;
import io.brane.internal.web3j.abi.datatypes.Uint;

/**
 * Auto generated code.
 * <p><strong>Do not modifiy!</strong>
 * <p>Please use io.brane.internal.web3j.codegen.AbiTypesGenerator in the 
 * <a href="https://github.com/hyperledger/web3j/tree/main/codegen">codegen module</a> to update.
 */
public class Uint200 extends Uint {
    public static final Uint200 DEFAULT = new Uint200(BigInteger.ZERO);

    public Uint200(BigInteger value) {
        super(200, value);
    }

    public Uint200(long value) {
        this(BigInteger.valueOf(value));
    }
}
