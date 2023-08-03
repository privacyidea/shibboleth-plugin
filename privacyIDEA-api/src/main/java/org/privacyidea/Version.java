package org.privacyidea;

import javax.annotation.Nullable;

/**
 * Class for getting and printing the version of the plugin.
 */
public final class Version
{
    /**
     * IdP plugin version.
     */
    @Nullable
    private static final String VERSION = Version.class.getPackage().getImplementationVersion();

    /**
     * Constructor.
     */
    private Version()
    {
    }

    /**
     * Main entry point to program.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args)
    {
        System.out.println(VERSION);
    }

    /**
     * Get the version of the plugin.
     *
     * @return version of the plugin
     */
    @Nullable
    public static String getVersion()
    {
        return VERSION;
    }
}