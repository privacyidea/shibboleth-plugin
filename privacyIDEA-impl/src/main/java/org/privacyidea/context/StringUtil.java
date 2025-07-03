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
}