import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Exam Timetable Scheduling = graph colouring with a colour-class capacity R.
 *
 *  - Welsh-Powell (capacity aware)            : fast greedy
 *  - DSatur (capacity aware)                  : strong greedy
 *  - DSatur multi-start (random tie-breaking) : improved heuristic
 *  - Exact DSatur-style Branch & Bound        : proves optimality (within time limit)
 *
 * Build : javac ExamScheduler.java
 * Run   : java ExamScheduler input.txt            (Option 1: "N M R" + M edges)
 *         java ExamScheduler -i in.txt -d 0.3     (Option 2: first line "N R", random graph)
 */
public class ExamScheduler {

    // ======================================================================
    //  Graph
    // ======================================================================
    static final class Graph {
        final int n;
        final int[][] adj;
        final int[] deg;
        final long m;

        Graph(int n, int[][] adj, long m) {
            this.n = n;
            this.adj = adj;
            this.m = m;
            this.deg = new int[n];
            for (int i = 0; i < n; i++) deg[i] = adj[i].length;
        }

        /** Builds a simple graph: ignores self loops, merges duplicate edges. */
        static Graph build(int n, List<int[]> edges) {
            long[] keys = new long[edges.size()];
            int k = 0;
            for (int[] e : edges) {
                int u = e[0], v = e[1];
                if (u == v) continue;
                if (u > v) { int t = u; u = v; v = t; }
                keys[k++] = (long) u * n + v;
            }
            keys = Arrays.copyOf(keys, k);
            Arrays.sort(keys);
            int[] d = new int[n];
            long[] uniq = new long[k];
            int uc = 0;
            long prev = -1;
            for (long x : keys) {
                if (x != prev) {
                    uniq[uc++] = x;
                    prev = x;
                    d[(int) (x / n)]++;
                    d[(int) (x % n)]++;
                }
            }
            int[][] adj = new int[n][];
            for (int i = 0; i < n; i++) adj[i] = new int[d[i]];
            int[] fill = new int[n];
            for (int i = 0; i < uc; i++) {
                int u = (int) (uniq[i] / n), v = (int) (uniq[i] % n);
                adj[u][fill[u]++] = v;
                adj[v][fill[v]++] = u;
            }
            for (int i = 0; i < n; i++) Arrays.sort(adj[i]);
            return new Graph(n, adj, uc);
        }

        static Graph random(int n, double density, long seed) {
            Random r = new Random(seed);
            List<int[]> e = new ArrayList<>();
            for (int u = 0; u < n; u++)
                for (int v = u + 1; v < n; v++)
                    if (r.nextDouble() < density) e.add(new int[]{u, v});
            return build(n, e);
        }

        boolean adjacent(int u, int v) {
            return Arrays.binarySearch(adj[u], v) >= 0;
        }
    }

    // ======================================================================
    //  Result
    // ======================================================================
    static final class Result {
        String name;
        int[] slot;      // slot index (0-based) per course
        int slots;
        int[] order;     // order in which courses were assigned
        double ms;
        boolean optimal; // certified optimal
        String note = "";
    }

    static Result make(String name, int[] col, int[] order, long nanos) {
        Result r = new Result();
        r.name = name;
        r.slot = col;
        int mx = -1;
        for (int c : col) mx = Math.max(mx, c);
        r.slots = mx + 1;
        r.order = order;
        r.ms = nanos / 1e6;
        return r;
    }

    /** Returns null if valid, otherwise an error description. */
    static String validate(Graph g, int R, int[] col) {
        int[] size = new int[g.n + 1];
        for (int v = 0; v < g.n; v++) {
            if (col[v] < 0) return "course " + v + " unassigned";
            if (++size[col[v]] > R) return "slot " + col[v] + " exceeds capacity";
            for (int w : g.adj[v])
                if (w > v && col[w] == col[v]) return "conflict " + v + "-" + w;
        }
        return null;
    }

    // ======================================================================
    //  Greedy 1: Welsh-Powell (one slot at a time, by decreasing degree)
    // ======================================================================
    static Result welshPowell(Graph g, int R) {
        long t0 = System.nanoTime();
        int n = g.n;
        Integer[] ordB = new Integer[n];
        for (int i = 0; i < n; i++) ordB[i] = i;
        Arrays.sort(ordB, (a, b) -> g.deg[b] != g.deg[a] ? g.deg[b] - g.deg[a] : a - b);
        int[] ord = new int[n];
        for (int i = 0; i < n; i++) ord[i] = ordB[i];

        int[] col = new int[n];
        Arrays.fill(col, -1);
        int[] block = new int[n];
        Arrays.fill(block, -1);
        int[] order = new int[n];
        int oc = 0, done = 0, c = 0;
        while (done < n) {
            int size = 0;
            for (int v : ord) {
                if (size >= R) break;
                if (col[v] != -1 || block[v] == c) continue;
                col[v] = c;
                order[oc++] = v;
                size++;
                done++;
                for (int w : g.adj[v]) block[w] = c;
            }
            c++;
        }
        return make("Welsh-Powell", col, order, System.nanoTime() - t0);
    }

