package io.brane.primitives.rlp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * RLP representation of a list of items.
 */
public record RlpList(List<RlpItem> items) implements RlpItem {
    /**
     * Constructs an {@link RlpList} with a defensive copy of items.
     *
     * @param items the items to include in the list
     */
    public RlpList {
        Objects.requireNonNull(items, "items cannot be null");
        items = Collections.unmodifiableList(new ArrayList<>(items));
        if (items.contains(null)) {
            throw new IllegalArgumentException("items cannot contain null values");
        }
    }

    /**
     * Creates an {@link RlpList} from the provided items.
     *
     * @param items the items to include
     * @return {@link RlpList} containing the provided items
     */
    public static RlpList of(final RlpItem... items) {
        return new RlpList(Arrays.asList(items));
    }

    /**
     * Creates an {@link RlpList} from the provided list of items.
     *
     * @param items the items to include
     * @return {@link RlpList} containing the provided items
     */
    public static RlpList of(final List<RlpItem> items) {
        return new RlpList(items);
    }

    @Override
    public byte[] encode() {
        return Rlp.encodeList(items);
    }
}
