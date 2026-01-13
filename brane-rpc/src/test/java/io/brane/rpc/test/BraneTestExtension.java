// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc.test;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import io.brane.rpc.Brane;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.SnapshotId;

/**
 * JUnit 5 extension for Brane SDK integration tests with automatic snapshot/revert.
 *
 * <p>This extension manages the lifecycle of a {@link Brane.Tester} instance and provides
 * automatic snapshot/revert functionality to isolate tests from each other. Each test runs
 * against a clean blockchain state.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Creates a {@link Brane.Tester} connected to local Anvil before all tests</li>
 *   <li>Takes a snapshot before each test</li>
 *   <li>Reverts to the snapshot after each test</li>
 *   <li>Closes the tester after all tests</li>
 *   <li>Supports parameter injection for {@link Brane.Tester}, {@link Brane.Signer}, {@link Brane.Reader}, and {@link BraneProvider}</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @ExtendWith(BraneTestExtension.class)
 * class MyContractTest {
 *
 *     @Test
 *     void testTransfer(Brane.Tester tester) {
 *         // Each test starts with a fresh blockchain state
 *         tester.setBalance(someAddress, Wei.fromEther("100"));
 *
 *         // ... test code ...
 *
 *         // Changes are automatically reverted after the test
 *     }
 *
 *     @Test
 *     void testWithSigner(Brane.Signer signer) {
 *         // Can inject Brane.Signer directly
 *         Hash txHash = signer.sendTransaction(request);
 *     }
 *
 *     @Test
 *     void testReadOnly(Brane.Reader reader) {
 *         // Can inject Brane.Reader for read-only operations
 *         Wei balance = reader.getBalance(address);
 *     }
 * }
 * }</pre>
 *
 * <h2>Requirements</h2>
 * <p>Requires Anvil running on {@code http://127.0.0.1:8545}. Start with:
 * <pre>
 * anvil
 * </pre>
 *
 * <p>For EIP-4844 blob transaction tests, use:
 * <pre>
 * anvil --hardfork cancun
 * </pre>
 *
 * @see Brane.Tester
 * @see Brane#connectTest()
 * @since 0.5.0
 */
public class BraneTestExtension implements
        BeforeAllCallback,
        AfterAllCallback,
        BeforeEachCallback,
        AfterEachCallback,
        ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(BraneTestExtension.class);

    private static final String RPC_URL_PROPERTY = "brane.test.rpc.url";

    private static final String TESTER_KEY = "tester";
    private static final String READER_KEY = "reader";
    private static final String PROVIDER_KEY = "provider";
    private static final String SNAPSHOT_KEY = "snapshot";

    @Override
    public void beforeAll(ExtensionContext context) {
        // Only create tester in the root test class, not in nested classes
        if (isNestedClass(context)) {
            return;
        }
        var tester = Brane.connectTest();
        getStore(context).put(TESTER_KEY, tester);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // Only close resources in the root test class, not in nested classes
        if (isNestedClass(context)) {
            return;
        }
        var tester = getTester(context);
        if (tester != null) {
            closeQuietly(tester);
        }
        var reader = getStore(context).get(READER_KEY, Brane.Reader.class);
        if (reader != null) {
            closeQuietly(reader);
        }
        var provider = getStore(context).get(PROVIDER_KEY, BraneProvider.class);
        if (provider != null) {
            closeQuietly(provider);
        }
    }

    /**
     * Checks if the context is for a nested test class.
     */
    private boolean isNestedClass(ExtensionContext context) {
        return context.getParent()
                .flatMap(ExtensionContext::getTestClass)
                .isPresent();
    }

    private void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            // Ignore close exceptions in test cleanup
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        var tester = getTester(context);
        if (tester != null) {
            SnapshotId snapshot = tester.snapshot();
            getStore(context).put(SNAPSHOT_KEY, snapshot);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var tester = getTester(context);
        var snapshot = getStore(context).remove(SNAPSHOT_KEY, SnapshotId.class);
        if (tester != null && snapshot != null) {
            // Try to revert. If it fails (e.g., snapshot was invalidated by reset()),
            // silently ignore - the test already completed and we don't want to mask
            // the actual test result with a cleanup error.
            try {
                tester.revert(snapshot);
            } catch (Exception e) {
                // Snapshot was likely invalidated by a reset() call in the test
            }
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return type == Brane.Tester.class
                || type == Brane.Signer.class
                || type == Brane.Reader.class
                || type == BraneProvider.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        var tester = getTester(extensionContext);

        if (tester == null) {
            throw new ParameterResolutionException("Brane.Tester not initialized");
        }

        if (type == Brane.Tester.class) {
            return tester;
        } else if (type == Brane.Signer.class) {
            return tester.asSigner();
        } else if (type == Brane.Reader.class) {
            return getOrCreateReader(extensionContext);
        } else if (type == BraneProvider.class) {
            return getOrCreateProvider(extensionContext);
        }

        throw new ParameterResolutionException("Unsupported parameter type: " + type);
    }

    private Brane.Reader getOrCreateReader(ExtensionContext context) {
        var store = getStore(context);
        var reader = store.get(READER_KEY, Brane.Reader.class);
        if (reader == null) {
            reader = Brane.builder()
                    .rpcUrl(Brane.DEFAULT_ANVIL_URL)
                    .buildReader();
            store.put(READER_KEY, reader);
        }
        return reader;
    }

    private BraneProvider getOrCreateProvider(ExtensionContext context) {
        var store = getStore(context);
        var provider = store.get(PROVIDER_KEY, BraneProvider.class);
        if (provider == null) {
            provider = HttpBraneProvider.builder(Brane.DEFAULT_ANVIL_URL).build();
            store.put(PROVIDER_KEY, provider);
        }
        return provider;
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getRoot().getStore(NAMESPACE);
    }

    private Brane.Tester getTester(ExtensionContext context) {
        return getStore(context).get(TESTER_KEY, Brane.Tester.class);
    }
}
