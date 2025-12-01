package io.brane.benchmark;

import org.openjdk.jmh.annotations.*;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class Web3jSignerBenchmark {

    private Credentials credentials;
    private RawTransaction legacyTx;
    private RawTransaction eip1559Tx;
    private RawTransaction largeTx;
    private long chainId = 31337;

    @Setup
    public void setup() {
        credentials = Credentials.create("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
        
        BigInteger nonce = BigInteger.ZERO;
        BigInteger gasPrice = new BigInteger("1000000000");
        BigInteger gasLimit = new BigInteger("21000");
        String to = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
        BigInteger value = BigInteger.ONE;
        
        legacyTx = RawTransaction.createEtherTransaction(
            nonce, gasPrice, gasLimit, to, value
        );

        eip1559Tx = RawTransaction.createTransaction(
            11155111,
            nonce,
            gasLimit,
            to,
            value,
            "",
            new BigInteger("1000000000"), // maxPriorityFee
            new BigInteger("2000000000")  // maxFee
        );
        
        // Large payload
        byte[] largeData = new byte[10240];
        java.util.Arrays.fill(largeData, (byte) 0xAA);
        String data = Numeric.toHexString(largeData);
        
        largeTx = RawTransaction.createTransaction(
            BigInteger.ONE,
            gasPrice,
            new BigInteger("100000"),
            to,
            BigInteger.ZERO,
            data
        );
    }

    @Benchmark
    public byte[] signLegacy() {
        return TransactionEncoder.signMessage(legacyTx, chainId, credentials);
    }

    @Benchmark
    public byte[] signEip1559() {
        return TransactionEncoder.signMessage(eip1559Tx, credentials);
    }

    @Benchmark
    public byte[] signLargePayload() {
        return TransactionEncoder.signMessage(largeTx, chainId, credentials);
    }
}
