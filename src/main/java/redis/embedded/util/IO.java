package redis.embedded.util;

import java.io.*;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public enum IO {;

    public static File writeResourceToExecutableFile(final String resourcePath) throws IOException {
        final File tempDirectory = createDirectories(createTempDirectory("redis-")).toFile();
        tempDirectory.deleteOnExit();

        final File executable = new File(tempDirectory, resourcePath);
        try (final InputStream in = IO.class.getResourceAsStream(resourcePath)) {
            Files.copy(in, executable.toPath(), REPLACE_EXISTING);
        }
        executable.deleteOnExit();
        if (!executable.setExecutable(true))
            throw new IOException("Failed to set executable permission for binary " + resourcePath + " at temporary location " + executable);
        return executable;
    }

    public static Runnable checkedToRuntime(final CheckedRunnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static void addShutdownHook(final String name, final Runnable run) {
        Runtime.getRuntime().addShutdownHook(new Thread(run, name));
    }

    public static void logStream(final InputStream stream, final Consumer<String> logConsumer) {
        new Thread(() -> {
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line; while ((line = reader.readLine()) != null) {
                    logConsumer.accept(line);
                }
            } catch (IOException e) { /* eat quietly */ }
        }).start();
    }

    public static boolean findMatchInStream(final InputStream in, final Pattern pattern,
                                            final StringBuilder processOutput) throws IOException {
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line; while ((line = reader.readLine()) != null) {
                if (pattern.matcher(line).matches())
                    return true;
                processOutput.append("\n").append(line);
            }
        }
        return false;
    }

    public static Stream<String> processToLines(final String command) throws IOException {
        final Process proc = Runtime.getRuntime().exec(command);
        return new BufferedReader(new InputStreamReader(proc.getInputStream())).lines();
    }

}
