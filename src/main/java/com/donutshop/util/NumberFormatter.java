package com.donutshop.util;

/**
 * Formats numeric values (prices, balances) into human-readable shorthand notation.
 *
 * Examples:
 *   500        →  "500"
 *   1500       →  "1.5k"
 *   5000000    →  "5m"
 *   2500000000 →  "2.5b"
 *   1e12       →  "1t"
 *
 * Suffixes: k (thousand), m (million), b (billion), t (trillion)
 */
public final class NumberFormatter {

    private NumberFormatter() {}

    /**
     * Format a value using shorthand suffixes, stripping unnecessary trailing zeros.
     *
     * @param value the numeric value to format
     * @return human-readable shorthand string
     */
    public static String format(double value) {
        if (value >= 1_000_000_000_000.0) {
            return withSuffix(value / 1_000_000_000_000.0, "t");
        } else if (value >= 1_000_000_000.0) {
            return withSuffix(value / 1_000_000_000.0, "b");
        } else if (value >= 1_000_000.0) {
            return withSuffix(value / 1_000_000.0, "m");
        } else if (value >= 1_000.0) {
            return withSuffix(value / 1_000.0, "k");
        } else {
            // Below 1 000: show up to 2 decimal places, strip trailing zeros
            return stripZeros(String.format("%.2f", value));
        }
    }

    private static String withSuffix(double value, String suffix) {
        // 1 decimal place; if it ends with ".0" strip it (e.g. "5.0m" → "5m")
        String s = String.format("%.1f", value);
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s + suffix;
    }

    /** Remove a trailing ".00" or ".0" from a decimal string. */
    private static String stripZeros(String s) {
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }
}
