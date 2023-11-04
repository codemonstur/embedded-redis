package redis.embedded.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
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
            if (in == null) throw new FileNotFoundException("Could not find Redis executable at " + resourcePath);
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
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static void addShutdownHook(final String name, final Runnable run) {
        Runtime.getRuntime().addShutdownHook(new Thread(run, name));
    }

    public static void logStream(final InputStream stream, final Consumer<String> logConsumer) {
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line; while ((line = reader.readLine()) != null) {
                logConsumer.accept(line);
            }
        } catch (final IOException ignored) {}
    }

    public static Thread newDaemonThread(final Runnable run) {
        final Thread thread = new Thread(run);
        thread.setDaemon(true);
        return thread;
    }

    public static boolean findMatchInStream(final InputStream in, final Pattern pattern,
                                            final StringBuilder processOutput) throws IOException {
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line; while ((line = reader.readLine()) != null) {
                if (pattern.matcher(line).matches())
                    return true;
                processOutput.append('\n').append(line);
            }
        }
        return false;
    }

    public static Stream<String> processToLines(final String command) throws IOException {
        final Process proc = Runtime.getRuntime().exec(command);
        return new BufferedReader(new InputStreamReader(proc.getInputStream())).lines();
    }

    public static Path findBinaryInPath(final String name) throws FileNotFoundException {
        return findBinaryInPath(name, System.getenv("PATH"));
    }

    private static Path findBinaryInPath(final String name, final String pathVar) throws FileNotFoundException {
        final Optional<Path> location = Stream.of(pathVar
            .split(Pattern.quote(File.pathSeparator)))
            .map(Paths::get)
            .map(path -> path.resolve(name))
            .filter(Files::isRegularFile)
            .findAny();
        if (!location.isPresent()) throw new FileNotFoundException("Could not find binary '" + name + "' in PATH");
        return location.get();
    }

}
