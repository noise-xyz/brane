package io.brane.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or method as internal API not intended for public use.
 *
 * <p>Elements annotated with {@code @InternalApi} are implementation details
 * that may change or be removed without notice between versions. External code
 * should not depend on these elements.
 *
 * <p>This annotation serves as documentation and a warning to users that the
 * annotated element is not part of the stable public API contract.
 *
 * <p><strong>Common usage:</strong>
 * <ul>
 *   <li>Utility classes in {@code internal} packages that must be public for
 *       cross-package access within the SDK</li>
 *   <li>Methods that exist for testing or framework integration only</li>
 *   <li>Implementation details exposed due to Java visibility constraints</li>
 * </ul>
 *
 * @since 0.1.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface InternalApi {
}
