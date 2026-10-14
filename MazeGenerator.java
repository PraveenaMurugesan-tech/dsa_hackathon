import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Generates an N x N maze with exactly T keys and T doors that is guaranteed solvable.
 *
 * Construction:
 *  1. Perfect maze (randomized DFS) -> unique route between any two cells.
 *  2. Start/exit = ends of the longest route (tree diameter).
 *  3. Put the T doors on that route (true chokepoints).
 *  4. Key i is placed only in the region reachable with keys 0..i-1, so a valid
 *     order of collection always exists.
 *  5. Extra loops are knocked through walls only INSIDE door-free regions, so no
 *     door can be bypassed.
 *  6. The result is re-verified with the solver; otherwise the attempt is discarded.
 *
 * Letter types used are a,b,c,d,f first and e last, so the clash between door 'E' and
 * the exit 'E' only appears when T = 6 (the exit is then guaranteed to be the first 'E'
 * in row-major order, matching Maze's parsing rule).
 */
public final class MazeGenerator {
    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};
    private static final char[] LETTER_POOL = {'a', 'b', 'c', 'd', 'f', 'e'};
    private static final double LOOP_PROB = 0.10;

    private MazeGenerator() {}

    public static char[][] generate(int n, int t, Random rnd) {
        if (t < 1 || t > 6) throw new IllegalArgumentException("T must be between 1 and 6");
        if (n < 3) throw new IllegalArgumentException("N must be at least 3");
        for (int attempt = 0; attempt < 3000; attempt++) {
            char[][] g = attempt(n, t, rnd);
            if (g != null) return g;
        }
        throw new IllegalArgumentException("N=" + n + " is too small to fit " + t
                + " keys and doors in a solvable maze; try a larger N");
    }

    private static char[][] attempt(int n, int t, Random rnd) {
        char[][] g = new char[n][n];
        for (char[] row : g) java.util.Arrays.fill(row, '#');

        // 1. perfect maze on even coordinates
        int dim = (n + 1) / 2;
        boolean[][] seen = new boolean[dim][dim];
        Deque<int[]> stack = new ArrayDeque<>();
        int i0 = rnd.nextInt(dim), j0 = rnd.nextInt(dim);
        seen[i0][j0] = true;
        g[2 * i0][2 * j0] = '.';
        stack.push(new int[]{i0, j0});
        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            List<Integer> dirs = new ArrayList<>(4);
            for (int d = 0; d < 4; d++) {
                int ni = cur[0] + DR[d], nj = cur[1] + DC[d];
                if (ni >= 0 && nj >= 0 && ni < dim && nj < dim && !seen[ni][nj]) dirs.add(d);
            }
            if (dirs.isEmpty()) {
                stack.pop();
                continue;
            }
            int d = dirs.get(rnd.nextInt(dirs.size()));
            int ni = cur[0] + DR[d], nj = cur[1] + DC[d];
            g[2 * cur[0] + DR[d]][2 * cur[1] + DC[d]] = '.';
            g[2 * ni][2 * nj] = '.';
            seen[ni][nj] = true;
            stack.push(new int[]{ni, nj});
        }

        // 2. start / exit at the diameter
        int[] any = {2 * i0, 2 * j0};
        int[] u = farthest(g, any, null, Integer.MAX_VALUE);
        int[] v = farthest(g, u, null, Integer.MAX_VALUE);
        int[] S = rnd.nextBoolean() ? u : v;
        int[] E = (S == u) ? v : u;

        // route S -> E
        List<int[]> route = route(g, S, E);
        int interior = route.size() - 2;
        if (interior < t) return null;

        // 3. doors on random interior route cells
        List<Integer> idx = new ArrayList<>();
        for (int k = 1; k <= interior; k++) idx.add(k);
        Collections.shuffle(idx, rnd);
        List<Integer> chosen = new ArrayList<>(idx.subList(0, t));
        Collections.sort(chosen);

        List<Character> letters = new ArrayList<>();
        for (int k = 0; k < t; k++) letters.add(LETTER_POOL[k]);
        Collections.shuffle(letters, rnd);

        int[][] doorIdx = new int[n][n];
        for (int[] row : doorIdx) java.util.Arrays.fill(row, -1);
        for (int k = 0; k < t; k++) {
            int[] cell = route.get(chosen.get(k));
            g[cell[0]][cell[1]] = Character.toUpperCase(letters.get(k));
            doorIdx[cell[0]][cell[1]] = k;
        }

        // 4. keys, door by door, in the region reachable before that door
        for (int k = 0; k < t; k++) {
            int[][] dist = regionDist(g, doorIdx, S, k);
            List<int[]> cands = new ArrayList<>();
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++)
                    if (dist[r][c] >= 0 && g[r][c] == '.'
                            && !(r == S[0] && c == S[1]) && !(r == E[0] && c == E[1]))
                        cands.add(new int[]{r, c});
            if (cands.isEmpty()) return null;
            final int[][] fd = dist;
            Collections.sort(cands, new Comparator<int[]>() {
                @Override
                public int compare(int[] a, int[] b) {
                    return Integer.compare(fd[b[0]][b[1]], fd[a[0]][a[1]]);
                }
            });
            int pool = Math.max(1, (int) Math.ceil(cands.size() * 0.4)); // favour far-away spots
            int[] spot = cands.get(rnd.nextInt(pool));
            g[spot[0]][spot[1]] = letters.get(k);
        }

        // 5. loops inside door-free regions only (cannot bypass any door)
        int[][] comp = components(g, doorIdx);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (g[r][c] != '#') continue;
                int r1, c1, r2, c2;
                if (r % 2 == 1 && c % 2 == 0) { r1 = r - 1; c1 = c; r2 = r + 1; c2 = c; }
                else if (r % 2 == 0 && c % 2 == 1) { r1 = r; c1 = c - 1; r2 = r; c2 = c + 1; }
                else continue;
                if (r1 < 0 || c1 < 0 || r2 >= n || c2 >= n) continue;
                if (g[r1][c1] == '#' || g[r2][c2] == '#') continue;
                if (doorIdx[r1][c1] >= 0 || doorIdx[r2][c2] >= 0) continue;
                if (comp[r1][c1] != comp[r2][c2] || comp[r1][c1] < 0) continue;
                if (rnd.nextDouble() < LOOP_PROB) g[r][c] = '.';
            }
        }

        g[S[0]][S[1]] = 'S';
        g[E[0]][E[1]] = 'E';

        // 6. verify
        Maze m = new Maze(g);
        if (m.er != E[0] || m.ec != E[1] || m.sr != S[0] || m.sc != S[1]) return null; // 'E' door clash
        int keys = 0, doors = 0;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                if (m.isKey(r, c)) keys++;
                if (m.isDoor(r, c)) doors++;
            }
        if (keys != t || doors != t) return null;
        if (MazeSolver.solve(m).length < 0) return null;
        return g;
    }

    /** Farthest reachable cell from src over non-wall cells. */
    private static int[] farthest(char[][] g, int[] src, int[][] unused, int limit) {
        int n = g.length;
        int[][] dist = new int[n][n];
        for (int[] row : dist) java.util.Arrays.fill(row, -1);
        Deque<int[]> q = new ArrayDeque<>();
        dist[src[0]][src[1]] = 0;
        q.add(src);
        int[] best = src;
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            if (dist[cur[0]][cur[1]] > dist[best[0]][best[1]]) best = cur;
            for (int d = 0; d < 4; d++) {
                int nr = cur[0] + DR[d], nc = cur[1] + DC[d];
                if (nr < 0 || nc < 0 || nr >= n || nc >= n || g[nr][nc] == '#' || dist[nr][nc] >= 0) continue;
                dist[nr][nc] = dist[cur[0]][cur[1]] + 1;
                q.add(new int[]{nr, nc});
            }
        }
        return best;
    }

    private static List<int[]> route(char[][] g, int[] s, int[] e) {
        int n = g.length;
        int[][] prev = new int[n][n];
        for (int[] row : prev) java.util.Arrays.fill(row, -2);
        Deque<int[]> q = new ArrayDeque<>();
        prev[s[0]][s[1]] = -1;
        q.add(s);
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            if (cur[0] == e[0] && cur[1] == e[1]) break;
            for (int d = 0; d < 4; d++) {
                int nr = cur[0] + DR[d], nc = cur[1] + DC[d];
                if (nr < 0 || nc < 0 || nr >= n || nc >= n || g[nr][nc] == '#' || prev[nr][nc] != -2) continue;
                prev[nr][nc] = cur[0] * n + cur[1];
                q.add(new int[]{nr, nc});
            }
        }
        LinkedListHelper out = new LinkedListHelper();
        int cr = e[0], cc = e[1];
        while (true) {
            out.addFirst(new int[]{cr, cc});
            int p = prev[cr][cc];
            if (p < 0) break;
            cr = p / n;
            cc = p % n;
        }
        return out.list;
    }

    private static final class LinkedListHelper {
        final java.util.LinkedList<int[]> list = new java.util.LinkedList<>();
        void addFirst(int[] x) { list.addFirst(x); }
    }

    /** Distances from S over cells reachable when doors with index < openBelow are open and the rest closed. */
    private static int[][] regionDist(char[][] g, int[][] doorIdx, int[] s, int openBelow) {
        int n = g.length;
        int[][] dist = new int[n][n];
        for (int[] row : dist) java.util.Arrays.fill(row, -1);
        Deque<int[]> q = new ArrayDeque<>();
        dist[s[0]][s[1]] = 0;
        q.add(s);
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            for (int d = 0; d < 4; d++) {
                int nr = cur[0] + DR[d], nc = cur[1] + DC[d];
                if (nr < 0 || nc < 0 || nr >= n || nc >= n || g[nr][nc] == '#' || dist[nr][nc] >= 0) continue;
                if (doorIdx[nr][nc] >= openBelow) continue;
                dist[nr][nc] = dist[cur[0]][cur[1]] + 1;
                q.add(new int[]{nr, nc});
            }
        }
        return dist;
    }

    /** Connected components of non-wall, non-door cells. */
    private static int[][] components(char[][] g, int[][] doorIdx) {
        int n = g.length;
        int[][] comp = new int[n][n];
        for (int[] row : comp) java.util.Arrays.fill(row, -1);
        int id = 0;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                if (g[r][c] == '#' || doorIdx[r][c] >= 0 || comp[r][c] >= 0) continue;
                Deque<int[]> q = new ArrayDeque<>();
                comp[r][c] = id;
                q.add(new int[]{r, c});
                while (!q.isEmpty()) {
                    int[] cur = q.poll();
                    for (int d = 0; d < 4; d++) {
                        int nr = cur[0] + DR[d], nc = cur[1] + DC[d];
                        if (nr < 0 || nc < 0 || nr >= n || nc >= n || g[nr][nc] == '#'
                                || doorIdx[nr][nc] >= 0 || comp[nr][nc] >= 0) continue;
                        comp[nr][nc] = id;
                        q.add(new int[]{nr, nc});
                    }
                }
                id++;
            }
        return comp;
    }
}
