package redis.embedded.model;

import redis.embedded.error.OsArchitectureNotFound;

import java.io.IOException;
import java.util.function.Function;
import java.util.stream.Stream;

import static redis.embedded.util.IO.streamToLines;

public enum Architecture {
    x86,
    x86_64,
    aarch64;

    public static Architecture detectWindowsArchitecture() {
        final String arch = System.getenv("PROCESSOR_ARCHITECTURE");
        final String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");

        return getWindowsArchitecture(arch, wow64Arch);
    }

    public static Architecture detectUnixArchitecture() {
        return find64BitInProcess("uname -m", Architecture::getUnixArchitecture);
    }

    public static Architecture detectMacOSXArchitecture() {
        return find64BitInProcess("sysctl hw", Architecture::getMacOsArchitecture);
    }

    private static Architecture find64BitInProcess(final String command, final Function<String, Architecture> mapper) {
        try {
            final Process proc = Runtime.getRuntime().exec(command);
            try (final Stream<String> lines = streamToLines(proc.getInputStream())) {
                return lines.map(mapper).findFirst().orElse(x86);
            }
        } catch (IOException e) {
            throw new OsArchitectureNotFound(e);
        }
    }

    private static Architecture getWindowsArchitecture(final String arch, final String wow64Arch) {
        return (arch.endsWith("64") || wow64Arch != null && wow64Arch.endsWith("64"))
                ? x86_64
                : x86;
    }

    private static Architecture getUnixArchitecture(final String unameLine) {
        if (unameLine != null) {
            if (unameLine.contains("aarch64")) {
                return aarch64;
            } else if (unameLine.contains("64")) {
                return x86_64;
            }
        }
        return Architecture.x86;
    }

    private static Architecture getMacOsArchitecture(final String sysctlLine) {
        return (!sysctlLine.isEmpty() && sysctlLine.contains("cpu64bit_capable") && sysctlLine.trim().endsWith("1"))
               ? x86_64
               : x86;
    }

}
