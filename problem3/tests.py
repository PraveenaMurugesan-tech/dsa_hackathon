import random
from solver import solve
from main import generate


def brute(n, edges, K, C):
    best = [float("inf")]
    def go(v, left, c, cost):
        if v == n: best[0] = min(best[0], cost)
        if left == 0: return
        for u, w, p in edges:
            if u == v:
                go(w, left - 1, c, cost + p)
                if c: go(w, left - 1, c - 1, cost + p // 2)
    go(1, K + 1, C, 0)
    return -1 if best[0] == float("inf") else best[0]


def check(n, e, K, C):
    cost, sel = solve(n, e, K, C)
    assert cost == brute(n, e, K, C), (n, e, K, C, cost)
    if cost >= 0 and n > 1:                          # validate returned route
        assert len(sel) <= K + 1 and sum(cp for _, cp in sel) <= C
        assert sum(e[i][2] // 2 if cp else e[i][2] for i, cp in sel) == cost
        pos = 1
        for i, _ in sel: assert e[i][0] == pos; pos = e[i][1]
        assert pos == n


rnd = random.Random(1)
for _ in range(3000):
    n = rnd.randint(1, 6)
    e = [(rnd.randint(1, n), rnd.randint(1, n), rnd.randint(0, 30)) for _ in range(rnd.randint(0, 12))]
    check(n, e, rnd.randint(-1, 4), rnd.randint(0, 4))
assert solve(1, [], 0, 0)[0] == 0
assert solve(3, [(1, 2, 5)], 5, 5)[0] == -1
assert solve(2, [(1, 2, 7), (1, 2, 9)], 0, 1)[0] == 3          # parallel edges + coupon rounding
assert solve(3, [(1, 2, 1), (2, 3, 1), (1, 3, 100)], 0, 0)[0] == 100   # K=0 -> direct only
for s in range(50):
    n, K, C, e = generate(8, 2, 1, 12, s); assert solve(n, e, K, C)[0] >= 0
print("all tests passed")
