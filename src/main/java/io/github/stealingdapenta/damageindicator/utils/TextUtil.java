package io.github.stealingdapenta.damageindicator.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;

public class TextUtil {

    /**
     * All possible formatting options, exhausted list: b s u i o matching bold strikethrough underline italic obfuscated
     */
    private static final Map<String, TextDecoration> FORMAT_CODE_STYLES = Map.of("b", TextDecoration.BOLD, "s", TextDecoration.STRIKETHROUGH, "u",
                                                                                 TextDecoration.UNDERLINED, "i", TextDecoration.ITALIC, "o",
                                                                                 TextDecoration.OBFUSCATED);
    private static final Pattern RGB_PATTERN = Pattern.compile("&(\\(\\d{1,3},\\d{1,3},\\d{1,3}\\))");
    private static final Pattern DECORATOR_PATTERN = Pattern.compile("&([buosir])");
    /**
     * This regex is designed to match color codes in the format &(r,g,b) or the style codes &b, &u, &o, &s, or &i. The backslashes are used to escape
     * parentheses, and capturing groups allow extracting the individual color components. Examples: &(12,7,222), &(255,255,255), &b, &u, &o, &s, &i (.*) allows
     * all the text after.
     */
    private static final Pattern TEXT_PATTERN = Pattern.compile(RGB_PATTERN + "|" + DECORATOR_PATTERN);
    private static TextUtil instance;

    public static TextUtil getInstance() {
        if (Objects.isNull(instance)) {
            instance = new TextUtil();
        }

        return instance;
    }

    Style createStyle(TextDecoration decoration) {
        return Style.style()
                    .decoration(decoration, State.TRUE)
                    .build();
    }


    public TextComponent parseFormattedString(String input) {
        String[] segments = splitByPatternWithDelimiters(input);

        if (segments.length <= 1) {
            return Component.text(segments[0]);
        }

        List<TextComponent> formattedSegments = new ArrayList<>();

        TextComponent.Builder textSegment = Component.text();

        int index = 0;

        while (index < segments.length) {
            if (Objects.isNull(textSegment)) {
                textSegment = Component.text();
            }
            String currentElement = segments[index];

            if (isRgbPattern(currentElement)) {
                textSegment.color(parseRGB(currentElement));
            } else if (isDecoratorPattern(currentElement)) {
                if (currentElement.contains("r")) {
                    disableAllStyle(textSegment);
                } else {
                    textSegment.decorate(FORMAT_CODE_STYLES.get(currentElement.substring(1)));
                }
            } else if (!currentElement.isBlank()) {
                if (textSegment.content()
                               .isEmpty()) {
                    textSegment.content(currentElement);
                    formattedSegments.add(textSegment.build());
                    textSegment = null;
                }
            }

            index++;
        }

        return combineTextComponents(formattedSegments);
    }

    private void disableAllStyle(TextComponent.Builder componentBuilder) {
        for (TextDecoration decoration : TextDecoration.values()) {
            componentBuilder.decoration(decoration, State.FALSE);
        }

        componentBuilder.color(TextColor.color(0xFFFFFF));
    }


    /**
     * This method is stolen from Java 21 library & adapted to only suit my needs. When upgrading back to J21, then this method should be replaced with the
     * native one.
     */
    private String[] splitByPatternWithDelimiters(CharSequence input) {
        int index = 0;
        ArrayList<String> matchList = new ArrayList<>();
        Matcher m = TEXT_PATTERN.matcher(input);

        // Add segments before each match found
        while (m.find()) {
            {
                if (index == 0 && index == m.start() && 0 == m.end()) {
                    // no empty leading substring included for zero-width match
                    // at the beginning of the input char sequence.
                    continue;
                }
                String match = input.subSequence(index, m.start())
                                    .toString();
                matchList.add(match);
                index = m.end();

                matchList.add(input.subSequence(m.start(), index)
                                   .toString()); // Add the delimiter

            }
        }

        // If no match was found, return this
        if (index == 0) {
            return new String[]{input.toString()};
        }

        // Add remaining segment
        matchList.add(input.subSequence(index, input.length())
                           .toString());

        // Construct result
        int resultSize = matchList.size();
        while (resultSize > 0 && matchList.get(resultSize - 1)
                                          .isEmpty()) {
            resultSize--;
        }
        String[] result = new String[resultSize];
        return matchList.subList(0, resultSize)
                        .toArray(result);
    }

    private TextComponent combineTextComponents(List<TextComponent> textComponents) {
        if (textComponents.isEmpty()) {
            throw new IllegalArgumentException("At least one TextComponent must be provided");
        }

        TextComponent combinedComponent = textComponents.get(textComponents.size() - 1);

        for (int i = textComponents.size() - 2; i >= 0; i--) {
            combinedComponent = textComponents.get(i)
                                              .append(combinedComponent);
        }

        return combinedComponent;
    }


    private boolean isRgbPattern(String element) {
        return RGB_PATTERN.matcher(element)
                          .matches();
    }

    private boolean isDecoratorPattern(String element) {
        return DECORATOR_PATTERN.matcher(element)
                                .matches();
    }

    public TextComponent repeatTextWithStyles(TextComponent textComponent, int times) {
        StringBuilder repeatedText = new StringBuilder();
        for (int i = 0; i < times; i++) {
            repeatedText.append(textComponent.content());
        }

        return textComponent.toBuilder()
                            .content(repeatedText.toString())
                            .build();
    }


    TextColor parseRGB(String input) {
        Matcher matcher = RGB_PATTERN.matcher(input.replace(" ", ""));

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Problem formatting RGB from input.");
        }

        String rgbString = matcher.group(1)
                                  .replaceAll("[()]", ""); // Remove parentheses
        String[] rgbComponents = rgbString.split(",");

        int red = parseRGBComponent(rgbComponents[0]);
        int green = parseRGBComponent(rgbComponents[1]);
        int blue = parseRGBComponent(rgbComponents[2]);

        return TextColor.color(toValidRGB(red), toValidRGB(green), toValidRGB(blue));
    }

    private int parseRGBComponent(String component) {
        try {
            return Integer.parseInt(component);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid RGB component: " + component, e);
        }
    }

    private int toValidRGB(int value) {
        return value % 256;
    }
}