package io.github.stealingdapenta.damageindicator.config;

import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.*;
import static io.github.stealingdapenta.damageindicator.utils.TextUtil.TEXT_UTIL;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class ConfigurationTestSupport {

    static final int REPEATED_ACCESS_COUNT = 200;
    static final int SERIALIZED_HEALTH_BAR_LIMIT = 256;
    static final List<ConfigKeys> HEALTH_BAR_APPEARANCE = List.of(HEALTH_BAR_PREFIX, HEALTH_BAR_SUFFIX,
            HEALTH_BAR_ALIVE_SYMBOL, HEALTH_BAR_DEAD_SYMBOL);

    private ConfigurationTestSupport() {
    }

    static Map<ConfigKeys, String> strings() {
        Map<ConfigKeys, String> values = new EnumMap<>(ConfigKeys.class);
        for (ConfigKeys key : ConfigKeys.values()) {
            values.put(key, key.asString());
        }
        return values;
    }

    static String serializedBar(int alive) {
        int length = HEALTH_BAR_LENGTH.asInt();
        return TEXT_UTIL.serializeComponent(HEALTH_BAR_PREFIX.asFormattedString()
                .append(TEXT_UTIL.repeatTextWithStyles(HEALTH_BAR_ALIVE_SYMBOL.asFormattedString(), alive))
                .append(TEXT_UTIL.repeatTextWithStyles(HEALTH_BAR_DEAD_SYMBOL.asFormattedString(), length - alive))
                .append(HEALTH_BAR_SUFFIX.asFormattedString()));
    }
}
