package redis.embedded.model;

import redis.embedded.exceptions.OsArchitectureNotFound;

import java.io.IOException;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static redis.embedded.util.IO.streamToLines;

public enum Architecture {
    x86,
    x86_64;

    public static Architecture detectWindowsArchitecture() {
        final String arch = System.getenv("PROCESSOR_ARCHITECTURE");
        final String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");

        return isWindows64Bit(arch, wow64Arch) ? x86_64 : x86;
    }

    public static Architecture detectUnixArchitecture() {
        return find64BitInProcess("uname -m", Architecture::isUnix64Bit);
    }

    public static Architecture detectMacOSXArchitecture() {
        return find64BitInProcess("sysctl hw", Architecture::isMacOS64Bit);
    }

    private static Architecture find64BitInProcess(final String command, final Predicate<String> filter) {
        try {
            final Process proc = Runtime.getRuntime().exec(command);
            try (final Stream<String> lines = streamToLines(proc.getInputStream())) {
                return lines.filter(filter).map(s -> x86_64).findFirst().orElse(x86);
            }
        } catch (IOException e) {
            throw new OsArchitectureNotFound(e);
        }
    }

    private static boolean isWindows64Bit(final String arch, final String wow64Arch) {
        return arch.endsWith("64") || wow64Arch != null && wow64Arch.endsWith("64");
    }

    private static boolean isUnix64Bit(final String unameLine) {
        return !unameLine.isEmpty() && unameLine.contains("64");
    }

    private static boolean isMacOS64Bit(final String sysctlLine) {
        return !sysctlLine.isEmpty() && sysctlLine.contains("cpu64bit_capable") && sysctlLine.trim().endsWith("1");
    }

}
