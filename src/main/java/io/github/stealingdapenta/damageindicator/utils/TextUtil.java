package io.github.stealingdapenta.damageindicator.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Utility singleton for formatting text with Adventure components using color codes and style flags. Supports both RGB (&(R,G,B)) and decorators like &b (bold), &u (underlined), etc.
 */
public enum TextUtil {
    TEXT_UTIL;

    private static final Map<String, TextDecoration> FORMAT_CODE_STYLES = Map.of("b", TextDecoration.BOLD, "s", TextDecoration.STRIKETHROUGH, "u", TextDecoration.UNDERLINED, "i", TextDecoration.ITALIC, "o", TextDecoration.OBFUSCATED);

    // Now allows optional whitespace inside the RGB tuple
    private static final Pattern RGB_PATTERN = Pattern.compile("&\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)");
    private static final Pattern DECORATOR_PATTERN = Pattern.compile("&([buosir])");
    private static final Pattern TEXT_PATTERN = Pattern.compile(RGB_PATTERN.pattern() + "|" + DECORATOR_PATTERN.pattern());

    /**
     * Parses a string with Adventure formatting codes into a TextComponent. Supports: color codes (&(r,g,b)) and decorator codes (&b, &i, &r, etc.)
     */
    public TextComponent parseFormattedString(String input) {
        String[] segments = splitByPatternWithDelimiters(input);

        if (segments.length == 1) {
            return Component.text(segments[0]);
        }

        List<TextComponent> formatted = new ArrayList<>();
        TextComponent.Builder builder = Component.text();

        for (String segment : segments) {
            if (isRgbPattern(segment)) {
                builder.color(parseRGB(segment));
            } else if (isDecoratorPattern(segment)) {
                String code = segment.substring(1);
                if ("r".equals(code)) {
                    // Flush current styled content (if any)
                    if (!builder.content()
                                .isEmpty()) {
                        formatted.add(builder.build());
                    }
                    builder = Component.text();
                    disableAllStyle(builder); // reset styles for next segment
                } else {
                    builder.decorate(FORMAT_CODE_STYLES.get(code));
                }
            } else if (!segment.isBlank()) {
                if (builder.content()
                           .isEmpty()) {
                    builder.content(segment);
                    formatted.add(builder.build());
                    builder = Component.text();
                }
            }
        }

        return combineTextComponents(formatted);
    }

    /**
     * Removes all styling and sets color to white.
     */
    private void disableAllStyle(TextComponent.Builder builder) {
        for (TextDecoration d : TextDecoration.values()) {
            builder.decoration(d, TextDecoration.State.FALSE);
        }
        builder.color(TextColor.color(0xFFFFFF)); // reset to white
    }

    /**
     * Combines a list of components into a single one. Throws if empty.
     */
    public TextComponent combineTextComponents(List<TextComponent> textComponents) {
        if (textComponents.isEmpty()) {
            throw new IllegalArgumentException("At least one TextComponent must be provided");
        }

        TextComponent combined = textComponents.get(0);
        for (int i = 1; i < textComponents.size(); i++) {
            combined = combined.append(textComponents.get(i));
        }
        return combined;
    }

    /**
     * Parses an RGB color from a string like &(255,255,255)
     */
    public TextColor parseRGB(String input) {
        Matcher matcher = RGB_PATTERN.matcher(input.replace(" ", ""));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Problem formatting RGB from input: " + input);
        }

        int red = toValidRGB(parseRGBComponent(matcher.group(1)));
        int green = toValidRGB(parseRGBComponent(matcher.group(2)));
        int blue = toValidRGB(parseRGBComponent(matcher.group(3)));

        return TextColor.color(red, green, blue);
    }

    /**
     * Parses an integer color component, throwing if invalid.
     */
    public int parseRGBComponent(String part) {
        try {
            return Integer.parseInt(part.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid RGB component: " + part, e);
        }
    }

    private int toValidRGB(int value) {
        return value % 256;
    }

    /**
     * Repeats a styled text component's content n times while preserving style.
     */
    public TextComponent repeatTextWithStyles(TextComponent textComponent, int times) {
        if (times < 0) {
            throw new IllegalArgumentException("Number of repetitions should be greater than zero.");
        }

        return textComponent.toBuilder()
                            .content(textComponent.content()
                                                  .repeat(times))
                            .build();
    }

    private boolean isRgbPattern(String segment) {
        return RGB_PATTERN.matcher(segment)
                          .matches();
    }

    private boolean isDecoratorPattern(String segment) {
        return DECORATOR_PATTERN.matcher(segment)
                                .matches();
    }

    /**
     * Splits an input string while preserving style codes as separate segments. Similar to Java 21's splitWithDelimiters, adapted for current usage.
     */
    private String[] splitByPatternWithDelimiters(CharSequence input) {
        List<String> parts = new ArrayList<>();
        Matcher matcher = TEXT_PATTERN.matcher(input);

        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                parts.add(input.subSequence(last, matcher.start())
                               .toString());
            }
            parts.add(matcher.group()); // full match
            last = matcher.end();
        }

        if (last < input.length()) {
            parts.add(input.subSequence(last, input.length())
                           .toString());
        }

        return parts.toArray(new String[0]);
    }
}
