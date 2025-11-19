package io.brane.internal.web3j.abi.datatypes.generated;

import java.math.BigInteger;
import io.brane.internal.web3j.abi.datatypes.Int;

/**
 * Auto generated code.
 * <p><strong>Do not modifiy!</strong>
 * <p>Please use io.brane.internal.web3j.codegen.AbiTypesGenerator in the 
 * <a href="https://github.com/hyperledger/web3j/tree/main/codegen">codegen module</a> to update.
 */
public class Int120 extends Int {
    public static final Int120 DEFAULT = new Int120(BigInteger.ZERO);

    public Int120(BigInteger value) {
        super(120, value);
    }

    public Int120(long value) {
        this(BigInteger.valueOf(value));
    }
}
