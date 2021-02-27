package redis.embedded.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class IOUtils {

    public static void addShutdownHook(final String name, final Runnable run) {
        Runtime.getRuntime().addShutdownHook(new Thread(run, name));
    }

    public static void logStream(final InputStream stream, final Consumer<String> logConsumer) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
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


}
