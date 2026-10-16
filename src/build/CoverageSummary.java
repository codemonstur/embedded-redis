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

    public static void main(final String[] args) throws Exception {
        final Path report = Path.of(args.length > 0 ? args[0] : "target/site/jacoco/jacoco.csv");
        if (!Files.exists(report)) {
            System.out.println("No coverage data in " + report);
            return;
        }
        final List<String> lines = Files.readAllLines(report);
        if (lines.size() < 2) {
            System.out.println("No coverage data in " + report);
            return;
        }

        final String[] columns = lines.get(0).split(",", -1);
        for (final String[] counter : COUNTERS) {
            final long missed = sum(lines, columns, counter[0] + "_MISSED");
            final long covered = sum(lines, columns, counter[0] + "_COVERED");
            final long total = missed + covered;
            System.out.printf("%-13s%5.1f%% (%d/%d)%n", counter[1], 100.0 * covered / total, covered, total);
        }
    }

    private static long sum(final List<String> lines, final String[] columns, final String name) {
        final int column = column(columns, name);
        long sum = 0;
        for (int i = 1; i < lines.size(); i++) sum += Long.parseLong(lines.get(i).split(",", -1)[column]);
        return sum;
    }

    private static int column(final String[] columns, final String name) {
        for (int i = 0; i < columns.length; i++) if (columns[i].equals(name)) return i;
        throw new IllegalStateException("Missing CSV column " + name);
    }

}