    // ======================================================================
    //  Greedy 2: DSatur (optionally with random tie-breaking)
    // ======================================================================
    static Result dsatur(Graph g, int R, Random rnd, String name) {
        long t0 = System.nanoTime();
        int n = g.n;
        int[] col = new int[n];
        Arrays.fill(col, -1);
        BitSet[] nb = new BitSet[n];
        for (int i = 0; i < n; i++) nb[i] = new BitSet();
        int[] sat = new int[n];
        int[] size = new int[n + 1];
        double[] tie = null;
        if (rnd != null) {
            tie = new double[n];
            for (int i = 0; i < n; i++) tie[i] = rnd.nextDouble();
        }
        int[] order = new int[n];
        int used = 0;
        for (int step = 0; step < n; step++) {
            int b = -1;
            for (int v = 0; v < n; v++) {
                if (col[v] != -1) continue;
                if (b == -1 || sat[v] > sat[b]
                        || (sat[v] == sat[b] && (g.deg[v] > g.deg[b]
                        || (g.deg[v] == g.deg[b] && tie != null && tie[v] > tie[b])))) b = v;
            }
            int c = 0;
            while (c < used && (nb[b].get(c) || size[c] >= R)) c++;
            if (c == used) used++;
            col[b] = c;
            size[c]++;
            order[step] = b;
            for (int w : g.adj[b])
                if (col[w] == -1 && !nb[w].get(c)) { nb[w].set(c); sat[w]++; }
        }
        return make(name, col, order, System.nanoTime() - t0);
    }

    static Result dsaturMulti(Graph g, int R, double budgetSec, long seed) {
        long t0 = System.nanoTime();
        Result best = dsatur(g, R, null, "DSatur multi-start");
        Random rnd = new Random(seed);
        long limit = t0 + (long) (budgetSec * 1e9);
        int runs = 0;
        while (g.n > 0 && runs < 2000 && System.nanoTime() < limit) {
            Result r = dsatur(g, R, rnd, "DSatur multi-start");
            if (r.slots < best.slots) best = r;
            runs++;
        }
        best.ms = (System.nanoTime() - t0) / 1e6;
        best.note = (runs + 1) + " runs";
        return best;
    }

    // ======================================================================
    //  Lower bound = max( ceil(N/R), greedy clique size )
    // ======================================================================
    static int cliqueLB(Graph g) {
        int n = g.n;
        if (n == 0) return 0;
        if (g.m == 0) return 1;
        Integer[] byDeg = new Integer[n];
        for (int i = 0; i < n; i++) byDeg[i] = i;
        Arrays.sort(byDeg, (a, b) -> g.deg[b] - g.deg[a]);
        int starts = n <= 300 ? n : 100;
        int best = 2;
        for (int si = 0; si < starts; si++) {
            int s = byDeg[si];
            Integer[] nbs = new Integer[g.adj[s].length];
            for (int i = 0; i < nbs.length; i++) nbs[i] = g.adj[s][i];
            Arrays.sort(nbs, (a, b) -> g.deg[b] - g.deg[a]);
            int[] clique = new int[g.deg[s] + 1];
            int cs = 0;
            clique[cs++] = s;
            for (int u : nbs) {
                boolean ok = true;
                for (int i = 1; i < cs && ok; i++) ok = g.adjacent(u, clique[i]);
                if (ok) clique[cs++] = u;
            }
            best = Math.max(best, cs);
        }
        return best;
    }

    static int lowerBound(Graph g, int R) {
        if (g.n == 0) return 0;
        long cap = (g.n + (long) R - 1) / R;
        return (int) Math.max(cap, cliqueLB(g));
    }

    // ======================================================================
    //  Exact: DSatur-ordered Branch & Bound with capacity
    // ======================================================================
    static final class Exact {
        final Graph g;
        final int R, n, lb;
        final long deadline;
        int best;
        int[] bestCol, bestOrder;
        final int[][] cnt;      // cnt[v][c] = #neighbours of v in slot c
        final int[] col, size, path;
        boolean timedOut;
        long nodes;

        Exact(Graph g, int R, Result init, int lb, long deadline) {
            this.g = g;
            this.R = R;
            this.n = g.n;
            this.lb = lb;
            this.deadline = deadline;
            best = init.slots;
            bestCol = init.slot.clone();
            bestOrder = init.order.clone();
            cnt = new int[n][best];
            col = new int[n];
            Arrays.fill(col, -1);
            size = new int[best + 1];
            path = new int[n];
        }

        void run() {
            if (n == 0 || best <= lb) return;
            dfs(0, 0);
        }

        void put(int v, int c, int depth) {
            col[v] = c;
            size[c]++;
            path[depth] = v;
            for (int w : g.adj[v]) cnt[w][c]++;
        }

        void remove(int v, int c) {
            col[v] = -1;
            size[c]--;
            for (int w : g.adj[v]) cnt[w][c]--;
        }

