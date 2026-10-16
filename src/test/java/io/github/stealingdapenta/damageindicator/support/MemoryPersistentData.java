package io.github.stealingdapenta.damageindicator.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory Bukkit tags, retaining primitive types and namespace-qualified keys.
 */
public final class MemoryPersistentData implements PersistentDataContainer {

    private final Map<NamespacedKey, Object> values = new HashMap<>();

    @Override
    public <P, C> void set(@NotNull NamespacedKey key, @NotNull PersistentDataType<P, C> type, @NotNull C value) {
        values.put(key, type.toPrimitive(value, getAdapterContext()));
    }

    @Override
    public <P, C> boolean has(@NotNull NamespacedKey key, @NotNull PersistentDataType<P, C> type) {
        return type.getPrimitiveType()
                   .isInstance(values.get(key));
    }

    @Override
    public boolean has(@NotNull NamespacedKey key) {
        return values.containsKey(key);
    }

    @Override
    public <P, C> @Nullable C get(@NotNull NamespacedKey key, @NotNull PersistentDataType<P, C> type) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        return type.fromPrimitive(type.getPrimitiveType()
                                      .cast(value), getAdapterContext());
    }

    @Override
    public <P, C> @NotNull C getOrDefault(@NotNull NamespacedKey key, @NotNull PersistentDataType<P, C> type, @NotNull C defaultValue) {
        C value = get(key, type);
        return value == null ? defaultValue : value;
    }

    @Override
    public @NotNull Set<NamespacedKey> getKeys() {
        return Set.copyOf(values.keySet());
    }

    @Override
    public void remove(@NotNull NamespacedKey key) {
        values.remove(key);
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public void copyTo(@NotNull PersistentDataContainer other, boolean replace) {
        values.forEach((key, value) -> {
            if (replace || !other.has(key)) {
                if (value instanceof Byte number) {
                    other.set(key, PersistentDataType.BYTE, number);
                } else if (value instanceof String text) {
                    other.set(key, PersistentDataType.STRING, text);
                } else {
                    throw new UnsupportedOperationException("Unsupported fixture tag: " + value.getClass());
                }
            }
        });
    }

    @Override
    public @NotNull PersistentDataAdapterContext getAdapterContext() {
        return MemoryPersistentData::new;
    }
}
