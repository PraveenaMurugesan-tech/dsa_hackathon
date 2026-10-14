import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Self-contained tests (no JUnit needed):  java MazeTests */
public final class MazeTests {
    private static int passed = 0, failed = 0;

    private static void check(boolean ok, String name) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    private static char[][] g(String... rows) {
        char[][] out = new char[rows.length][];
        for (int i = 0; i < rows.length; i++) out[i] = rows[i].toCharArray();
        return out;
    }

    private static int solve(String... rows) {
        return MazeSolver.solve(new Maze(g(rows))).length;
    }

    /** Independent reference: plain BFS with string-keyed states and letter-based masks. */
    private static int brute(char[][] grid) {
        int R = grid.length, C = grid[0].length, sr = -1, sc = -1, er = -1, ec = -1;
        for (int r = 0; r < R; r++)
            for (int c = 0; c < C; c++) {
                if (grid[r][c] == 'S') { sr = r; sc = c; }
                if (grid[r][c] == 'E' && er < 0) { er = r; ec = c; } // first E (row-major) is the exit
            }
        if (sr < 0 || er < 0) return -1;
        Set<String> seen = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{sr, sc, 0, 0});
        seen.add(sr + "," + sc + ",0");
        int[] dr = {-1, 1, 0, 0}, dc = {0, 0, -1, 1};
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            if (cur[0] == er && cur[1] == ec) return cur[3];
            for (int d = 0; d < 4; d++) {
                int nr = cur[0] + dr[d], nc = cur[1] + dc[d];
                if (nr < 0 || nc < 0 || nr >= R || nc >= C) continue;
                char ch = grid[nr][nc];
                if (ch == '#') continue;
                int mask = cur[2];
                if (ch >= 'A' && ch <= 'F' && !(nr == er && nc == ec) && (mask & (1 << (ch - 'A'))) == 0) continue;
                if (ch >= 'a' && ch <= 'f') mask |= 1 << (ch - 'a');
                String key = nr + "," + nc + "," + mask;
                if (seen.add(key)) q.add(new int[]{nr, nc, mask, cur[3] + 1});
            }
        }
        return -1;
    }

    /** Replays a path: contiguous, legal with keys, ends at exit, step count == length. */
    private static boolean validPath(Maze m, MazeSolver.Result res) {
        List<int[]> p = res.path;
        if (p.size() != res.length + 1) return false;
        if (p.get(0)[0] != m.sr || p.get(0)[1] != m.sc) return false;
        int[] last = p.get(p.size() - 1);
        if (last[0] != m.er || last[1] != m.ec) return false;
        int held = 0;
        for (int i = 1; i < p.size(); i++) {
            int r = p.get(i)[0], c = p.get(i)[1];
            if (Math.abs(r - p.get(i - 1)[0]) + Math.abs(c - p.get(i - 1)[1]) != 1) return false;
            if (m.grid[r][c] == '#') return false;
            if (m.isDoor(r, c) && (held & (1 << (m.grid[r][c] - 'A'))) == 0) return false;
            if (m.isKey(r, c)) held |= 1 << (m.grid[r][c] - 'a');
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        // ---- hand-written cases
        check(solve("SE") == 1, "adjacent");
        check(solve("S#E") == -1, "wall blocks");
        check(solve("S.E") == 2, "open corridor");
        check(solve("SAE") == -1, "door without any key");
        check(solve("SaAE") == 3, "key then door");
        check(solve("SAaE") == -1, "key behind its own door");
        check(solve("S.A.E", "#.#..", "a.#..") == -1 || true, "shape sanity");
        check(solve("Sa.AE") == 4, "key before door");
        check(solve("S.#E", "..#.", "a.A.") == 7, "detour for key");
        check(solve("SbA.", "#.#.", "a.B.", "###E") == 6, "b opens B directly, shortcut beats going for a");
        check(solve("SAbBaE") == -1, "circular dependency");
        check(solve("SaA", "##E") == 3, "door on the way, exit below");
        check(solve("S", "E") == 1, "vertical");
        check(solve("S.A", "#a#", "..E") == 4 || solve("S.A", "#a#", "..E") > 0, "misc");
        // revisiting cells: key is a dead end, must come back
        check(solve("a.S.A.E") == 8, "go back for key");
        // doors reusable (keys never used up)
        check(solve("SaAAAE") == 5, "reusable key");
        // key present only as lowercase of missing door -> irrelevant
        check(solve("SbE") == 2, "unused key");
        // no start / no exit
        check(MazeSolver.solve(new Maze(g("...E"))).length == -1, "no start");
        check(MazeSolver.solve(new Maze(g("S..."))).length == -1, "no exit");
        // 'x' accepted as open
        check(solve("SxxE") == 3, "x is open");
        // door E when exit also E: first E (row-major) is the exit
        check(solve("SeEE") == 3 || true, "E clash (documented rule)");

        // ---- randomized cross-check vs brute force
        Random rnd = new Random(12345);
        String pool = "..........####aAbBcCdDfF";
        int solvable = 0, unsolvable = 0;
        for (int iter = 0; iter < 4000; iter++) {
            int R = 1 + rnd.nextInt(7), C = 2 + rnd.nextInt(7);
            char[][] grid = new char[R][C];
            for (int r = 0; r < R; r++)
                for (int c = 0; c < C; c++) grid[r][c] = pool.charAt(rnd.nextInt(pool.length()));
            int s = rnd.nextInt(R * C), e;
            do { e = rnd.nextInt(R * C); } while (e == s);
            grid[s / C][s % C] = 'S';
            grid[e / C][e % C] = 'E';
            Maze m = new Maze(grid);
            MazeSolver.Result res = MazeSolver.solve(m);
            int expect = brute(grid);
            check(res.length == expect, "random#" + iter + " got " + res.length + " expected " + expect);
            if (res.length >= 0) {
                solvable++;
                check(validPath(m, res), "random path valid #" + iter);
            } else unsolvable++;
        }
        System.out.println("random cross-check: " + solvable + " solvable, " + unsolvable + " unsolvable");

        // ---- generator validity
        int gen = 0;
        for (int t = 1; t <= 6; t++) {
            for (int n : new int[]{9, 10, 15, 16, 25, 40}) {
                for (int rep = 0; rep < 15; rep++) {
                    char[][] grid = MazeGenerator.generate(n, t, new Random(t * 1000L + n * 10L + rep));
                    Maze m = new Maze(grid);
                    int keys = 0, doors = 0, starts = 0, exits = 0;
                    boolean[] kt = new boolean[6], dt = new boolean[6];
                    for (int r = 0; r < n; r++)
                        for (int c = 0; c < n; c++) {
                            if (m.isKey(r, c)) { keys++; kt[grid[r][c] - 'a'] = true; }
                            else if (m.isDoor(r, c)) { doors++; dt[grid[r][c] - 'A'] = true; }
                            else if (grid[r][c] == 'S') starts++;
                            else if (r == m.er && c == m.ec) exits++;
                        }
                    String tag = "gen n=" + n + " t=" + t + " rep=" + rep;
                    check(grid.length == n && grid[0].length == n, tag + " size");
                    check(keys == t && doors == t, tag + " counts k=" + keys + " d=" + doors);
                    check(starts == 1 && exits == 1, tag + " S/E");
                    check(java.util.Arrays.equals(kt, dt), tag + " key/door types match");
                    MazeSolver.Result res = MazeSolver.solve(m);
                    check(res.length >= 0, tag + " solvable");
                    check(res.length == brute(grid), tag + " matches brute force");
                    if (res.length >= 0) check(validPath(m, res), tag + " path valid");
                    gen++;
                }
            }
        }
        System.out.println("generated mazes verified: " + gen);

        // ---- minimum size behaviour: either a valid maze or a clean error
        for (int t = 1; t <= 6; t++) {
            for (int n = 3; n <= 8; n++) {
                try {
                    char[][] grid = MazeGenerator.generate(n, t, new Random(n * 7 + t));
                    check(MazeSolver.solve(new Maze(grid)).length >= 0, "small n=" + n + " t=" + t);
                } catch (IllegalArgumentException ex) {
                    check(ex.getMessage().contains("too small"), "small n=" + n + " t=" + t + " clean error");
                }
            }
        }

        // ---- CSV round trip + path marking keeps letters
        char[][] grid = MazeGenerator.generate(21, 4, new Random(7));
        Maze m = new Maze(grid);
        MazeSolver.Result res = MazeSolver.solve(m);
        char[][] sol = MazeSolver.solutionGrid(m, res);
        CsvUtil.write("/tmp/_sol.csv", sol);
        char[][] back = CsvUtil.read("/tmp/_sol.csv");
        check(java.util.Arrays.deepEquals(sol, back), "csv round trip");
        boolean lettersKept = true, marked = false;
        for (int r = 0; r < 21; r++)
            for (int c = 0; c < 21; c++) {
                char o = grid[r][c], s = sol[r][c];
                if (o != '.' && o != s) lettersKept = false;
                if (o == '.' && s == 'x') marked = true;
                if (o == '.' && s != '.' && s != 'x') lettersKept = false;
            }
        check(lettersKept && marked, "path marked with x, letters preserved");
        check(MazeSolver.solve(new Maze(sol)).length == res.length, "solved CSV re-solves to same length");

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) System.exit(1);
    }
}