        void dfs(int depth, int used) {
            if (timedOut || best <= lb) return;
            if (depth == n) {
                if (used < best) {
                    best = used;
                    bestCol = col.clone();
                    bestOrder = path.clone();
                }
                return;
            }
            if ((++nodes & 1023) == 0 && System.nanoTime() > deadline) { timedOut = true; return; }

            // capacity bound: vertices that cannot fit in the free space of used slots need new slots
            long rem = n - depth;
            long free = (long) used * R - depth;
            long extra = rem > free ? (rem - free + R - 1) / R : 0;
            if (used + extra >= best) return;

            // most constrained vertex (fewest available slots), ties -> highest degree
            int bv = -1, ba = Integer.MAX_VALUE;
            for (int v = 0; v < n; v++) {
                if (col[v] != -1) continue;
                int a = 0;
                int[] cv = cnt[v];
                for (int c = 0; c < used; c++) if (cv[c] == 0 && size[c] < R) a++;
                if (a < ba || (a == ba && g.deg[v] > g.deg[bv])) {
                    ba = a;
                    bv = v;
                    if (a == 0) break;
                }
            }
            int[] cand = new int[ba];
            int k = 0;
            int[] cv = cnt[bv];
            for (int c = 0; c < used; c++) {
                if (cv[c] == 0 && size[c] < R) {
                    int j = k++;
                    while (j > 0 && size[cand[j - 1]] < size[c]) { cand[j] = cand[j - 1]; j--; } // fuller slots first
                    cand[j] = c;
                }
            }
            for (int i = 0; i < k; i++) {
                int c = cand[i];
                put(bv, c, depth);
                dfs(depth + 1, used);
                remove(bv, c);
                if (timedOut || best <= lb) return;
            }
            if (used + 1 < best) {   // open exactly one new slot (symmetry breaking)
                put(bv, used, depth);
                dfs(depth + 1, used + 1);
                remove(bv, used);
            }
        }
    }

    static Result exact(Graph g, int R, Result init, int lb, double timeSec) {
        long t0 = System.nanoTime();
        Exact ex = new Exact(g, R, init, lb, t0 + (long) (timeSec * 1e9));
        ex.run();
        Result r = make("Exact B&B", ex.bestCol, ex.bestOrder, System.nanoTime() - t0);
        r.ms += init.ms; // include time spent obtaining the starting upper bound
        r.optimal = !ex.timedOut || r.slots <= lb;
        r.note = ex.nodes + " nodes" + (ex.timedOut && !r.optimal ? ", time limit hit" : "");
        return r;
    }

