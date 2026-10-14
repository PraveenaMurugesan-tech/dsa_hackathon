import java.util.Arrays;

/**
 * Immutable maze model.
 *
 * Cell legend: '.' open, '#' wall, 'S' start, 'E' exit, 'a'-'f' keys, 'A'-'F' doors.
 * 'x' (a path mark from a previously solved CSV) is accepted and treated as '.'.
 *
 * NOTE on the 'E' clash: the exit is written 'E' and door type E is also 'E'.
 * Rule used everywhere: if the grid has exactly one 'E' it is the exit; if it has
 * several, the FIRST one in row-major order is the exit and the rest are doors.
 */
public final class Maze {
    public static final int NOT_DOOR = -1;
    public static final int BLOCKED = -2; // door whose key does not exist anywhere -> impassable

    public final char[][] grid;
    public final int rows, cols;
    public final int sr, sc; // start (-1 if missing)
    public final int er, ec; // exit  (-1 if missing)
    public final boolean[] hasKey = new boolean[6];
    public final boolean[] hasDoor = new boolean[6];
    /** Number of distinct key types present (mask width used by the solver). */
    public final int keyCount;
    private final int[] bitOfType = new int[6];

    public Maze(char[][] src) {
        if (src == null || src.length == 0 || src[0].length == 0)
            throw new IllegalArgumentException("Grid is empty");
        rows = src.length;
        cols = src[0].length;
        grid = new char[rows][cols];

        int startCount = 0, exitCount = 0;
        int s_r = -1, s_c = -1, e_r = -1, e_c = -1;
        for (int r = 0; r < rows; r++) {
            if (src[r].length != cols)
                throw new IllegalArgumentException("Row " + (r + 1) + " has " + src[r].length
                        + " cells, expected " + cols);
            for (int c = 0; c < cols; c++) {
                char ch = src[r][c];
                if (ch == 'x') ch = '.';
                boolean ok = ch == '.' || ch == '#' || ch == 'S' || ch == 'E'
                        || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
                if (!ok)
                    throw new IllegalArgumentException("Invalid cell '" + ch + "' at row "
                            + (r + 1) + ", column " + (c + 1));
                grid[r][c] = ch;
                if (ch == 'S') {
                    startCount++;
                    s_r = r;
                    s_c = c;
                } else if (ch == 'E') {
                    exitCount++;
                    if (exitCount == 1) {
                        e_r = r;
                        e_c = c;
                    }
                }
            }
        }
        if (startCount > 1) throw new IllegalArgumentException("Grid has more than one 'S'");
        sr = s_r;
        sc = s_c;
        er = e_r;
        ec = e_c;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = grid[r][c];
                if (ch >= 'a' && ch <= 'f') hasKey[ch - 'a'] = true;
                else if (ch >= 'A' && ch <= 'F' && !(r == er && c == ec)) hasDoor[ch - 'A'] = true;
            }
        }
        int k = 0;
        for (int t = 0; t < 6; t++) bitOfType[t] = hasKey[t] ? k++ : -1;
        keyCount = k;
    }

    public boolean isDoor(int r, int c) {
        char ch = grid[r][c];
        return ch >= 'A' && ch <= 'F' && !(r == er && c == ec);
    }

    public boolean isKey(int r, int c) {
        char ch = grid[r][c];
        return ch >= 'a' && ch <= 'f';
    }

    /** Compact mask bit of the key in this cell, or -1 if the cell is not a key. */
    public int keyBit(int r, int c) {
        return isKey(r, c) ? bitOfType[grid[r][c] - 'a'] : -1;
    }

    /** Mask bit needed to enter this cell; NOT_DOOR for non-doors; BLOCKED if no matching key exists. */
    public int doorNeed(int r, int c) {
        if (!isDoor(r, c)) return NOT_DOOR;
        int b = bitOfType[grid[r][c] - 'A'];
        return b < 0 ? BLOCKED : b;
    }

    public char[][] copyGrid() {
        char[][] g = new char[rows][];
        for (int r = 0; r < rows; r++) g[r] = Arrays.copyOf(grid[r], cols);
        return g;
    }
}
