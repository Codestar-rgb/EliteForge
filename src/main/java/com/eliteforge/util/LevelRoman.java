package com.eliteforge.util;

/**
 * Utility class for converting ability levels (1-5+) to Roman numerals.
 * Used for display in tooltips, name plates, and UI elements.
 *
 * Supports standard Roman numerals up to level 10, with Unicode
 * numeral characters for levels 1-5 (Ⅰ-Ⅴ) for aesthetic consistency.
 */
public final class LevelRoman {

    private static final String[] ROMAN_UNICODE = {
            "",     // 0 - unused
            "Ⅰ",   // 1
            "Ⅱ",   // 2
            "Ⅲ",   // 3
            "Ⅳ",   // 4
            "Ⅴ"    // 5
    };

    private static final String[] ROMAN_ASCII = {
            "",         // 0 - unused
            "I",        // 1
            "II",       // 2
            "III",      // 3
            "IV",       // 4
            "V",        // 5
            "VI",       // 6
            "VII",      // 7
            "VIII",     // 8
            "IX",       // 9
            "X",        // 10
            "XI",       // 11
            "XII",      // 12
            "XIII",     // 13
            "XIV",      // 14
            "XV",       // 15
            "XVI",      // 16
            "XVII",     // 17
            "XVIII",    // 18
            "XIX",      // 19
            "XX"        // 20
    };

    private LevelRoman() {
        // Utility class, no instantiation
    }

    /**
     * Convert a level number to a Unicode Roman numeral string.
     * Uses the Unicode numeral characters (Ⅰ-Ⅴ) for levels 1-5.
     *
     * @param level The ability level (1-5)
     * @return The Unicode Roman numeral string, or the number as string for levels > 5
     */
    public static String toUnicode(int level) {
        if (level >= 1 && level < ROMAN_UNICODE.length) {
            return ROMAN_UNICODE[level];
        }
        return toAscii(level);
    }

    /**
     * Convert a level number to an ASCII Roman numeral string.
     * Uses standard Latin characters (I, II, III, IV, V, etc.) for levels 1-20.
     *
     * @param level The ability level (1-20)
     * @return The ASCII Roman numeral string, or the number as string for levels > 20
     */
    public static String toAscii(int level) {
        if (level >= 1 && level < ROMAN_ASCII.length) {
            return ROMAN_ASCII[level];
        }
        return String.valueOf(level);
    }

    /**
     * Convert a level number to a Roman numeral string.
     * Defaults to Unicode Roman numerals for levels 1-5, ASCII for 6-10.
     * This is the primary method used for display.
     *
     * @param level The ability level
     * @return The formatted Roman numeral string
     */
    public static String format(int level) {
        if (level <= 0) {
            return "";
        }
        if (level <= 5) {
            return toUnicode(level);
        }
        return toAscii(level);
    }

    /**
     * Format a level with a space prefix for display after ability names.
     * Example: formatSuffix(3) returns " Ⅲ"
     *
     * @param level The ability level
     * @return The formatted Roman numeral with leading space
     */
    public static String formatSuffix(int level) {
        String numeral = format(level);
        return numeral.isEmpty() ? "" : " " + numeral;
    }

    /**
     * Parse a Roman numeral string back to an integer.
     * Supports both Unicode and ASCII Roman numerals.
     *
     * @param roman The Roman numeral string
     * @return The integer value, or 0 if parsing fails
     */
    public static int parse(String roman) {
        if (roman == null || roman.isEmpty()) {
            return 0;
        }

        // Try Unicode lookup first
        for (int i = 1; i < ROMAN_UNICODE.length; i++) {
            if (ROMAN_UNICODE[i].equals(roman)) {
                return i;
            }
        }

        // Try ASCII lookup
        for (int i = 1; i < ROMAN_ASCII.length; i++) {
            if (ROMAN_ASCII[i].equalsIgnoreCase(roman)) {
                return i;
            }
        }

        // Try parsing as integer
        try {
            return Integer.parseInt(roman);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
