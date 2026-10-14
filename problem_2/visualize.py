"""Plots + animation for the closest-pair problem (needs matplotlib; pillow for GIF)."""
import math
import random
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, PillowWriter
from closest_pair import brute_force, divide_and_conquer, gen_points, timed


def plot_points(pts, bf, dc, info, path="closest_pair_plot.png"):
    fig, ax = plt.subplots(figsize=(9, 7))
    xs, ys = zip(*pts)
    ax.scatter(xs, ys, s=14, c="gray", alpha=0.7, label="points")
    if "mid_x" in info:
        mx, d = info["mid_x"], info["d"]
        ax.axvline(mx, color="purple", ls="--", label="dividing line")
        ax.axvspan(mx - d, mx + d, color="orange", alpha=0.25,
                   label="strip (width 2d, d=%.4f)" % d)
    if bf is not None:
        ax.plot([bf[1][0], bf[2][0]], [bf[1][1], bf[2][1]], c="red", lw=5, alpha=0.6,
                marker="o", ms=9, label="Brute Force: %.4f" % bf[0])
    ax.plot([dc[1][0], dc[2][0]], [dc[1][1], dc[2][1]], c="blue", lw=2,
            marker="x", ms=9, label="Divide & Conquer: %.4f" % dc[0])
    ax.set_title("Closest Pair of Points (N=%d)" % len(pts))
    ax.set_xlabel("x"); ax.set_ylabel("y"); ax.grid(alpha=0.3); ax.legend()
    fig.savefig(path, dpi=150, bbox_inches="tight"); plt.close(fig)


def plot_runtime(sizes=(100, 200, 400, 800, 1600, 3200, 6400), path="runtime_plot.png"):
    random.seed(0)
    tb, td = [], []
    for n in sizes:
        pts = gen_points(n)
        tb.append(timed(brute_force, pts)[1])
        td.append(timed(divide_and_conquer, pts)[1])
    fig, axs = plt.subplots(1, 2, figsize=(13, 5))
    for ax, log in zip(axs, (False, True)):
        ax.plot(sizes, tb, "r-o", label="Brute Force  O(n^2)")
        ax.plot(sizes, td, "b-o", label="Divide & Conquer  O(n log n)")
        ax.set_xlabel("N"); ax.set_ylabel("time (ms)"); ax.grid(alpha=0.3, which="both")
        if log:
            ax.set_xscale("log"); ax.set_yscale("log"); ax.set_title("Running time vs N (log-log)")
        else:
            ax.set_title("Running time vs N")
        ax.legend()
    fig.savefig(path, dpi=150, bbox_inches="tight"); plt.close(fig)
    return sizes, tb, td


def animate(pts, path="divide_conquer_animation.gif"):
    """Animate recursive splitting (dashed lines) and merging (strip + best pair)."""
    pts = sorted(pts)
    frames = []   # (kind, lo, hi, midx, d, pair)

    def rec(lo, hi):
        if hi - lo <= 3:
            r = brute_force(pts[lo:hi]) if hi - lo >= 2 else None
            frames.append(("base", lo, hi, None, r[0] if r else math.inf, r))
            return r
        mid = (lo + hi) // 2
        midx = pts[mid][0]
        frames.append(("split", lo, hi, midx, None, None))
        a, b = rec(lo, mid), rec(mid, hi)
        best = min((x for x in (a, b) if x), key=lambda r: r[0])
        d = best[0]
        strip = sorted((p for p in pts[lo:hi] if abs(p[0] - midx) < d), key=lambda p: p[1])
        for i in range(len(strip)):
            for j in range(i + 1, len(strip)):
                if strip[j][1] - strip[i][1] >= d:
                    break
                dd = math.dist(strip[i], strip[j])
                if dd < d:
                    d, best = dd, (dd, strip[i], strip[j])
        frames.append(("merge", lo, hi, midx, d, best))
        return best

    rec(0, len(pts))
    xs, ys = zip(*pts)
    fig, ax = plt.subplots(figsize=(7, 6))

    def draw(k):
        ax.clear()
        kind, lo, hi, midx, d, pair = frames[k]
        ax.scatter(xs, ys, c="lightgray", s=18)
        seg = pts[lo:hi]
        sx, sy = zip(*seg)
        ax.scatter(sx, sy, c="tab:blue", s=30)
        ax.axvspan(sx[0], sx[-1], color="tab:blue", alpha=0.07)
        if kind == "split":
            ax.axvline(midx, color="purple", ls="--")
            ax.set_title("DIVIDE: split %d points at x=%.1f" % (hi - lo, midx))
        elif kind == "base":
            ax.set_title("BASE CASE: brute force on %d points" % (hi - lo))
        else:
            ax.axvline(midx, color="purple", ls="--")
            ax.axvspan(midx - d, midx + d, color="orange", alpha=0.35)
            ax.set_title("MERGE: strip width 2d (d=%.2f)" % d)
        if pair:
            ax.plot([pair[1][0], pair[2][0]], [pair[1][1], pair[2][1]], "r-o", lw=3)
        ax.set_xlim(min(xs) - 5, max(xs) + 5); ax.set_ylim(min(ys) - 5, max(ys) + 5)

    ani = FuncAnimation(fig, draw, frames=len(frames), interval=900)
    ani.save(path, writer=PillowWriter(fps=1.2))
    plt.close(fig)


def make_all(pts, bf, dc, info):
    plot_points(pts, bf, dc, info)
    plot_runtime()
    random.seed(3)
    animate(gen_points(24, 0, 100))
    print("Saved: closest_pair_plot.png, runtime_plot.png, divide_conquer_animation.gif",
          file=__import__("sys").stderr)
