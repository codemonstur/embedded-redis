package redis.embedded.model;

import redis.embedded.error.OsArchitectureNotFound;

import java.io.IOException;
import java.util.stream.Stream;

import static redis.embedded.util.IO.processToLines;

/**
 * The {@code Architecture} enum.
 */
public enum Architecture {
    /**
     * The {@code x86} constant.
     */
    x86,
    /**
     * The {@code x86_64} constant.
     */
    x86_64,
    /**
     * The {@code aarch64} constant.
     */
    aarch64;

    /**
     * Returns the result of {@code detectWindowsArchitecture}.
     *
     * @return the result
     */
    public static Architecture detectWindowsArchitecture() {
        final String arch = System.getenv("PROCESSOR_ARCHITECTURE");
        final String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");

        return isWindows64Bit(arch, wow64Arch) ? x86_64 : x86;
    }

    /**
     * Returns the result of {@code detectUnixMacOSXArchitecture}.
     *
     * @return the result
     */
    public static Architecture detectUnixMacOSXArchitecture() {
        try (final Stream<String> lines = processToLines("uname -m")) {
            return lines.filter(Architecture::isUnix64Bit)
                .map(line -> line.contains("aarch64") || line.contains("arm64") ? aarch64:  x86_64)
                .findFirst().orElse(x86);
        } catch (IOException e) {
            throw new OsArchitectureNotFound(e);
        }
    }

    private static boolean isWindows64Bit(final String arch, final String wow64Arch) {
        return arch.endsWith("64") || wow64Arch != null && wow64Arch.endsWith("64");
    }

    private static boolean isUnix64Bit(final String line) {
        return !line.isEmpty() && line.contains("64");
    }

}
