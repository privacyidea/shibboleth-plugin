package org.privacyidea.context;

public class StringUtil
{
    public StringUtil() {}

    public static boolean isBlank(String str)
    {
        return !isNotBlank(str);
    }

    public static boolean isNotBlank(String str)
    {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * Replace line breaks and control characters (CR, LF, tab, ...) in a value before it is logged, so that a
     * value taken from a form field always stays on its own log line.
     *
     * @param value the value to log, may be null
     * @return the value with every line break and control character replaced by an underscore, or "null"
     */
    public static String sanitizeForLog(String value)
    {
        // \R matches every line break sequence (a CRLF pair counts as one, and it also covers the Unicode line
        // separators that the ASCII-only \p{Cntrl} does not), then \p{Cntrl} catches the remaining control chars.
        return value == null ? "null" : value.replaceAll("\\R", "_").replaceAll("\\p{Cntrl}", "_");
    }
}