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
     * Replace control characters (CR, LF, tab, ...) in a value before it is logged, so that a value taken from
     * a form field always stays on its own log line.
     *
     * @param value the value to log, may be null
     * @return the value with every control character replaced by an underscore, or "null"
     */
    public static String sanitizeForLog(String value)
    {
        return value == null ? "null" : value.replaceAll("\\p{Cntrl}", "_");
    }
}