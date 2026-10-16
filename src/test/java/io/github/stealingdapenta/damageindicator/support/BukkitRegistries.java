package io.github.stealingdapenta.damageindicator.support;

import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageType;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/** Serverless, key-stable registries for registry-backed Bukkit interface constants. */
public final class BukkitRegistries {

    private static final Map<Class<?>, Registry<?>> REGISTRIES = new HashMap<>();
    private static boolean initialized;

    private BukkitRegistries() {
    }

    /** Call before creating Bukkit static mocks or mocking Attribute/DamageType. */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        try (MockedStatic<Bukkit> ignored = mockStatic(Bukkit.class, invocation -> {
            if (invocation.getMethod().getName().equals("getRegistry")) {
                return registry(invocation.getArgument(0));
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        })) {
            Objects.requireNonNull(Registry.ATTRIBUTE);
            Objects.requireNonNull(Registry.DAMAGE_TYPE);
            Objects.requireNonNull(Attribute.MAX_HEALTH);
            Objects.requireNonNull(DamageType.GENERIC);
            initialized = true;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Keyed> Registry<T> registry(Class<T> type) {
        Registry<?> existing = REGISTRIES.get(type);
        if (existing == null) {
            Registry<T> created = new InterfaceRegistry<>(type);
            // Registry initialization may reenter this method for its static fields.
            REGISTRIES.putIfAbsent(type, created);
            existing = REGISTRIES.get(type);
        }
        return (Registry<T>) existing;
    }

    private static final class InterfaceRegistry<T extends Keyed> implements Registry<T> {

        private final Class<T> type;
        private final Map<NamespacedKey, T> entries = new HashMap<>();

        private InterfaceRegistry(Class<T> type) {
            this.type = type;
        }

        @Override
        public T get(NamespacedKey key) {
            return entries.computeIfAbsent(Objects.requireNonNull(key), this::entry);
        }

        @Override
        public T getOrThrow(NamespacedKey key) {
            return get(key);
        }

        @Override
        public Stream<T> stream() {
            return entries.values().stream();
        }

        @Override
        public Iterator<T> iterator() {
            return entries.values().iterator();
        }

        private T entry(NamespacedKey key) {
            if (!type.isInterface()) {
                throw new UnsupportedOperationException("Test registry only supports interface entries: " + type.getName());
            }
            Object value = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "getKey", "getKeyOrThrow", "getKeyOrNull" -> key;
                    case "isRegistered" -> true;
                    case "toString" -> type.getSimpleName() + "[" + key + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "name" -> key.getKey().toUpperCase(Locale.ROOT);
                    case "getTranslationKey" -> type.getSimpleName().toLowerCase(Locale.ROOT) + "." + key.getNamespace() + "." + key.getKey();
                    default -> defaultValue(method.getReturnType());
                };
            });
            return type.cast(value);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        if (type.isEnum()) return type.getEnumConstants()[0];
        throw new UnsupportedOperationException("Unsupported test registry value: " + type.getName());
    }
}
