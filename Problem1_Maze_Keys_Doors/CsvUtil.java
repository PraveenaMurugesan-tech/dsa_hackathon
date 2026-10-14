import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Minimal, forgiving CSV I/O: one character per cell, comma separated. */
public final class CsvUtil {
    private CsvUtil() {}

    public static char[][] read(String path) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        List<char[]> rows = new ArrayList<>();
        int width = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0 && line.startsWith("\uFEFF")) line = line.substring(1); // BOM
            if (line.trim().isEmpty()) continue;

            List<String> cells = new ArrayList<>();
            for (String p : line.split(",", -1)) cells.add(p.trim().replace("\"", ""));
            while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty())
                cells.remove(cells.size() - 1); // trailing commas

            char[] row;
            if (cells.size() == 1 && cells.get(0).length() > 1) {
                row = cells.get(0).toCharArray(); // tolerate "S..#E" style rows without commas
            } else {
                row = new char[cells.size()];
                for (int c = 0; c < row.length; c++) {
                    String cell = cells.get(c);
                    if (cell.length() != 1)
                        throw new IllegalArgumentException("Line " + (i + 1) + ", column " + (c + 1)
                                + ": expected a single character but found '" + cell + "'");
                    row[c] = cell.charAt(0);
                }
            }
            if (width < 0) width = row.length;
            else if (row.length != width)
                throw new IllegalArgumentException("Line " + (i + 1) + " has " + row.length
                        + " cells, expected " + width);
            rows.add(row);
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV file is empty");
        return rows.toArray(new char[0][]);
    }

    public static void write(String path, char[][] grid) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (char[] row : grid) {
            for (int c = 0; c < row.length; c++) {
                if (c > 0) sb.append(',');
                sb.append(row[c]);
            }
            sb.append('\n');
        }
        Files.write(Paths.get(path), sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
