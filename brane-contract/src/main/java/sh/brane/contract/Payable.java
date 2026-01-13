// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a contract interface method as payable, allowing it to send ETH.
 *
 * <p>When a method is annotated with {@code @Payable}, the first parameter
 * of type {@link sh.brane.core.types.Wei} is used as the value to send with
 * the transaction. This parameter is NOT passed to the contract function.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * public interface MyContract {
 *     // Sends `value` Wei with the deposit() call
 *     @Payable
 *     TransactionReceipt deposit(Wei value);
 *
 *     // Sends `amount` Wei with mint(to) call
 *     @Payable
 *     TransactionReceipt mint(Wei amount, Address to);
 * }
 *
 * // Usage:
 * MyContract contract = BraneContract.bind(...);
 * contract.deposit(Wei.ether(1)); // Sends 1 ETH
 * contract.mint(Wei.gwei(100), recipientAddress);
 * }</pre>
 *
 * @see BraneContract#bind
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Payable {
}
