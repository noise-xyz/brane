/*
 * Copyright 2019 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.brane.internal.web3j.crypto;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.brane.internal.web3j.crypto.transaction.type.TransactionType;
import io.brane.internal.web3j.rlp.RlpDecoder;
import io.brane.internal.web3j.rlp.RlpList;
import io.brane.internal.web3j.rlp.RlpString;
import io.brane.internal.web3j.rlp.RlpType;
import io.brane.internal.web3j.utils.Numeric;

import static java.util.stream.Collectors.toList;

public class TransactionDecoder {
    private static final int UNSIGNED_EIP1559TX_RLP_LIST_SIZE = 9;
    private static final int UNSIGNED_EIP2930TX_RLP_LIST_SIZE = 8;
    private static final int UNSIGNED_EIP7702TX_RLP_LIST_SIZE = 10;

    public static RawTransaction decode(final String hexTransaction) {
        final byte[] transaction = Numeric.hexStringToByteArray(hexTransaction);
        TransactionType transactionType = getTransactionType(transaction);

        switch (transactionType) {
            case EIP1559:
                return decodeEIP1559Transaction(transaction);
            case EIP2930:
                return decodeEIP2930Transaction(transaction);
            case EIP7702:
                return decodeEIP7702Transaction(transaction);
            default:
                return decodeLegacyTransaction(transaction);
        }
    }

    private static RawTransaction decodeEIP7702Transaction(final byte[] transaction) {
        // Strip off the type byte (0x04) before decoding the RLP list
        final byte[] encodedTx = Arrays.copyOfRange(transaction, 1, transaction.length);
        final RlpList rlpList = RlpDecoder.decode(encodedTx);
        final RlpList values = (RlpList) rlpList.getValues().get(0);
        final List<RlpType> fields = values.getValues();

        final long chainId = ((RlpString) fields.get(0)).asPositiveBigInteger().longValue();
        final BigInteger nonce = ((RlpString) fields.get(1)).asPositiveBigInteger();
        final BigInteger maxPriorityFeePerGas = ((RlpString) fields.get(2)).asPositiveBigInteger();
        final BigInteger maxFeePerGas = ((RlpString) fields.get(3)).asPositiveBigInteger();
        final BigInteger gasLimit = ((RlpString) fields.get(4)).asPositiveBigInteger();
        final String to = ((RlpString) fields.get(5)).asString();
        final BigInteger value = ((RlpString) fields.get(6)).asPositiveBigInteger();
        final String data = ((RlpString) fields.get(7)).asString();
        final List<RlpType> accessListRlp = ((RlpList) fields.get(8)).getValues();
        final List<AccessListObject> accessList = decodeAccessList(accessListRlp);
        final List<AuthorizationTuple> authorizationList =
                decodeAuthorizationList(((RlpList) fields.get(9)).getValues());
        // INV: Per the EIP, authorization list should be nonempty. We don't
        // enforce that here.

        final RawTransaction rawTransaction =
                RawTransaction.createTransaction(
                        chainId,
                        nonce,
                        maxPriorityFeePerGas,
                        maxFeePerGas,
                        gasLimit,
                        to,
                        value,
                        data,
                        accessList,
                        authorizationList);

        if (fields.size() == UNSIGNED_EIP7702TX_RLP_LIST_SIZE) {
            return rawTransaction;
        } else {
            final int yParity =
                    Numeric.toBigInt(((RlpString) fields.get(10)).getBytes()).intValue();
            final byte[] rBytes =
                    Numeric.toBytesPadded(
                            Numeric.toBigInt(((RlpString) fields.get(11)).getBytes()), 32);
            final byte[] sBytes =
                    Numeric.toBytesPadded(
                            Numeric.toBigInt(((RlpString) fields.get(12)).getBytes()), 32);

            final byte[] vBytes = Sign.getVFromRecId(yParity);

            final Sign.SignatureData signatureData = new Sign.SignatureData(vBytes, rBytes, sBytes);

            return new SignedRawTransaction(rawTransaction.getTransaction(), signatureData);
        }
    }

    private static TransactionType getTransactionType(final byte[] transaction) {
        // The first byte indicates a transaction type.
        byte firstByte = transaction[0];
        if (firstByte == TransactionType.EIP1559.getRlpType()) return TransactionType.EIP1559;
        else if (firstByte == TransactionType.EIP2930.getRlpType())
            return TransactionType.EIP2930;
        else if (firstByte == TransactionType.EIP7702.getRlpType()) return TransactionType.EIP7702;
        else return TransactionType.LEGACY;
    }

    private static RawTransaction decodeEIP1559Transaction(final byte[] transaction) {
        final byte[] encodedTx = Arrays.copyOfRange(transaction, 1, transaction.length);
        final RlpList rlpList = RlpDecoder.decode(encodedTx);
        final RlpList values = (RlpList) rlpList.getValues().get(0);

        final long chainId =
                ((RlpString) values.getValues().get(0)).asPositiveBigInteger().longValue();
        final BigInteger nonce = ((RlpString) values.getValues().get(1)).asPositiveBigInteger();
        final BigInteger maxPriorityFeePerGas =
                ((RlpString) values.getValues().get(2)).asPositiveBigInteger();
        final BigInteger maxFeePerGas =
                ((RlpString) values.getValues().get(3)).asPositiveBigInteger();
        final BigInteger gasLimit = ((RlpString) values.getValues().get(4)).asPositiveBigInteger();
        final String to = ((RlpString) values.getValues().get(5)).asString();

        final BigInteger value = ((RlpString) values.getValues().get(6)).asPositiveBigInteger();
        final String data = ((RlpString) values.getValues().get(7)).asString();
        List<AccessListObject> accessList =
                decodeAccessList(((RlpList) values.getValues().get(8)).getValues());

        final RawTransaction rawTransaction =
                RawTransaction.createTransaction(
                        chainId,
                        nonce,
                        gasLimit,
                        to,
                        value,
                        data,
                        maxPriorityFeePerGas,
                        maxFeePerGas,
                        accessList);

        if (values.getValues().size() == UNSIGNED_EIP1559TX_RLP_LIST_SIZE) {
            return rawTransaction;
        } else {
            final byte[] v =
                    Sign.getVFromRecId(
                            Numeric.toBigInt(((RlpString) values.getValues().get(9)).getBytes())
                                    .intValue());
            final byte[] r =
                    Numeric.toBytesPadded(
                            Numeric.toBigInt(((RlpString) values.getValues().get(10)).getBytes()),
                            32);
            final byte[] s =
                    Numeric.toBytesPadded(
                            Numeric.toBigInt(((RlpString) values.getValues().get(11)).getBytes()),
                            32);
            final Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);
            return new SignedRawTransaction(rawTransaction.getTransaction(), signatureData);
        }
    }

    private static RawTransaction decodeLegacyTransaction(final byte[] transaction) {
        final RlpList rlpList = RlpDecoder.decode(transaction);
        final RlpList values = (RlpList) rlpList.getValues().get(0);
        final BigInteger nonce = ((RlpString) values.getValues().get(0)).asPositiveBigInteger();
        final BigInteger gasPrice = ((RlpString) values.getValues().get(1)).asPositiveBigInteger();
        final BigInteger gasLimit = ((RlpString) values.getValues().get(2)).asPositiveBigInteger();
        final String to = ((RlpString) values.getValues().get(3)).asString();
        final BigInteger value = ((RlpString) values.getValues().get(4)).asPositiveBigInteger();
        final String data = ((RlpString) values.getValues().get(5)).asString();
        if (values.getValues().size() == 6
                || (values.getValues().size() == 8
                        && ((RlpString) values.getValues().get(7)).getBytes().length == 10)
                || (values.getValues().size() == 9
                        && ((RlpString) values.getValues().get(8)).getBytes().length == 10)) {
            // the 8th or 9nth element is the hex
            // representation of "restricted" for private transactions
            return RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, value, data);
        } else {
            final byte[] v = ((RlpString) values.getValues().get(6)).getBytes();
            final byte[] r =
                    Numeric.toBytesPadded(
                            Numeric.toBigInt(((RlpString) values.getValues().get(7)).getBytes()),
                            32);
            final byte[] s =
                    Numeric.toBytesPadded(
                            Numeric.toBigInt(((RlpString) values.getValues().get(8)).getBytes()),
                            32);
            final Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);
            return new SignedRawTransaction(
                    nonce, gasPrice, gasLimit, to, value, data, signatureData);
        }
    }

    private static RawTransaction decodeEIP2930Transaction(final byte[] transaction) {
        final byte[] encodedTx = Arrays.copyOfRange(transaction, 1, transaction.length);
        final RlpList rlpList = RlpDecoder.decode(encodedTx);
        final RlpList values = (RlpList) rlpList.getValues().get(0);

        final long chainId =
                ((RlpString) values.getValues().get(0)).asPositiveBigInteger().longValue();
        final BigInteger nonce = ((RlpString) values.getValues().get(1)).asPositiveBigInteger();
        final BigInteger gasPrice = ((RlpString) values.getValues().get(2)).asPositiveBigInteger();
        final BigInteger gasLimit = ((RlpString) values.getValues().get(3)).asPositiveBigInteger();
        final String to = ((RlpString) values.getValues().get(4)).asString();
        final BigInteger value = ((RlpString) values.getValues().get(5)).asPositiveBigInteger();
        final String data = ((RlpString) values.getValues().get(6)).asString();
        List<AccessListObject> accessList =
                decodeAccessList(((RlpList) values.getValues().get(7)).getValues());

        final RawTransaction rawTransaction =
                RawTransaction.createTransaction(
                        chainId, nonce, gasPrice, gasLimit, to, value, data, accessList);

        if (values.getValues().size() == UNSIGNED_EIP2930TX_RLP_LIST_SIZE) {
            return rawTransaction;
        } else {
            final byte[] v =
                    Sign.getVFromRecId(
                            Numeric.toBigInt(((RlpString) values.getValues().get(8)).getBytes())
                                    .intValue());
            final byte[] r =
                    Numeric.toBytesPadded(
                            Numeric.toBigInt(((RlpString) values.getValues().get(9)).getBytes()),
                            32);
            final byte[] s =
                    Numeric.toBytesPadded(
                            Numeric.toBigInt(((RlpString) values.getValues().get(10)).getBytes()),
                            32);
            final Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);
            return new SignedRawTransaction(rawTransaction.getTransaction(), signatureData);
        }
    }

    private static List<AccessListObject> decodeAccessList(List<RlpType> rlp) {
        return rlp.stream()
                .map(rawEntry -> ((RlpList) rawEntry).getValues())
                .map(
                        values -> {
                            return new AccessListObject(
                                    ((RlpString) values.get(0)).asString(),
                                    ((RlpList) values.get(1))
                                            .getValues().stream()
                                                    .map(rawKey -> ((RlpString) rawKey).asString())
                                                    .collect(toList()));
                        })
                .collect(toList());
    }

    private static List<AuthorizationTuple> decodeAuthorizationList(final List<RlpType> rlpList) {
        return rlpList.stream()
                .map(item -> (RlpList) item) // each authorization tuple is an RlpList
                .map(
                        tuple -> {
                            final List<RlpType> elements = tuple.getValues();
                            final BigInteger authChainId =
                                    ((RlpString) elements.get(0)).asPositiveBigInteger();
                            final String address = ((RlpString) elements.get(1)).asString();
                            final BigInteger authNonce =
                                    ((RlpString) elements.get(2)).asPositiveBigInteger();
                            final BigInteger yParity =
                                    ((RlpString) elements.get(3)).asPositiveBigInteger();
                            final BigInteger rValue =
                                    ((RlpString) elements.get(4)).asPositiveBigInteger();
                            final BigInteger sValue =
                                    ((RlpString) elements.get(5)).asPositiveBigInteger();

                            // Convert or store them as desired; for example:
                            return new AuthorizationTuple(
                                    authChainId, address, authNonce, yParity, rValue, sValue);
                        })
                .collect(Collectors.toList());
    }
}
