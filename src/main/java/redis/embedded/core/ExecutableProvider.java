package redis.embedded.core;

import redis.embedded.error.OsArchitectureNotFound;
import redis.embedded.model.OsArchitecture;
import redis.embedded.util.IO;
import redis.embedded.util.IOSupplier;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.file.Files.*;
import static java.nio.file.StandardCopyOption.*;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.attribute.PosixFilePermissions.fromString;
import static redis.embedded.model.OsArchitecture.*;
import static redis.embedded.util.IO.*;

public interface ExecutableProvider {

    String ENVIRONMENT_EXECUTABLE_LOCATION = "EMBEDDED_REDIS_EXECUTABLE";
    String PROPERTY_EXECUTABLE_LOCATION = "embedded.redis.executable";

    // Socket timeouts for downloads: a stalled connection must not hang the caller forever
    int DOWNLOAD_TIMEOUT_MILLIS = 60000;

    URI
        // Downloaded from https://github.com/zkteco-home/redis-windows/raw/master/redis-server.exe
        REDIS_7_2_5_WINDOWS_ZKTECO = URI.create("https://github.com/codemonstur/embedded-redis/raw/master/src/main/binaries/redis-server-7.2.5-windows"),
        REDIS_8_8_0_WINDOWS_ZKTECO = URI.create("https://github.com/zkteco-home/redis-windows/raw/refs/tags/8.8.0/redis-server.exe"),
        REDIS_8_10_1_WINDOWS_ZKTECO = URI.create("https://github.com/zkteco-home/redis-windows/raw/refs/tags/8.10.1/redis-server.exe"),
        REDIS_7_2_MACOSX_14_SONOMA_HANKCP = URI.create("https://github.com/codemonstur/embedded-redis/raw/master/src/main/binaries/redis-server-7.2-darwin-arm64"),
        // Downloaded from: https://packages.redis.io/redis-stack/redis-stack-server-7.2.0-v6.jammy.x86_64.tar.gz
        REDIS_7_2_LINUX_JAMMY_X86_64 = URI.create("https://github.com/codemonstur/embedded-redis/raw/master/src/main/binaries/redis-stack-server-7.2.0-v6.jammy.x86_64"),
        // Downloaded from: https://packages.redis.io/redis-stack/redis-stack-server-7.2.0-v6.jammy.arm64.tar.gz
        REDIS_7_2_LINUX_JAMMY_ARM_64 = URI.create("https://github.com/codemonstur/embedded-redis/raw/master/src/main/binaries/redis-stack-server-7.2.0-v6.jammy.arm64");

    File get() throws IOException;

    static ExecutableProvider newJarResourceProvider() {
        final Map<OsArchitecture, String> map = newProvidedVersionsMap();
        return newJarResourceProvider(IO::newTempDirForBinary, map);
    }

    static ExecutableProvider newJarResourceProvider(final File tempDirectory) {
        final Map<OsArchitecture, String> map = newProvidedVersionsMap();
        return newJarResourceProvider(() -> tempDirectory, map);
    }
    static ExecutableProvider newJarResourceProvider(final IOSupplier<File> tempDirectorySupplier) {
        final Map<OsArchitecture, String> map = newProvidedVersionsMap();
        return newJarResourceProvider(tempDirectorySupplier, map);
    }

    static ExecutableProvider newJarResourceProvider(final Map<OsArchitecture, String> executables) {
        return newJarResourceProvider(IO::newTempDirForBinary, executables);
    }

    static ExecutableProvider newJarResourceProvider(final IOSupplier<File> tempDirectory, final Map<OsArchitecture, String> executables) {
        final OsArchitecture osArch = detectOSandArchitecture();
        return () -> {
            final String executablePath = executables.get(osArch);
            if (executablePath == null)
                throw new OsArchitectureNotFound(new FileNotFoundException(
                    "No Redis executable is provided for " + osArch.os + " " + osArch.arch));
            return writeResourceToExecutableFile(tempDirectory.get(), executablePath);
        };
    }

    static ExecutableProvider newFileThenJarResourceProvider(final Map<OsArchitecture, String> executables) {
        return () -> {
            final OsArchitecture osArch = detectOSandArchitecture();
            final String executablePath = executables.get(osArch);
            if (executablePath == null)
                throw new OsArchitectureNotFound(new FileNotFoundException(
                    "No Redis executable is provided for " + osArch.os + " " + osArch.arch));
            final File executable = new File(executablePath);
            return executable.isFile()
                ? executable
                : writeResourceToExecutableFile(newTempDirForBinary(), executablePath);
        };
    }

    static ExecutableProvider newEnvironmentVariableProvider() {
        return newEnvironmentVariableProvider(ENVIRONMENT_EXECUTABLE_LOCATION);
    }
    static ExecutableProvider newEnvironmentVariableProvider(final String envName) {
        return () -> new File(System.getenv(envName));
    }

    static ExecutableProvider newSystemPropertyProvider() {
        return newSystemPropertyProvider(PROPERTY_EXECUTABLE_LOCATION);
    }
    static ExecutableProvider newSystemPropertyProvider(final String propertyName) {
        return () -> new File(System.getProperty(propertyName));
    }

    static ExecutableProvider newExecutableInPath(final String executableName) throws FileNotFoundException {
        return findBinaryInPath(executableName)::toFile;
    }

    static ExecutableProvider newCachedUrlProvider(final Path cachedLocation, final URI uri) {
        return () -> {
            if (isRegularFile(cachedLocation))
                return cachedLocation.toFile();

            final HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(DOWNLOAD_TIMEOUT_MILLIS);
            connection.setReadTimeout(DOWNLOAD_TIMEOUT_MILLIS);
            Path partial = null;
            try {
                if (connection.getResponseCode() != HTTP_OK)
                    throw new IOException("Failed to download redis binary from " + uri + ", status code is " + connection.getResponseCode());

                createDirectories(cachedLocation.getParent());
                partial = createTempFile(cachedLocation.getParent(), cachedLocation.getFileName().toString(), ".part");
                try (final OutputStream out = newOutputStream(partial, CREATE, WRITE, TRUNCATE_EXISTING);
                     final InputStream in = connection.getInputStream()) {

                    final byte[] buffer = new byte[8192];
                    int length; while ((length = in.read(buffer)) != -1) {
                        out.write(buffer, 0, length);
                    }
                }
                partial.toFile().setExecutable(true);
                // Moved into place only once complete, so an interrupted download never poisons the cache
                try {
                    move(partial, cachedLocation, ATOMIC_MOVE);
                } catch (final AtomicMoveNotSupportedException e) {
                    move(partial, cachedLocation, REPLACE_EXISTING);
                }

                return cachedLocation.toFile();
            } finally {
                connection.disconnect();
                if (partial != null) {
                    try {
                        deleteIfExists(partial);
                    } catch (final IOException ignored) {}
                }
            }
        };
    }

    static Map<OsArchitecture, String> newProvidedVersionsMap() {
        final Map<OsArchitecture, String> map = new HashMap<>();
        map.put(UNIX_x86, "/redis-server-6.2.7-linux-386");
        map.put(UNIX_x86_64, "/redis-server-6.2.6-v5-linux-amd64");
        map.put(UNIX_AARCH64, "/redis-server-6.2.7-linux-arm64");
        map.put(WINDOWS_x86_64, "/redis-server-5.0.14.1-windows-amd64.exe");
        map.put(MAC_OS_X_x86_64, "/redis-server-6.2.6-v5-darwin-amd64");
        map.put(MAC_OS_X_ARM64, "/redis-server-6.2.6-v5-darwin-arm64");
        return map;
    }

}
