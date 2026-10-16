package io.github.stealingdapenta.damageindicator.config;

import static io.github.stealingdapenta.damageindicator.utils.TextUtil.TEXT_UTIL;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class RuntimeConfiguration {

    private static final Pattern RGB = Pattern.compile("&\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)");
    private static final int MAX_SERIALIZED_HEALTH_BAR_LENGTH = 256;
    private static final List<ConfigKeys> HEALTH_BAR_APPEARANCE = List.of(ConfigKeys.HEALTH_BAR_PREFIX,
            ConfigKeys.HEALTH_BAR_SUFFIX, ConfigKeys.HEALTH_BAR_ALIVE_SYMBOL, ConfigKeys.HEALTH_BAR_DEAD_SYMBOL);
    private final JavaPlugin plugin;
    private final FileConfiguration source;
    private final Map<ConfigKeys, Value> values;
    private final Set<ConfigKeys> rangeWarnings = ConcurrentHashMap.newKeySet();

    private RuntimeConfiguration(JavaPlugin plugin, FileConfiguration source, Map<ConfigKeys, Value> values) {
        this.plugin = plugin;
        this.source = source;
        this.values = Map.copyOf(values);
    }

    static RuntimeConfiguration load(JavaPlugin plugin, FileConfiguration config) {
        Map<ConfigKeys, Value> values = new EnumMap<>(ConfigKeys.class);
        for (ConfigKeys key : ConfigKeys.values()) {
            values.put(key, read(plugin, config, key));
        }
        validateHealthBarOutput(plugin, values);
        return new RuntimeConfiguration(plugin, config, values);
    }

    static RuntimeConfiguration single(JavaPlugin plugin, FileConfiguration config, ConfigKeys key) {
        return new RuntimeConfiguration(plugin, config, Map.of(key, read(plugin, config, key)));
    }

    boolean matches(JavaPlugin plugin, FileConfiguration config) {
        return this.plugin == plugin && source == config;
    }

    RuntimeConfiguration withSource(FileConfiguration config) {
        return new RuntimeConfiguration(plugin, config, values);
    }

    Value value(ConfigKeys key) {
        return values.get(key);
    }

    void warnRangeOnce(ConfigKeys key, Number minimum, Number maximum) {
        if (rangeWarnings.add(key)) {
            warn(plugin, key, "must be between " + minimum + " and " + maximum);
        }
    }

    private static Value read(JavaPlugin plugin, FileConfiguration config, ConfigKeys key) {
        Object raw = configuredValue(config, key);
        if (raw == null) {
            return parse(key, key.getDefaultValue());
        }
        try {
            return parse(key, raw);
        } catch (IllegalArgumentException exception) {
            warn(plugin, key, exception.getMessage());
            return parse(key, key.getDefaultValue());
        }
    }

    private static void validateHealthBarOutput(JavaPlugin plugin, Map<ConfigKeys, Value> values) {
        int length = values.get(ConfigKeys.HEALTH_BAR_LENGTH).integer();
        TextComponent prefix = values.get(ConfigKeys.HEALTH_BAR_PREFIX).formatted();
        TextComponent suffix = values.get(ConfigKeys.HEALTH_BAR_SUFFIX).formatted();
        TextComponent alive = values.get(ConfigKeys.HEALTH_BAR_ALIVE_SYMBOL).formatted();
        TextComponent dead = values.get(ConfigKeys.HEALTH_BAR_DEAD_SYMBOL).formatted();
        for (int aliveCount = 0; aliveCount <= length; aliveCount++) {
            TextComponent bar = prefix.append(TEXT_UTIL.repeatTextWithStyles(alive, aliveCount))
                    .append(TEXT_UTIL.repeatTextWithStyles(dead, length - aliveCount))
                    .append(suffix);
            if (TEXT_UTIL.serializeComponent(bar).length() > MAX_SERIALIZED_HEALTH_BAR_LENGTH) {
                plugin.getLogger().warning("Configured health bar exceeds Spigot's 256 UTF-16 character serialized name limit"
                        + " with " + aliveCount + " alive symbols out of " + length
                        + ". Using default health_bar_prefix, health_bar_suffix, health_bar_alive_symbol and health_bar_dead_symbol;"
                        + " retaining health_bar_length=" + length + ".");
                for (ConfigKeys key : HEALTH_BAR_APPEARANCE) {
                    values.put(key, parse(key, key.getDefaultValue()));
                }
                return;
            }
        }
    }

    private static Object configuredValue(FileConfiguration config, ConfigKeys key) {
        // contains(path, true) ignores Bukkit defaults, even when copyDefaults is enabled.
        if (config.contains(key.getConfigKey(), true)) {
            return config.get(key.getConfigKey(), null);
        }
        for (String legacy : key.getLegacyKeys()) {
            if (config.contains(legacy, true)) {
                return config.get(legacy, null);
            }
        }
        // Supports standalone FileConfiguration implementations without explicit-key metadata.
        // A real YAML configuration must always use our authoritative defaults instead.
        if (config.getDefaults() == null) {
            return config.getString(key.getConfigKey());
        }
        return null;
    }

    private static Value parse(ConfigKeys key, Object raw) {
        return switch (key.valueType()) {
            case BOOLEAN -> {
                if (!(raw instanceof Boolean) && !(raw instanceof String)) {
                    throw new IllegalArgumentException("must be a boolean (true or false)");
                }
                String text = scalar(key, raw).trim();
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("must be a boolean (true or false)");
                }
                boolean value = Boolean.parseBoolean(text);
                yield new Value(Boolean.toString(value), value, 0, 0, null, null);
            }
            case INTEGER -> {
                String text = numericText(key, raw);
                int value;
                try {
                    value = Integer.parseInt(text);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("must be a whole number between " + key.minimum() + " and " + key.maximum());
                }
                checkRange(key, value);
                yield new Value(Integer.toString(value), false, value, value, null, null);
            }
            case DECIMAL -> {
                String text = numericText(key, raw);
                double value;
                try {
                    value = Double.parseDouble(text);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("must be a finite number between " + key.minimum() + " and " + key.maximum());
                }
                checkRange(key, value);
                yield new Value(Double.toString(value), false, 0, value, null, null);
            }
            case COLOR -> {
                String text = text(key, raw);
                validateColor(text);
                yield new Value(text, false, 0, 0, TEXT_UTIL.parseRGB(text), null);
            }
            case TEXT -> {
                String text = text(key, raw);
                validateFormattedColors(text);
                yield new Value(text, false, 0, 0, null, TEXT_UTIL.parseFormattedString(text));
            }
        };
    }

    private static String numericText(ConfigKeys key, Object raw) {
        if (!(raw instanceof Number) && !(raw instanceof String)) {
            throw new IllegalArgumentException("must be a number, not " + raw.getClass().getSimpleName());
        }
        return scalar(key, raw).trim();
    }

    private static String text(ConfigKeys key, Object raw) {
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException("must be text, not " + raw.getClass().getSimpleName());
        }
        return scalar(key, raw);
    }

    private static String scalar(ConfigKeys key, Object raw) {
        String text = raw.toString();
        if (text.length() > key.maximumTextLength()) {
            throw new IllegalArgumentException("must not exceed " + key.maximumTextLength() + " characters");
        }
        return text;
    }

    private static void checkRange(ConfigKeys key, double value) {
        if (!Double.isFinite(value) || value < key.minimum() || value > key.maximum()) {
            throw new IllegalArgumentException("must be finite and between " + key.minimum() + " and " + key.maximum());
        }
    }

    private static void validateColor(String text) {
        Matcher matcher = RGB.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("must use &(R,G,B), with each channel between 0 and 255");
        }
        for (int channel = 1; channel <= 3; channel++) {
            if (Integer.parseInt(matcher.group(channel)) > 255) {
                throw new IllegalArgumentException("RGB channels must be between 0 and 255");
            }
        }
    }

    private static void validateFormattedColors(String text) {
        int offset = 0;
        while ((offset = text.indexOf("&(", offset)) >= 0) {
            Matcher matcher = RGB.matcher(text);
            matcher.region(offset, text.length());
            if (!matcher.lookingAt()) {
                throw new IllegalArgumentException("contains a malformed &(R,G,B) color");
            }
            validateColor(matcher.group());
            offset = matcher.end();
        }
    }

    private static void warn(JavaPlugin plugin, ConfigKeys key, String reason) {
        plugin.getLogger().warning("Invalid config value for '" + key.getConfigKey() + "': " + reason
                + ". Using the default value.");
    }

    record Value(String text, boolean bool, int integer, double number, TextColor color, TextComponent formatted) {
    }
}
