package redis.embedded.model;

import redis.embedded.error.OsArchitectureNotFound;

import java.io.IOException;
import java.util.stream.Stream;

import static redis.embedded.util.IO.processToLines;

public enum Architecture {
    x86,
    x86_64,
    aarch64;

    public static Architecture detectWindowsArchitecture() {
        final String arch = System.getenv("PROCESSOR_ARCHITECTURE");
        final String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");

        return isWindows64Bit(arch, wow64Arch) ? x86_64 : x86;
    }

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
