package build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Prints the coverage totals of the jacoco.csv report. Run with the single-file source launcher:
 *  java src/build/CoverageSummary.java target/site/jacoco/jacoco.csv */
public class CoverageSummary {

    private static final String[][] COUNTERS = {
        {"INSTRUCTION", "instructions"},
        {"BRANCH", "branches"},
        {"LINE", "lines"},
    };

    /** The entry point. */
    public static void main(final String[] args) throws Exception {
        final var report = Path.of(args.length > 0 ? args[0] : "target/site/jacoco/jacoco.csv");
        if (!Files.exists(report)) {
            System.out.println("No coverage data in " + report);
            return;
        }
        final var lines = Files.readAllLines(report);
        if (lines.size() < 2) {
            System.out.println("No coverage data in " + report);
            return;
        }

        final var columns = lines.get(0).split(",", -1);
        for (final var counter : COUNTERS) {
            final var missed = sum(lines, columns, counter[0] + "_MISSED");
            final var covered = sum(lines, columns, counter[0] + "_COVERED");
            final var total = missed + covered;
            System.out.printf("%-13s%5.1f%% (%d/%d)%n", counter[1], 100.0 * covered / total, covered, total);
        }
    }

    private static long sum(final List<String> lines, final String[] columns, final String name) {
        final var column = column(columns, name);
        long sum = 0;
        for (final var line : lines.stream().skip(1).toList()) {
            sum += Long.parseLong(line.split(",", -1)[column]);
        }
        return sum;
    }

    private static int column(final String[] columns, final String name) {
        var index = 0;
        for (final var column : columns) {
            if (column.equals(name)) return index;
            index++;
        }
        throw new IllegalStateException("Missing CSV column " + name);
    }

}
