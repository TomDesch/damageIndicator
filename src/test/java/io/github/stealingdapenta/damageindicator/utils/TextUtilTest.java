package io.github.stealingdapenta.damageindicator.utils;

import static io.github.stealingdapenta.damageindicator.utils.TextUtil.TEXT_UTIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class TextUtilTest {

    @Test
    void combineTextComponents_EmptyList_IllegalArgumentExceptionThrown() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> TEXT_UTIL.combineTextComponents(Collections.emptyList()));

        assertEquals("At least one TextComponent must be provided", exception.getMessage());
    }


    @Test
    void parseRGB_Valid_Success() {
        String input = "&(100, 150, 200)";
        TextColor result = TEXT_UTIL.parseRGB(input);
        assertNotNull(result);
        assertEquals(100, result.red());
        assertEquals(150, result.green());
        assertEquals(200, result.blue());
    }

    @Test
    void parseRGB_LeadingSpaces_Success() {
        String input = "  &(50, 100, 150)";
        TextColor result = TEXT_UTIL.parseRGB(input);
        assertNotNull(result);
        assertEquals(50, result.red());
        assertEquals(100, result.green());
        assertEquals(150, result.blue());
    }

    @Test
    void parseRGB_TrailingSpaces_Success() {
        String input = "&(25, 75, 125)  ";
        TextColor result = TEXT_UTIL.parseRGB(input);
        assertNotNull(result);
        assertEquals(25, result.red());
        assertEquals(75, result.green());
        assertEquals(125, result.blue());
    }

    @Test
    void parseRGB_Invalid_IllegalArgumentException() {
        String input = "&(aaa, 555, 777)";
        assertThrows(IllegalArgumentException.class, () -> TEXT_UTIL.parseRGB(input));
    }

    @Test
    void parseRGB_TooHighValues_Success() {
        String input = "&(256, 512, 768)";
        TextColor result = TEXT_UTIL.parseRGB(input);
        assertNotNull(result);
        assertEquals(0, result.red());
        assertEquals(0, result.green());
        assertEquals(0, result.blue());
    }

    @Test
    void parseFormattedString_InvalidRgbCode_RemainsLiteral() {
        String input = "&(abc,def,ghi)";

        assertEquals(Component.text(input), TEXT_UTIL.parseFormattedString(input));
    }

    @Test
    void parseFormattedString_NoFormat_PlainText() {
        String input = "Hello, World!";

        TextComponent result = TEXT_UTIL.parseFormattedString(input);

        TextComponent expected = Component.text(input);
        assertEquals(expected, result);
    }

    @Test
    void parseFormattedString_ColorCodes_Formatted() {
        String input = "&(255,0,0)Red Text";
        TextColor color = TextColor.color(255, 0, 0);

        TextComponent result = TEXT_UTIL.parseFormattedString(input);

        TextComponent expected = Component.text("Red Text", color);
        assertEquals(expected, result);
    }

    @Test
    void parseFormattedString_DecorationCodes_Formatted() {
        String input = "&bBold Text";
        TextComponent result = TEXT_UTIL.parseFormattedString(input);

        TextComponent expected = Component.text("Bold Text", Style.style(TextDecoration.BOLD));
        assertEquals(expected, result);
    }

    @Test
    void parseFormattedString_ColorAndDecorationCodes_Formatted() {
        String input = "&(0,0,0)&bFormatted Text";
        TextColor color = TextColor.color(0, 0, 0);

        TextComponent result = TEXT_UTIL.parseFormattedString(input);
        TextComponent expected = Component.text("Formatted Text", color, TextDecoration.BOLD);

        assertEquals(expected, result);
    }

    @Test
    void parseFormattedString_ColorAndDecorationCodesNotAsFirstPattern_Formatted() {
        String input = "Some unformatted and some &(0,0,0)&bFormatted Text";
        TextColor color = TextColor.color(0, 0, 0);

        TextComponent result = TEXT_UTIL.parseFormattedString(input);
        TextComponent expected = Component.text("Some unformatted and some ")
                                          .append(Component.text("Formatted Text", color, TextDecoration.BOLD));

        assertEquals(expected, result);
    }

    @Test
    void parseFormattedString_ColorAndDecorationCodesWithReset_Formatted() {
        String input = "Some unformatted and some &(0,0,0)&bFormatted Text &rexcept this";
        TextColor color = TextColor.color(0, 0, 0);

        TextComponent expected = Component.text("Some unformatted and some ")
                                          .append(Component.text("Formatted Text ", color, TextDecoration.BOLD))
                                          .append(Component.text("except this", resetStyle()));

        TextComponent result = TEXT_UTIL.parseFormattedString(input);

        assertEquals(expected, result);
    }

    @Test
    void parseFormattedString_InvalidFormatCodes_PlainText() {
        String input = "&InvalidCode&Text";

        TextComponent result = TEXT_UTIL.parseFormattedString(input);

        TextComponent expected = Component.text(input);
        assertEquals(expected, result);
    }

    @Test
    void parseFormattedString_EmptyInput_ReturnsEmptyComponent() {
        assertEquals(Component.text(""), TEXT_UTIL.parseFormattedString(""));
    }

    @Test
    void parseFormattedString_FormatCodeWithoutText_ReturnsEmptyComponent() {
        assertEquals(Component.text(""), TEXT_UTIL.parseFormattedString("&b"));
        assertEquals(Component.empty(), TEXT_UTIL.parseFormattedString("&(12,34,56)&b&r&u"));
    }

    @Test
    void parseFormattedString_WhitespaceOnly_IsPreserved() {
        String whitespace = " \t\n ";

        assertEquals(Component.text(whitespace), TEXT_UTIL.parseFormattedString(whitespace));
        assertEquals(Component.text(whitespace).decorate(TextDecoration.BOLD), TEXT_UTIL.parseFormattedString("&b" + whitespace));
    }

    @Test
    void parseFormattedString_WhitespaceBetweenCodes_IsPreservedWithItsStyle() {
        TextComponent result = TEXT_UTIL.parseFormattedString(" &(12, 34, 56)\t &b \n&r  ");

        assertLegacyEquals(" §x§0§c§2§2§3§8\t §l \n§f  ", result);
    }

    @Test
    void parseFormattedString_StylesAccumulateAcrossSegmentsAndColorChanges() {
        TextComponent result = TEXT_UTIL.parseFormattedString("&(18,52,86)&bA&iB&(1,2,3)C&rD&uE");

        assertLegacyEquals("§x§1§2§3§4§5§6§lA§oB§x§0§1§0§2§0§3§l§oC§fD§nE", result);
    }

    @Test
    void parseFormattedString_AllDecorationsAreClearedByReset() {
        TextComponent result = TEXT_UTIL.parseFormattedString("&bB&sS&uU&iI&oO&rR");

        assertLegacyEquals("§lB§mS§nU§oI§kO§fR", result);
    }

    @Test
    void combineTextComponents_IndependentComponents_DoNotInheritFirstStyle() {
        TextComponent first = Component.text("A", NamedTextColor.RED, TextDecoration.BOLD);
        TextComponent second = Component.text("B");
        TextComponent third = Component.text("C", NamedTextColor.BLUE, TextDecoration.ITALIC);
        List<TextComponent> components = List.of(first, second, third);

        TextComponent result = TEXT_UTIL.combineTextComponents(components);

        assertLegacyEquals("§c§lA§rB§9§oC", result);
        assertEquals(List.of(first, second, third), components);
        assertEquals(Component.text("A", NamedTextColor.RED, TextDecoration.BOLD), first);
        assertEquals(Component.text("B"), second);
    }

    @Test
    void combineTextComponents_SingleComponent_RetainsItsTree() {
        TextComponent original = Component.text("A", NamedTextColor.RED)
                                          .append(Component.text("B", NamedTextColor.BLUE));

        assertSame(original, TEXT_UTIL.combineTextComponents(List.of(original)));
    }

    @Test
    void parseRGBComponent_InvalidComponent_NumberFormatExceptionThrown() {
        String invalidComponent = "invalid";
        assertThrows(IllegalArgumentException.class, () -> TEXT_UTIL.parseRGBComponent(invalidComponent));
    }

    @Test
    void repeatTextWithStyles_SingleRepetition_NoStylesChanged() {
        TextComponent originalComponent = Component.text("Hello")
                                                   .color(TextColor.color(255, 0, 0));
        int times = 1;

        TextComponent repeatedComponent = TEXT_UTIL.repeatTextWithStyles(originalComponent, times);

        assertEquals(originalComponent, repeatedComponent);
    }

    @Test
    void repeatTextWithStyles_MultiSegmentCounts_RepeatWholeComponent() {
        TextComponent original = Component.text("A", NamedTextColor.RED, TextDecoration.BOLD)
                                          .append(Component.text("B", resetStyle()));
        TextComponent snapshot = original.toBuilder().build();

        for (int times : new int[]{0, 1, 3}) {
            TextComponent repeated = TEXT_UTIL.repeatTextWithStyles(original, times);

            assertLegacyEquals("§c§lA§fB".repeat(times), repeated);
            assertEquals(snapshot, original);
        }
        assertEquals(Component.empty(), TEXT_UTIL.repeatTextWithStyles(original, 0));
        assertSame(original, TEXT_UTIL.repeatTextWithStyles(original, 1));
    }

    @Test
    void repeatTextWithStyles_NestedStyles_PreserveInheritanceAndOverrides() {
        TextComponent original = Component.text("A", NamedTextColor.RED, TextDecoration.BOLD)
                                          .append(Component.text("B", NamedTextColor.BLUE, TextDecoration.ITALIC)
                                                           .append(Component.text("C", TextColor.color(0x12, 0x34, 0x56))
                                                                            .decoration(TextDecoration.BOLD, false)))
                                          .append(Component.text("D"));
        TextComponent snapshot = original.toBuilder().build();

        TextComponent repeated = TEXT_UTIL.repeatTextWithStyles(original, 2);

        assertLegacyEquals(("§c§lA§9§l§oB§x§1§2§3§4§5§6§oC§c§lD").repeat(2), repeated);
        assertEquals(snapshot, original);
    }

    @Test
    void repeatTextWithStyles_EmptyRootAndTrailingStyles_DoNotLeakBetweenCopies() {
        TextComponent original = Component.text()
                                          .append(Component.text("A"))
                                          .append(Component.text("B", NamedTextColor.BLUE, TextDecoration.BOLD))
                                          .build();

        assertLegacyEquals("A§9§lB§rA§9§lB§rA§9§lB", TEXT_UTIL.repeatTextWithStyles(original, 3));
        assertEquals(Component.empty(), TEXT_UTIL.repeatTextWithStyles(original, 0));
    }

    @Test
    void repeatTextWithStyles_EmptyAndWhitespace_PreserveTextAndStyles() {
        assertEquals(Component.empty(), TEXT_UTIL.repeatTextWithStyles(Component.empty(), 4));
        assertLegacyEquals("§l \t\n \t\n \t\n", TEXT_UTIL.repeatTextWithStyles(Component.text(" \t\n").decorate(TextDecoration.BOLD), 3));
    }

    @Test
    void repeatTextWithStyles_NegativeRepetitions_ExceptionThrown() {
        TextComponent originalComponent = Component.text("A", NamedTextColor.RED)
                                                   .append(Component.text("B", NamedTextColor.BLUE));
        int times = -1;

        assertThrows(IllegalArgumentException.class, () -> TEXT_UTIL.repeatTextWithStyles(originalComponent, times));
    }

    @Test
    void serializeComponent_PreservesRgbColor() {
        Component component = Component.text("RGB", TextColor.color(0x12, 0x34, 0x56));

        assertEquals("§x§1§2§3§4§5§6RGB", TEXT_UTIL.serializeComponent(component));
    }

    @Test
    void serializeAndDeserialize_RoundTrip_PreservesRgbDecorationsResetAndWhitespace() {
        String legacy = "start §x§1§2§3§4§5§6§lRGB §oitalic§f \t\n§nunderlined";
        TextComponent formatted = TEXT_UTIL.parseFormattedString("start &(18,52,86)&bRGB &iitalic&r \t\n&uunderlined");

        assertLegacyEquals(legacy, formatted);

        String serialized = TEXT_UTIL.serializeComponent(formatted);
        Component deserialized = TEXT_UTIL.deserializeComponent(serialized);

        assertEquals(serialized, TEXT_UTIL.serializeComponent(deserialized));
        assertLegacyEquals(legacy, deserialized);
    }

    @Test
    void serializeAndDeserialize_EmptyAndNull_PreserveTheirContracts() {
        assertEquals("", TEXT_UTIL.serializeComponent(Component.empty()));
        assertEquals(Component.empty(), TEXT_UTIL.deserializeComponent(""));
        assertNull(TEXT_UTIL.serializeComponent(null));
        assertNull(TEXT_UTIL.deserializeComponent(null));
    }

    private void assertLegacyEquals(String expected, Component actual) {
        assertEquals(TEXT_UTIL.serializeComponent(TEXT_UTIL.deserializeComponent(expected)), TEXT_UTIL.serializeComponent(actual));
    }

    private Style resetStyle() {
        return Style.style()
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, false)
                    .decoration(TextDecoration.STRIKETHROUGH, false)
                    .decoration(TextDecoration.UNDERLINED, false)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.OBFUSCATED, false)
                    .build();
    }
}