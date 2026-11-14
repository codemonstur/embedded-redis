package redis.embedded.model;

import redis.embedded.error.UnknownOSName;

import java.util.function.Supplier;

/**
 * The {@code OS} enum.
 */
public enum OS {
    /**
     * The {@code WINDOWS} constant.
     */
    WINDOWS(Architecture::detectWindowsArchitecture),
    /**
     * The {@code UNIX} constant.
     */
    UNIX(Architecture::detectUnixMacOSXArchitecture),
    /**
     * The {@code MAC_OS_X} constant.
     */
    MAC_OS_X(Architecture::detectUnixMacOSXArchitecture);

    private final Supplier<Architecture> archSupplier;
    OS(final Supplier<Architecture> archSupplier) {
        this.archSupplier = archSupplier;
    }

    /**
     * Returns the result of {@code detectArchitecture}.
     *
     * @return the result
     */
    public Architecture detectArchitecture() {
        return archSupplier.get();
    }

    /**
     * Returns the result of {@code detectOS}.
     *
     * @return the result
     */
    public static OS detectOS() {
        final String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win"))
            return OS.WINDOWS;
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix"))
            return OS.UNIX;
        if ("Mac OS X".equalsIgnoreCase(osName))
            return OS.MAC_OS_X;

        throw new UnknownOSName(osName);
    }

}
