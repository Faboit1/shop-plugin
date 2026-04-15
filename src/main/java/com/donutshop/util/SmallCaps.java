package com.donutshop.util;

public final class SmallCaps {

    private static final char[] SMALL_CAPS = new char[26];

    static {
        // A-Z mapping to Unicode small capitals
        SMALL_CAPS[0]  = '\u1D00'; // ᴀ
        SMALL_CAPS[1]  = '\u0299'; // ʙ
        SMALL_CAPS[2]  = '\u1D04'; // ᴄ
        SMALL_CAPS[3]  = '\u1D05'; // ᴅ
        SMALL_CAPS[4]  = '\u1D07'; // ᴇ
        SMALL_CAPS[5]  = '\u0493'; // ғ
        SMALL_CAPS[6]  = '\u0262'; // ɢ
        SMALL_CAPS[7]  = '\u029C'; // ʜ
        SMALL_CAPS[8]  = '\u026A'; // ɪ
        SMALL_CAPS[9]  = '\u1D0A'; // ᴊ
        SMALL_CAPS[10] = '\u1D0B'; // ᴋ
        SMALL_CAPS[11] = '\u029F'; // ʟ
        SMALL_CAPS[12] = '\u1D0D'; // ᴍ
        SMALL_CAPS[13] = '\u0274'; // ɴ
        SMALL_CAPS[14] = '\u1D0F'; // ᴏ
        SMALL_CAPS[15] = '\u1D18'; // ᴘ
        SMALL_CAPS[16] = '\u01EB'; // ǫ
        SMALL_CAPS[17] = '\u0280'; // ʀ
        SMALL_CAPS[18] = 's';      // s (no small cap available)
        SMALL_CAPS[19] = '\u1D1B'; // ᴛ
        SMALL_CAPS[20] = '\u1D1C'; // ᴜ
        SMALL_CAPS[21] = '\u1D20'; // ᴠ
        SMALL_CAPS[22] = '\u1D21'; // ᴡ
        SMALL_CAPS[23] = 'x';      // x (no small cap available)
        SMALL_CAPS[24] = '\u028F'; // ʏ
        SMALL_CAPS[25] = '\u1D22'; // ᴢ
    }

    private SmallCaps() {}

    /**
     * Converts all ASCII letters in the input to their Unicode small-cap equivalents.
     * Non-letter characters pass through unchanged.
     */
    public static String convert(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                sb.append(SMALL_CAPS[c - 'A']);
            } else if (c >= 'a' && c <= 'z') {
                sb.append(SMALL_CAPS[c - 'a']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Converts text to small caps while preserving Minecraft color codes.
     * Characters immediately following § or &amp; are kept as-is.
     */
    public static String convertWithColor(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '\u00A7' || c == '&') && i + 1 < input.length()) {
                // Color code prefix — keep prefix and the following code character unchanged
                sb.append(c);
                sb.append(input.charAt(i + 1));
                i++;
            } else if (c >= 'A' && c <= 'Z') {
                sb.append(SMALL_CAPS[c - 'A']);
            } else if (c >= 'a' && c <= 'z') {
                sb.append(SMALL_CAPS[c - 'a']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
