package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import java.util.List;
import java.util.Objects;

public final class AccessListEntry {

    private final Address address;
    private final List<Hash> storageKeys;

    public AccessListEntry(final Address address, final List<Hash> storageKeys) {
        this.address = Objects.requireNonNull(address, "address");
        this.storageKeys = List.copyOf(Objects.requireNonNull(storageKeys, "storageKeys"));
    }

    public Address address() {
        return address;
    }

    public List<Hash> storageKeys() {
        return storageKeys;
    }

    @Override
    public String toString() {
        return "AccessListEntry{" + "address=" + address + ", storageKeys=" + storageKeys + '}';
    }
}
