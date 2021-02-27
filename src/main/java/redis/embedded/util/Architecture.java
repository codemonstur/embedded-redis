package redis.embedded.util;

import redis.embedded.exceptions.OsDetectionException;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public enum Architecture {
    x86,
    x86_64;

    public static Architecture detectWindowsArchitecture() {
        String arch = System.getenv("PROCESSOR_ARCHITECTURE");
        String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");

        if (arch.endsWith("64") || wow64Arch != null && wow64Arch.endsWith("64")) {
            return Architecture.x86_64;
        } else {
            return Architecture.x86;
        }
    }

    public static Architecture detectUnixArchitecture() {
        BufferedReader input = null;
        try {
            String line;
            Process proc = Runtime.getRuntime().exec("uname -m");
            input = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            while ((line = input.readLine()) != null) {
                if (line.length() > 0) {
                    if (line.contains("64")) {
                        return Architecture.x86_64;
                    }
                }
            }
        } catch (Exception e) {
            throw new OsDetectionException(e);
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        return Architecture.x86;
    }

    public static Architecture detectMacOSXArchitecture() {
        BufferedReader input = null;
        try {
            String line;
            Process proc = Runtime.getRuntime().exec("sysctl hw");
            input = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            while ((line = input.readLine()) != null) {
                if (line.length() > 0) {
                    if ((line.contains("cpu64bit_capable")) && (line.trim().endsWith("1"))) {
                        return Architecture.x86_64;
                    }
                }
            }
        } catch (Exception e) {
            throw new OsDetectionException(e);
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        return Architecture.x86;
    }

}
