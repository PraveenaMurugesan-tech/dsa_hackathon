"""Animated visualization (matplotlib + networkx)."""
import math


def animate(n, edges, sel, K, C, cost, gif=None, png=None, show=True):
    import matplotlib
    if not show:
        matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import networkx as nx
    from matplotlib.animation import FuncAnimation, PillowWriter
    from matplotlib.lines import Line2D
    from matplotlib.patches import FancyArrowPatch

    G = nx.Graph()
    G.add_nodes_from(range(1, n + 1))
    G.add_edges_from((u, v) for u, v, _ in edges)
    pos = {k: tuple(v) for k, v in nx.spring_layout(G, seed=3, k=1.6 / math.sqrt(n)).items()}
    xs, ys = [p[0] for p in pos.values()], [p[1] for p in pos.values()]
    pad = 0.25

    dirs = {(u, v) for u, v, _ in edges}
    cnt, rad = {}, []
    for u, v, _ in edges:
        k = cnt.get((u, v), 0)
        cnt[(u, v)] = k + 1
        rad.append(0.25 * (k + 1) if ((v, u) in dirs or k) else 0.0)

    m = len(sel)
    selmap = {i: (o, cp) for o, (i, cp) in enumerate(sel)}
    path = [1] + [edges[i][1] for i, _ in sel]
    inter = set(path[1:-1]) - {1, n}
    paid = [edges[i][2] // 2 if cp else edges[i][2] for i, cp in sel]
    fs = 8 if n <= 25 else 6

    fig, ax = plt.subplots(figsize=(9, 7))

    def bez(i, t):
        u, v, _ = edges[i]
        (x1, y1), (x2, y2) = pos[u], pos[v]
        cx, cy = (x1 + x2) / 2 + rad[i] * (y2 - y1), (y1 + y2) / 2 - rad[i] * (x2 - x1)
        return ((1 - t) ** 2 * x1 + 2 * (1 - t) * t * cx + t * t * x2,
                (1 - t) ** 2 * y1 + 2 * (1 - t) * t * cy + t * t * y2)

    def render(done, cur, t):
        ax.clear(); ax.axis("off"); ax.set_aspect("equal")
        ax.set_xlim(min(xs) - pad, max(xs) + pad); ax.set_ylim(min(ys) - pad, max(ys) + pad)
        for i, (u, v, p) in enumerate(edges):
            col, lw, z, lab, lc, fw = "#b5b5b5", 1.2, 1, str(p), "#555", "normal"
            if i in selmap and (selmap[i][0] < done or selmap[i][0] == cur):
                cp = selmap[i][1]
                col, lw, z = ("#e6b800" if cp else "#1f77b4"), 3.4, 3
                if cp:
                    lab, lc, fw = f"{p} → {p // 2}", "#a07800", "bold"
            ax.add_patch(FancyArrowPatch(pos[u], pos[v], connectionstyle=f"arc3,rad={rad[i]}",
                         arrowstyle="-|>", mutation_scale=14, color=col, lw=lw, zorder=z,
                         shrinkA=13, shrinkB=13))
            mx, my = bez(i, 0.5)
            ax.text(mx, my, lab, fontsize=fs, color=lc, fontweight=fw, ha="center", va="center",
                    zorder=5, bbox=dict(fc="white", ec="none", alpha=0.8, pad=0.8))
        for v in range(1, n + 1):
            fc = "#2ca02c" if v == 1 else "#d62728" if v == n else "#ff9f1c" if v in inter else "#dddddd"
            ec = "#d62728" if (n == 1) else "#333"
            ax.scatter(*pos[v], s=650, c=fc, edgecolors=ec, linewidths=2.5 if n == 1 else 1.2, zorder=6)
            ax.text(*pos[v], str(v), ha="center", va="center", fontsize=10, fontweight="bold", zorder=7)
        if cur is not None:
            ax.scatter(*bez(sel[cur][0], t), marker="*", s=380, c="black", zorder=8)
        ds = min(done, m)
        stops = max(0, ds - (1 if ds == m and m else 0))
        used = sum(1 for _, cp in sel[:ds] if cp)
        title = f"Running cost: {sum(paid[:ds])}   |   Stops used: {stops}/{K}   |   Coupons used: {used}/{C}"
        if cost < 0:
            title = "No valid route (unreachable within the stop limit)"
        elif ds == m:
            title += f"\nDestination reached - minimum cost = {cost}"
        ax.set_title(title, fontsize=11)
        if cur is not None or (0 < ds):
            k = cur if cur is not None else ds - 1
            i, cp = sel[k]; u, v, p = edges[i]
            ax.text(0.5, 0.01, f"Flight {u} → {v}: " + (f"coupon!  {p} → {p // 2}" if cp else f"{p}"),
                    transform=ax.transAxes, ha="center", fontsize=11,
                    color="#a07800" if cp else "#1f77b4", fontweight="bold")
        ax.legend(handles=[Line2D([], [], marker="o", ls="", mfc=c, mec="#333", ms=9, label=l) for c, l in
                           [("#2ca02c", "start"), ("#d62728", "destination"), ("#ff9f1c", "stop")]] +
                          [Line2D([], [], color="#e6b800", lw=3, label="coupon flight"),
                           Line2D([], [], color="#1f77b4", lw=3, label="full-price flight")],
                  loc="upper left", fontsize=8, framealpha=0.9)

    SUB = 12
    frames = [(0, None, 0)] * 6
    for s in range(m):
        frames += [(s, s, j / SUB) for j in range(1, SUB)] + [(s + 1, None, 0)] * 3
    frames += [(m, None, 0)] * 15

    anim = FuncAnimation(fig, lambda f: render(*frames[f]), frames=len(frames), interval=80, repeat=False)
    if gif:
        anim.save(gif, writer=PillowWriter(fps=12))
    if png:
        render(m, None, 0); fig.savefig(png, dpi=140, bbox_inches="tight")
    if show:
        plt.show()
    plt.close(fig)
