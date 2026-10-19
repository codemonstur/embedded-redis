package redis.embedded.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public enum IO {;

    // Daemon threads used to read process output with a deadline; a binary that never prints its ready-line
    // would otherwise block the reader forever.
    private static final ExecutorService STREAM_READERS = Executors.newCachedThreadPool(run -> {
        final Thread thread = new Thread(run, "embedded-redis-stream-reader");
        thread.setDaemon(true);
        return thread;
    });

    public static File newTempDirForBinary() throws IOException {
        final File tempDirectory = createDirectories(createTempDirectory("redis-")).toFile();
        tempDirectory.deleteOnExit();
        return tempDirectory;
    }

    public static File writeResourceToExecutableFile(final File tempDirectory, final String resourcePath) throws IOException {
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
                if (logConsumer != null) logConsumer.accept(line);
            }
        } catch (final IOException ignored) {}
    }

    public static Thread newDaemonThread(final Runnable run) {
        final Thread thread = new Thread(run);
        thread.setDaemon(true);
        return thread;
    }

    public static boolean findMatchInStream(final InputStream in, final Pattern pattern
            , final Consumer<String> soutListener, final StringBuilder processOutput) throws IOException {
        // Deliberately not closed on a successful match: the caller keeps reading the process output after
        // the ready-line, and closing the stream here would both silence the listener and fill up the pipe
        final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line; while ((line = reader.readLine()) != null) {
            if (soutListener != null) soutListener.accept(line);
            processOutput.append('\n').append(line);
            if (pattern.matcher(line).matches())
                return true;
        }
        closeQuietly(reader);
        return false;
    }

    /**
     * Same as {@link #findMatchInStream(InputStream, Pattern, Consumer, StringBuilder)}, but gives up after the
     * given timeout instead of blocking until the stream ends. Returns false on timeout, and closes the stream
     * so the reader thread stops blocking on it.
     */
    public static boolean findMatchInStream(final InputStream in, final Pattern pattern
            , final Consumer<String> soutListener, final StringBuilder processOutput, final long timeoutMs)
            throws IOException {
        final Future<Boolean> match = STREAM_READERS.submit(
            () -> findMatchInStream(in, pattern, soutListener, processOutput));
        try {
            return match.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            match.cancel(true);
            closeQuietly(in);
            return false;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for a match on the process output", e);
        } catch (final ExecutionException e) {
            throw new IOException("Failed to read the process output", e.getCause());
        }
    }

    private static void closeQuietly(final Closeable closeable) {
        try {
            closeable.close();
        } catch (final IOException ignored) {}
    }

    public static String readFully(final InputStream in, final Consumer<String> listener) {
        final StringBuilder ret = new StringBuilder();
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line; while ((line = reader.readLine()) != null) {
                if (listener != null) listener.accept(line);
                ret.append(line);
            }
        } catch (final IOException ignored) {}
        return ret.toString();
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
