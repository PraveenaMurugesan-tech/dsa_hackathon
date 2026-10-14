#!/usr/bin/env python3
"""Budget Traveller with Coupons.  python main.py [--mode 1|2|3] [options]"""
import argparse, csv, random, sys
from solver import solve


def die(msg):
    sys.exit(f"Input error: {msg}")


def validate(n, K, C, edges):
    if n < 1: die("N must be >= 1")
    if K < 0 or C < 0: die("K and C must be non-negative")
    for u, v, p in edges:
        if not (1 <= u <= n and 1 <= v <= n): die(f"city out of range in flight {u} {v} {p}")
        if p < 0: die(f"negative price in flight {u} {v} {p}")


def read_stdin():                                   # Option 1
    if sys.stdin.isatty():
        print("Enter: N M / K C / then M lines 'u v p'", file=sys.stderr)
    t = sys.stdin.read().split()
    try:
        n, m, K, C = map(int, t[:4])
        nums = list(map(int, t[4:4 + 3 * m]))
    except ValueError:
        die("non-integer token")
    if len(nums) < 3 * m: die("fewer flights than M")
    edges = [tuple(nums[i:i + 3]) for i in range(0, 3 * m, 3)]
    validate(n, K, C, edges)
    return n, K, C, edges


def read_csv(path, K=None, C=None, n=None):         # Option 2
    edges, kc = [], None
    with open(path, newline="") as f:
        for row in csv.reader(f):
            row = [x.strip() for x in row]
            if not any(row): continue
            try:
                nums = [int(x) for x in row[:3]]
            except ValueError:
                continue                            # header line
            if len(row) == 2 and not edges and kc is None:
                kc = nums[:2]
            elif len(nums) == 3:
                edges.append(tuple(nums))
    if K is None: K = kc[0] if kc else int(input("K (max stops): "))
    if C is None: C = kc[1] if kc else int(input("C (coupons): "))
    if n is None:
        n = max([max(u, v) for u, v, _ in edges], default=1)
    validate(n, K, C, edges)
    return n, K, C, edges


def generate(n, K, C, m=None, seed=None, maxp=100):  # Option 3
    rnd = random.Random(seed)
    if n < 2: die("generator needs N >= 2")
    L = rnd.randint(1, min(K + 1, n - 1))           # planted feasible route: <= K stops
    mids = rnd.sample(range(2, n), L - 1) if L > 1 else []
    chain = [1] + mids + [n]
    es = {(chain[i], chain[i + 1]) for i in range(L)}
    m = min(max(m if m else 3 * n, len(es)), n * (n - 1))
    while len(es) < m:
        u, v = rnd.randint(1, n), rnd.randint(1, n)
        if u != v: es.add((u, v))
    edges = [(u, v, rnd.randint(1, maxp)) for u, v in es]
    rnd.shuffle(edges)
    return n, K, C, edges


def write_input_csv(path, K, C, edges):
    with open(path, "w", newline="") as f:
        w = csv.writer(f); w.writerow([K, C]); w.writerow(["u", "v", "price"]); w.writerows(edges)


def write_result_csv(path, edges, sel):
    chosen = {i: cp for i, cp in sel}
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["from", "to", "price", "on_path", "coupon_used", "price_paid"])
        for i, (u, v, p) in enumerate(edges):
            on = i in chosen
            cp = on and chosen[i]
            w.writerow([u, v, p, "x" if on else "", "x" if cp else "", (p // 2 if cp else p) if on else ""])


def main():
    a = argparse.ArgumentParser()
    a.add_argument("--mode", type=int, choices=[1, 2, 3])
    a.add_argument("--csv"); a.add_argument("--n", type=int); a.add_argument("--m", type=int)
    a.add_argument("--k", type=int); a.add_argument("--c", type=int); a.add_argument("--seed", type=int)
    a.add_argument("--out", default="result.csv"); a.add_argument("--gif", default="journey.gif")
    a.add_argument("--png", default="path.png")
    a.add_argument("--no-anim", action="store_true"); a.add_argument("--show", action="store_true")
    a = a.parse_args()
    mode = a.mode or int(input("Input option [1=stdin, 2=CSV, 3=generate]: "))
    if mode == 1:
        n, K, C, edges = read_stdin()
    elif mode == 2:
        n, K, C, edges = read_csv(a.csv or input("CSV path: "), a.k, a.c, a.n)
    else:
        n = a.n or int(input("N: ")); K = a.k if a.k is not None else int(input("K: "))
        C = a.c if a.c is not None else int(input("C: "))
        n, K, C, edges = generate(n, K, C, a.m, a.seed)
        write_input_csv("graph_generated.csv", K, C, edges)
        print("Generated graph saved to graph_generated.csv", file=sys.stderr)

    cost, sel = solve(n, edges, K, C)
    print(cost)                                     # Output 1
    write_result_csv(a.out, edges, sel)             # Output 2
    if cost >= 0:
        route = [1] + [edges[i][1] for i, _ in sel]
        print("Route :", " -> ".join(map(str, route)), file=sys.stderr)
        for i, cp in sel:
            u, v, p = edges[i]
            print(f"  {u}->{v}: {p}" + (f" -> {p // 2} (coupon)" if cp else ""), file=sys.stderr)
    if not a.no_anim:
        from viz import animate
        animate(n, edges, sel, K, C, cost, gif=a.gif, png=a.png, show=a.show)
        print(f"Saved {a.out}, {a.gif}, {a.png}", file=sys.stderr)


if __name__ == "__main__":
    main()