    // ======================================================================
    //  Layout (Fruchterman-Reingold) for the HTML report
    // ======================================================================
    static double[][] layout(Graph g) {
        int n = g.n;
        double[][] p = new double[n][2];
        Random r = new Random(7);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / Math.max(n, 1);
            p[i][0] = 0.5 + 0.4 * Math.cos(a) + (r.nextDouble() - 0.5) * 0.02;
            p[i][1] = 0.5 + 0.4 * Math.sin(a) + (r.nextDouble() - 0.5) * 0.02;
        }
        if (n > 400 || n < 3) return p;
        double k = Math.sqrt(1.0 / n);
        double temp = 0.1;
        double[][] d = new double[n][2];
        for (int it = 0; it < 250; it++) {
            for (double[] x : d) { x[0] = 0; x[1] = 0; }
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++) {
                    double dx = p[i][0] - p[j][0], dy = p[i][1] - p[j][1];
                    double dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1e-4);
                    double f = k * k / dist;
                    d[i][0] += dx / dist * f; d[i][1] += dy / dist * f;
                    d[j][0] -= dx / dist * f; d[j][1] -= dy / dist * f;
                }
            for (int u = 0; u < n; u++)
                for (int v : g.adj[u]) {
                    if (v < u) continue;
                    double dx = p[u][0] - p[v][0], dy = p[u][1] - p[v][1];
                    double dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1e-4);
                    double f = dist * dist / k;
                    d[u][0] -= dx / dist * f; d[u][1] -= dy / dist * f;
                    d[v][0] += dx / dist * f; d[v][1] += dy / dist * f;
                }
            for (int i = 0; i < n; i++) {
                double len = Math.max(Math.sqrt(d[i][0] * d[i][0] + d[i][1] * d[i][1]), 1e-9);
                double s = Math.min(len, temp);
                p[i][0] += d[i][0] / len * s;
                p[i][1] += d[i][1] / len * s;
            }
            temp *= 0.98;
        }
        double minX = 1e18, maxX = -1e18, minY = 1e18, maxY = -1e18;
        for (double[] q : p) {
            minX = Math.min(minX, q[0]); maxX = Math.max(maxX, q[0]);
            minY = Math.min(minY, q[1]); maxY = Math.max(maxY, q[1]);
        }
        double sx = Math.max(maxX - minX, 1e-9), sy = Math.max(maxY - minY, 1e-9);
        for (double[] q : p) {
            q[0] = 0.04 + 0.92 * (q[0] - minX) / sx;
            q[1] = 0.04 + 0.92 * (q[1] - minY) / sy;
        }
        return p;
    }

    // ======================================================================
    //  Options / input
    // ======================================================================
    static final class Opt {
        String input = null;
        double density = -1;
        long seed = 42;
        double time = 10;
        String out = "output";
        boolean bench = true;
        double benchTime = 2;
        int benchMax = 120;
    }

    static Opt parseArgs(String[] a) {
        Opt o = new Opt();
        for (int i = 0; i < a.length; i++) {
            switch (a[i]) {
                case "-i": case "--input": o.input = a[++i]; break;
                case "-d": case "--density": o.density = Double.parseDouble(a[++i]); break;
                case "-s": case "--seed": o.seed = Long.parseLong(a[++i]); break;
                case "-t": case "--time": o.time = Double.parseDouble(a[++i]); break;
                case "-o": case "--out": o.out = a[++i]; break;
                case "--nobench": o.bench = false; break;
                case "--bench-time": o.benchTime = Double.parseDouble(a[++i]); break;
                case "--bench-max": o.benchMax = Integer.parseInt(a[++i]); break;
                default:
                    if (a[i].startsWith("-")) throw new IllegalArgumentException("Unknown option " + a[i]);
                    o.input = a[i];
            }
        }
        return o;
    }

    static long toLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not an integer: '" + s + "'");
        }
    }

    // ======================================================================
    //  Main
    // ======================================================================
    public static void main(String[] args) throws Exception {
        // run in a thread with a big stack: B&B recursion depth = number of courses
        Thread t = new Thread(null, () -> {
            try {
                run(args);
            } catch (IllegalArgumentException e) {
                System.err.println("Input error: " + e.getMessage());
                System.exit(2);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }, "solver", 1L << 29);
        t.start();
        t.join();
    }

    static void run(String[] args) throws Exception {
        Opt opt = parseArgs(args);
        BufferedReader br = opt.input != null
                ? Files.newBufferedReader(Path.of(opt.input), StandardCharsets.UTF_8)
                : new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        String line;
        do {
            line = br.readLine();
            if (line == null) throw new IllegalArgumentException("Empty input");
        } while (line.trim().isEmpty());
        String[] head = line.trim().split("[\\s,]+");

        int n, R, base = 1;
        Graph g;
        double density = -1;
        if (head.length >= 3) {                       // ---- Option 1
            long nn = toLong(head[0]), mm = toLong(head[1]), rr = toLong(head[2]);
            if (nn < 0 || nn > 1_000_000) throw new IllegalArgumentException("N out of range");
            if (rr < 1) throw new IllegalArgumentException("R must be >= 1");
            if (mm < 0) throw new IllegalArgumentException("M must be >= 0");
            n = (int) nn;
            R = (int) Math.min(rr, Integer.MAX_VALUE / 2);
            List<Long> nums = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line, " \t,;");
                while (st.hasMoreTokens()) nums.add(toLong(st.nextToken()));
            }
            int pairs = nums.size() / 2;
            if (pairs < mm) System.err.println("Warning: expected " + mm + " edges, found " + pairs);
            int use = (int) Math.min(pairs, mm);
            long mn = Long.MAX_VALUE, mx = Long.MIN_VALUE;
            for (int i = 0; i < use * 2; i++) { mn = Math.min(mn, nums.get(i)); mx = Math.max(mx, nums.get(i)); }
            if (use > 0 && mn == 0) base = 0;
            if (use > 0 && (mn < base || mx > n - 1 + base))
                throw new IllegalArgumentException("Edge endpoint out of range [" + base + "," + (n - 1 + base) + "]");
            List<int[]> edges = new ArrayList<>();
            int loops = 0;
            for (int i = 0; i < use; i++) {
                int u = (int) (nums.get(2 * i) - base), v = (int) (nums.get(2 * i + 1) - base);
                if (u == v) { loops++; continue; }
                edges.add(new int[]{u, v});
            }
            if (loops > 0) System.err.println("Warning: ignored " + loops + " self-loop(s)");
            g = Graph.build(n, edges);
            density = n > 1 ? 2.0 * g.m / ((double) n * (n - 1)) : 0;
        } else if (head.length == 2) {                // ---- Option 2
            long nn = toLong(head[0]), rr = toLong(head[1]);
            if (nn < 0 || nn > 20000) throw new IllegalArgumentException("N out of range (0..20000 for random graphs)");
            if (rr < 1) throw new IllegalArgumentException("R must be >= 1");
            n = (int) nn;
            R = (int) Math.min(rr, Integer.MAX_VALUE / 2);
            density = opt.density;
            if (density < 0) {
                System.err.print("Edge density (0-1) [0.3]: ");
                String l = br.readLine();
                density = (l == null || l.trim().isEmpty()) ? 0.3 : Double.parseDouble(l.trim());
            }
            if (density > 1 && density <= 100) density /= 100;   // accept percentages
            if (density < 0 || density > 1) throw new IllegalArgumentException("density must be within [0,1]");
            g = Graph.random(n, density, opt.seed);
        } else {
            throw new IllegalArgumentException("First line must be 'N M R' or 'N R'");
        }

        Files.createDirectories(Path.of(opt.out));
        System.out.printf(Locale.ROOT, "Courses N=%d  conflicts M=%d  hall limit R=%d  density=%.3f%n", n, g.m, R, density);
        if (opt.input == null || head.length == 2) System.out.println("(random graph, seed " + opt.seed + ")");

        // ---------------- solve ----------------
        int lb = lowerBound(g, R);
        Result wp = welshPowell(g, R);
        Result ds = dsatur(g, R, null, "DSatur");
        Result ms = dsaturMulti(g, R, Math.min(1.0, opt.time * 0.1), opt.seed);
        Result init = wp;
        for (Result r : new Result[]{ds, ms}) if (r.slots < init.slots) init = r;
        Result ex = exact(g, R, init, lb, opt.time);
        ex.ms += wp.ms + ds.ms + ms.ms - init.ms;   // exact time includes seeding heuristics

        List<Result> all = List.of(wp, ds, ms, ex);
        for (Result r : all) {
            String err = validate(g, R, r.slot);
            if (err != null) throw new IllegalStateException(r.name + " produced invalid timetable: " + err);
            r.optimal = r.optimal || r.slots <= lb || (ex.optimal && r.slots == ex.slots);
        }

        // ---------------- Output 1 ----------------
        System.out.println();
        System.out.println("Lower bound on slots: " + lb + "  (max of ceil(N/R) and clique size)");
        System.out.printf("%-20s %6s  %-14s %12s  %s%n", "Algorithm", "Slots", "Proven optimal", "Time (ms)", "Note");
        StringBuilder res = new StringBuilder();
        res.append("Lower bound: ").append(lb).append('\n');
        for (Result r : all) {
            String line1 = String.format(Locale.ROOT, "%-20s %6d  %-14s %12.3f  %s",
                    r.name, r.slots, r.optimal ? "Yes" : "No", r.ms, r.note);
            System.out.println(line1);
            res.append(line1).append('\n');
        }
        if (!ex.optimal)
            System.out.println("Note: exact search hit the time limit; " + ex.slots + " slots is the best found (not proven).");

        // ---------------- Output 2 & 3 (final = exact result) ----------------
        List<List<Integer>> slotLists = new ArrayList<>();
        for (int s = 0; s < ex.slots; s++) slotLists.add(new ArrayList<>());
        for (int v = 0; v < n; v++) slotLists.get(ex.slot[v]).add(v + base);
        StringBuilder txt = new StringBuilder();
        StringBuilder csv = new StringBuilder();
        int cols = 1;
        for (List<Integer> l : slotLists) cols = Math.max(cols, l.size());
        csv.append("Slot");
        for (int j = 1; j <= cols; j++) csv.append(",Hall ").append(j);
        csv.append('\n');
        for (int s = 0; s < slotLists.size(); s++) {
            List<Integer> l = slotLists.get(s);
            txt.append("Slot ").append(s + 1).append(": ");
            csv.append("Slot ").append(s + 1);
            for (int j = 0; j < cols; j++) csv.append(',').append(j < l.size() ? String.valueOf(l.get(j)) : "");
            csv.append('\n');
            StringJoiner sj = new StringJoiner(", ");
            for (int c : l) sj.add(String.valueOf(c));
            txt.append(sj).append('\n');
        }
        System.out.println();
        System.out.println("Final timetable (" + ex.slots + " slots, " + (ex.optimal ? "proven optimal" : "best found") + "):");
        System.out.print(txt);

        Path out = Path.of(opt.out);
        Files.writeString(out.resolve("timetable.csv"), csv.toString());
        Files.writeString(out.resolve("slots.txt"), txt.toString());
        Files.writeString(out.resolve("results.txt"), res.toString());

        // ---------------- benchmark (slots & time as N grows) ----------------
        List<String> benchRows = new ArrayList<>();
        StringBuilder bcsv = new StringBuilder("N,algorithm,slots,time_ms,proven_optimal\n");
        if (opt.bench) {
            double bd = density > 0 ? density : 0.3;
            int[] sizes = {10, 20, 30, 40, 50, 60, 80, 100, 120, 150, 200};
            System.out.println();
            System.out.println("Benchmark (density " + String.format(Locale.ROOT, "%.2f", bd) + ", R=" + R + ", exact limit " + opt.benchTime + "s)...");
            for (int bn : sizes) {
                if (bn > opt.benchMax) break;
                Graph bg = Graph.random(bn, bd, opt.seed + bn);
                int blb = lowerBound(bg, R);
                Result bw = welshPowell(bg, R);
                Result bs = dsatur(bg, R, null, "DSatur");
                Result bm = dsaturMulti(bg, R, 0.05, opt.seed);
                Result bi = bw;
                for (Result r : new Result[]{bs, bm}) if (r.slots < bi.slots) bi = r;
                Result be = exact(bg, R, bi, blb, opt.benchTime);
                be.ms += bw.ms + bs.ms + bm.ms - bi.ms;
                Object[][] rows = {{"Welsh-Powell", bw}, {"DSatur", bs}, {"DSatur multi-start", bm}, {"Exact B&B", be}};
                for (Object[] row : rows) {
                    Result r = (Result) row[1];
                    bcsv.append(bn).append(',').append(row[0]).append(',').append(r.slots).append(',')
                            .append(String.format(Locale.ROOT, "%.3f", r.ms)).append(',')
                            .append(r.name.equals("Exact B&B") ? be.optimal : (r.slots <= blb || (be.optimal && r.slots == be.slots))).append('\n');
                }
                benchRows.add(String.format(Locale.ROOT,
                        "{\"n\":%d,\"ws\":{\"slots\":%d,\"ms\":%.4f},\"ds\":{\"slots\":%d,\"ms\":%.4f},"
                                + "\"mst\":{\"slots\":%d,\"ms\":%.4f},\"ex\":{\"slots\":%d,\"ms\":%.4f,\"optimal\":%b}}",
                        bn, bw.slots, bw.ms, bs.slots, bs.ms, bm.slots, bm.ms, be.slots, be.ms, be.optimal));
                System.out.printf(Locale.ROOT, "  N=%-4d WP=%-3d DSatur=%-3d Multi=%-3d Exact=%-3d (%s, %.1f ms)%n",
                        bn, bw.slots, bs.slots, bm.slots, be.slots, be.optimal ? "optimal" : "timeout", be.ms);
            }
            Files.writeString(out.resolve("benchmark.csv"), bcsv.toString());
        }

        // ---------------- HTML report ----------------
        double[][] pos = layout(g);
        StringBuilder js = new StringBuilder();
        js.append("{\"n\":").append(n).append(",\"R\":").append(R).append(",\"base\":").append(base)
                .append(",\"lb\":").append(lb).append(",\"m\":").append(g.m).append(",\"pos\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) js.append(',');
            js.append(String.format(Locale.ROOT, "[%.4f,%.4f]", pos[i][0], pos[i][1]));
        }
        js.append("],\"edges\":[");
        boolean drawEdges = g.m <= 30000;
        if (drawEdges) {
            boolean first = true;
            for (int u = 0; u < n; u++)
                for (int v : g.adj[u])
                    if (v > u) {
                        if (!first) js.append(',');
                        js.append('[').append(u).append(',').append(v).append(']');
                        first = false;
                    }
        }
        js.append("],\"algs\":[");
        for (int i = 0; i < all.size(); i++) {
            Result r = all.get(i);
            if (i > 0) js.append(',');
            js.append("{\"name\":\"").append(r.name).append("\",\"slots\":").append(r.slots)
                    .append(",\"optimal\":").append(r.optimal)
                    .append(String.format(Locale.ROOT, ",\"ms\":%.4f", r.ms))
                    .append(",\"note\":\"").append(r.note.replace("\"", "'")).append("\",\"slot\":")
                    .append(Arrays.toString(r.slot).replace(" ", "")).append(",\"order\":")
                    .append(Arrays.toString(r.order).replace(" ", "")).append('}');
        }
        js.append("],\"bench\":[").append(String.join(",", benchRows)).append("]}");
        Files.writeString(out.resolve("report.html"), HTML.replace("/*DATA*/null", js.toString()));

        System.out.println();
        System.out.println("Files written to " + out.toAbsolutePath() + ":");
        System.out.println("  timetable.csv, slots.txt, results.txt, report.html" + (opt.bench ? ", benchmark.csv" : ""));
    }

    // ======================================================================
    //  HTML report template (graph, timetable grid, charts, animation)
    // ======================================================================
    static final String HTML = """
<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Exam timetable report</title>
<style>
:root{--bg:#eef1f5;--ink:#16202e;--mute:#5d6b7e;--line:#cdd5e0;--panel:#fff}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.5 "Segoe UI",system-ui,sans-serif}
main{max-width:1200px;margin:0 auto;padding:24px 20px 60px}
h1{font-size:26px;margin:0 0 4px}
h2{font-size:17px;margin:0 0 10px}
.sub{color:var(--mute);margin:0 0 20px}
.panel{background:var(--panel);border:1px solid var(--line);border-radius:6px;padding:16px;margin-bottom:18px}
.row,.charts{display:grid;gap:18px}
.row{grid-template-columns:minmax(0,1.1fr) minmax(0,1fr)}
.charts{grid-template-columns:1fr 1fr}
@media(max-width:900px){.row,.charts{grid-template-columns:1fr}}
table{border-collapse:collapse;width:100%}
th,td{padding:6px 10px;text-align:left;border-bottom:1px solid var(--line)}
.grid td,.grid th{text-align:center;border:1px solid var(--line);min-width:44px;height:34px;padding:2px 6px}
.grid th.s{text-align:left;white-space:nowrap}
.ctl{display:flex;flex-wrap:wrap;gap:10px;align-items:center}
button,select{font:inherit;padding:5px 12px;border:1px solid var(--line);background:#fff;border-radius:4px;cursor:pointer;color:var(--ink)}
button:focus-visible,select:focus-visible,input:focus-visible{outline:2px solid #2f5fd0;outline-offset:2px}
.wrap{overflow:auto;max-height:640px}
#status{color:var(--mute);min-height:1.5em;margin-top:8px}
svg text{font-family:inherit}
@media(prefers-reduced-motion:reduce){*{transition:none!important}}
</style></head>
<body><main>
<h1>Exam timetable</h1>
<p class="sub" id="sub"></p>

<div class="panel"><h2>Algorithm comparison</h2><div class="wrap"><table id="summary"></table></div></div>

<div class="panel">
  <h2>View and replay an algorithm's assignment order</h2>
  <div class="ctl">
    <label for="alg">Algorithm</label><select id="alg"></select>
    <button id="play">Play</button><button id="step">Step</button><button id="reset">Show all</button>
    <label for="speed">Delay</label><input id="speed" type="range" min="10" max="800" value="150">
  </div>
  <div id="status"></div>
</div>

<div class="row">
  <div class="panel"><h2>Conflict graph</h2><svg id="graph" viewBox="0 0 800 800" width="100%" role="img" aria-label="Conflict graph coloured by slot"></svg></div>
  <div class="panel"><h2>Timetable grid</h2><div class="wrap" id="tt"></div></div>
</div>

<div class="charts">
  <div class="panel"><h2>Slots used as N grows</h2><div id="c1"></div></div>
  <div class="panel"><h2>Running time (ms, log scale) as N grows</h2><div id="c2"></div></div>
</div>
</main>
<script>
window.DATA = /*DATA*/null;
const D = window.DATA, NS = 'http://www.w3.org/2000/svg';
const $ = id => document.getElementById(id);
const slotColor = i => 'hsl(' + ((i * 137.508) % 360).toFixed(1) + ',62%,62%)';
const lab = v => v + D.base;
let cur, idx = [], shown = 0, timer = null, nodeEls = [], cells = [];

$('sub').textContent = D.n + ' courses, ' + D.m + ' conflicts, at most ' + D.R + ' exams per slot. Lower bound: ' + D.lb + ' slots.';

// summary table
let h = '<tr><th>Algorithm</th><th>Slots</th><th>Proven optimal</th><th>Time (ms)</th><th>Note</th></tr>';
D.algs.forEach(a => {
  h += '<tr><td>' + a.name + '</td><td>' + a.slots + '</td><td>' + (a.optimal ? 'Yes' : 'No') + '</td><td>' + a.ms.toFixed(3) + '</td><td>' + a.note + '</td></tr>';
});
$('summary').innerHTML = h;

// graph
const svg = $('graph');
function el(tag, attrs, parent) {
  const e = document.createElementNS(NS, tag);
  for (const k in attrs) e.setAttribute(k, attrs[k]);
  (parent || svg).appendChild(e);
  return e;
}
const X = p => 20 + p[0] * 760, Y = p => 20 + p[1] * 760;
if (D.edges.length) {
  const g = el('g', {stroke: '#9aa6b8', 'stroke-opacity': D.n > 100 ? 0.25 : 0.55, 'stroke-width': 1});
  D.edges.forEach(e => el('line', {x1: X(D.pos[e[0]]), y1: Y(D.pos[e[0]]), x2: X(D.pos[e[1]]), y2: Y(D.pos[e[1]])}, g));
}
const rad = Math.max(4, Math.min(16, 300 / Math.sqrt(Math.max(D.n, 1))));
for (let v = 0; v < D.n; v++) {
  const c = el('circle', {cx: X(D.pos[v]), cy: Y(D.pos[v]), r: rad, fill: '#d9dee7', stroke: '#16202e', 'stroke-width': 1});
  const t = document.createElementNS(NS, 'title'); t.textContent = 'Course ' + lab(v); c.appendChild(t);
  nodeEls.push(c);
  if (D.n <= 120) {
    const tx = el('text', {x: X(D.pos[v]), y: Y(D.pos[v]) + rad * 0.35, 'text-anchor': 'middle', 'font-size': rad * 0.95, 'pointer-events': 'none'});
    tx.textContent = lab(v);
  }
}

function buildTable() {
  const K = cur.slots, lists = [];
  for (let s = 0; s < K; s++) lists.push([]);
  for (let v = 0; v < D.n; v++) lists[cur.slot[v]].push(v);
  let cols = 1;
  lists.forEach(l => { if (l.length > cols) cols = l.length; });
  let t = '<table class="grid"><tr><th class="s">Slot</th>';
  for (let j = 1; j <= cols; j++) t += '<th>Hall ' + j + '</th>';
  t += '</tr>';
  cells = [];
  for (let s = 0; s < K; s++) {
    t += '<tr><th class="s" style="border-left:8px solid ' + slotColor(s) + '">Slot ' + (s + 1) + '</th>';
    for (let j = 0; j < cols; j++) {
      const v = j < lists[s].length ? lists[s][j] : -1;
      t += '<td id="cell' + s + '_' + j + '"></td>';
      if (v >= 0) cells.push([s, j, v]);
    }
    t += '</tr>';
  }
  $('tt').innerHTML = t + '</table>';
}

function update() {
  for (let v = 0; v < D.n; v++) nodeEls[v].setAttribute('fill', idx[v] < shown ? slotColor(cur.slot[v]) : '#d9dee7');
  cells.forEach(c => {
    const e = $('cell' + c[0] + '_' + c[1]);
    const on = idx[c[2]] < shown;
    e.textContent = on ? lab(c[2]) : '';
    e.style.background = on ? slotColor(c[0]) : '';
  });
}

function stop() { if (timer) { clearInterval(timer); timer = null; $('play').textContent = 'Play'; } }
function step() {
  if (shown < D.n) {
    const v = cur.order[shown++];
    $('status').textContent = 'Assigned course ' + lab(v) + ' to slot ' + (cur.slot[v] + 1) + ' (' + shown + ' of ' + D.n + ')';
    update();
  } else stop();
}
function choose(i) {
  stop();
  cur = D.algs[i];
  idx = new Array(D.n);
  cur.order.forEach((v, k) => idx[v] = k);
  shown = D.n;
  buildTable(); update();
  $('status').textContent = cur.name + ': ' + cur.slots + ' slots' + (cur.optimal ? ' (proven optimal)' : '');
}
D.algs.forEach((a, i) => {
  const o = document.createElement('option');
  o.value = i; o.textContent = a.name + ' (' + a.slots + ' slots)';
  $('alg').appendChild(o);
});
$('alg').value = D.algs.length - 1;
$('alg').onchange = () => choose(+$('alg').value);
$('play').onclick = () => {
  if (timer) { stop(); return; }
  if (shown >= D.n) { shown = 0; update(); }
  $('play').textContent = 'Pause';
  timer = setInterval(step, +$('speed').value);
};
$('speed').onchange = () => { if (timer) { clearInterval(timer); timer = setInterval(step, +$('speed').value); } };
$('step').onclick = () => { stop(); if (shown >= D.n) { shown = 0; update(); } step(); };
$('reset').onclick = () => { stop(); shown = D.n; update(); };
choose(D.algs.length - 1);

// charts
function chart(host, key, log) {
  const rows = D.bench;
  if (!rows.length) { host.textContent = 'Benchmark was disabled (--nobench).'; return; }
  const W = 540, H = 320, L = 58, R = 16, T = 16, B = 70;
  const series = [['ws', 'Welsh-Powell', '#d1495b', ''], ['ds', 'DSatur', '#2e7d9a', '7 4'],
                  ['mst', 'DSatur multi-start', '#7a5ea8', '2 3'], ['ex', 'Exact B&B', '#16202e', '']];
  const f = v => log ? Math.log10(Math.max(v, 0.001)) : v;
  let lo = Infinity, hi = -Infinity;
  rows.forEach(r => series.forEach(s => { const v = f(r[s[0]][key]); lo = Math.min(lo, v); hi = Math.max(hi, v); }));
  if (log) { lo = Math.floor(lo); hi = Math.ceil(hi); } else { lo = Math.max(0, Math.floor(lo) - 1); hi = Math.ceil(hi) + 1; }
  if (hi <= lo) hi = lo + 1;
  const xs = rows.map(r => r.n), xmin = Math.min(...xs), xmax = Math.max(...xs);
  const x = n => L + (n - xmin) / ((xmax - xmin) || 1) * (W - L - R);
  const y = v => T + (1 - (f(v) - lo) / (hi - lo)) * (H - T - B);
  let s = '<svg viewBox="0 0 ' + W + ' ' + H + '" width="100%">';
  for (let i = 0; i <= 5; i++) {
    const tv = lo + (hi - lo) * i / 5, py = T + (1 - i / 5) * (H - T - B);
    const label = log ? Math.pow(10, tv).toPrecision(2) : tv.toFixed(0);
    s += '<line x1="' + L + '" x2="' + (W - R) + '" y1="' + py + '" y2="' + py + '" stroke="#e1e6ee"/>';
    s += '<text x="' + (L - 6) + '" y="' + (py + 4) + '" font-size="11" text-anchor="end" fill="#5d6b7e">' + label + '</text>';
  }
  xs.forEach(n => {
    s += '<text x="' + x(n) + '" y="' + (H - B + 16) + '" font-size="11" text-anchor="middle" fill="#5d6b7e">' + n + '</text>';
  });
  s += '<text x="' + ((L + W - R) / 2) + '" y="' + (H - B + 34) + '" font-size="12" text-anchor="middle">Number of courses N</text>';
  series.forEach(se => {
    const pts = rows.map(r => x(r.n) + ',' + y(r[se[0]][key])).join(' ');
    s += '<polyline fill="none" stroke="' + se[2] + '" stroke-width="2" stroke-dasharray="' + se[3] + '" points="' + pts + '"/>';
    rows.forEach(r => {
      const hollow = se[0] === 'ex' && !r.ex.optimal;
      s += '<circle cx="' + x(r.n) + '" cy="' + y(r[se[0]][key]) + '" r="3.5" fill="' + (hollow ? '#fff' : se[2]) + '" stroke="' + se[2] + '"/>';
    });
  });
  series.forEach((se, i) => {
    const lx = L + (i % 2) * 220, ly = H - 26 + Math.floor(i / 2) * 16;
    s += '<line x1="' + lx + '" x2="' + (lx + 24) + '" y1="' + ly + '" y2="' + ly + '" stroke="' + se[2] + '" stroke-width="2" stroke-dasharray="' + se[3] + '"/>';
    s += '<text x="' + (lx + 30) + '" y="' + (ly + 4) + '" font-size="12">' + se[1] + '</text>';
  });
  host.innerHTML = s + '</svg>' + (key === 'slots' ? '<div style="color:#5d6b7e;font-size:13px">Hollow exact points: time limit reached, result not proven optimal.</div>' : '');
}
chart($('c1'), 'slots', false);
chart($('c2'), 'ms', true);
</script></body></html>
""";
}
