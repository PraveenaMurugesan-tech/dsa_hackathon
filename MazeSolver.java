import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Shortest path with keys/doors: BFS over states (row, col, set-of-keys-held).
 * Memory per state: 4 bytes (queue) + 1 byte (parent move), so large grids stay feasible.
 */
public final class MazeSolver {
    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    public static final class Result {
        /** Number of moves, or -1 if the exit cannot be reached. */
        public final int length;
        /** Cells from start to exit inclusive (empty if unsolvable). Cells may repeat. */
        public final List<int[]> path;

        Result(int length, List<int[]> path) {
            this.length = length;
            this.path = path;
        }
    }

    private MazeSolver() {}

    public static Result solve(Maze m) {
        if (m.sr < 0 || m.er < 0) return new Result(-1, Collections.<int[]>emptyList());

        final int R = m.rows, C = m.cols, M = 1 << m.keyCount;
        long totalL = (long) R * C * M;
        if (totalL > Integer.MAX_VALUE - 16)
            throw new IllegalArgumentException("Grid too large for the state space");
        final int total = (int) totalL;

        byte[] par = new byte[total];   // 0 = unvisited, 100 = start, else 1 + (dir<<1 | gainedKey)
        int[] queue = new int[total];
        int head = 0, tail = 0;

        final int start = (m.sr * C + m.sc) * M; // mask 0
        par[start] = 100;
        queue[tail++] = start;
        int goal = -1;

        search:
        while (head < tail) {
            int s = queue[head++];
            int mask = s % M;
            int cell = s / M;
            int r = cell / C, c = cell % C;
            for (int d = 0; d < 4; d++) {
                int nr = r + DR[d], nc = c + DC[d];
                if (nr < 0 || nc < 0 || nr >= R || nc >= C) continue;
                if (m.grid[nr][nc] == '#') continue;
                int need = m.doorNeed(nr, nc);
                if (need == Maze.BLOCKED) continue;
                if (need >= 0 && ((mask >> need) & 1) == 0) continue;
                int nm = mask;
                boolean gained = false;
                int kb = m.keyBit(nr, nc);
                if (kb >= 0 && ((mask >> kb) & 1) == 0) {
                    nm |= 1 << kb;
                    gained = true;
                }
                int ns = (nr * C + nc) * M + nm;
                if (par[ns] != 0) continue;
                par[ns] = (byte) (1 + ((d << 1) | (gained ? 1 : 0)));
                if (nr == m.er && nc == m.ec) {
                    goal = ns;
                    break search; // BFS: first discovery of the exit is a shortest path
                }
                queue[tail++] = ns;
            }
        }

        if (goal < 0) return new Result(-1, Collections.<int[]>emptyList());

        LinkedList<int[]> path = new LinkedList<>();
        int s = goal;
        while (true) {
            int cell = s / M, mask = s % M;
            int r = cell / C, c = cell % C;
            path.addFirst(new int[]{r, c});
            if (s == start) break;
            int p = (par[s] & 0xFF) - 1;
            boolean gained = (p & 1) != 0;
            int d = p >> 1;
            int pr = r - DR[d], pc = c - DC[d];
            int pm = gained ? (mask & ~(1 << m.keyBit(r, c))) : mask;
            s = (pr * C + pc) * M + pm;
        }
        return new Result(path.size() - 1, new ArrayList<>(path));
    }

    /**
     * Copy of the grid with the route marked by 'x'.
     * Start, exit, keys and doors on the route keep their letters so they stay identifiable.
     */
    public static char[][] solutionGrid(Maze m, Result res) {
        char[][] g = m.copyGrid();
        for (int[] p : res.path) {
            if (g[p[0]][p[1]] == '.') g[p[0]][p[1]] = 'x';
        }
        return g;
    }
}
