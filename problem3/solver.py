"""Core algorithm: min-cost route 1 -> N with <= K stops and <= C half-price coupons.

State = (flights used e, coupons used c, city v). Layered DP, O((K+1) * C * M).
Why this is optimal & handles hidden cases:
  * "at most K stops" == at most K+1 flights; layers are bounded by min(K+1, N-1)
    because with non-negative prices an optimal route never revisits a city.
  * coupons never hurt (p//2 <= p), but we allow "at most C" anyway; c is capped at #flights.
  * parallel edges / opposite edges / self loops are handled per edge index.
  * N == 1 (start == destination) -> cost 0, empty route.
  * unreachable / K<0 -> -1.
"""
INF = float("inf")


def solve(n, edges, K, C):
    """edges: list of (u, v, p). Returns (cost, sel); sel = [(edge_idx, used_coupon), ...]."""
    if n == 1:
        return 0, []
    if K < 0 or C < 0:
        return -1, []
    L = min(K + 1, n - 1)          # max flights worth considering
    Cc = min(C, L)                 # max useful coupons
    live = [(i, u, v, p) for i, (u, v, p) in enumerate(edges) if u != v and v != 1]
    dp = [[[INF] * (n + 1) for _ in range(Cc + 1)] for _ in range(L + 1)]
    par = [[[None] * (n + 1) for _ in range(Cc + 1)] for _ in range(L + 1)]
    dp[0][0][1] = 0
    for e in range(L):
        for c in range(min(e, Cc) + 1):
            cur, nxt0 = dp[e][c], dp[e + 1][c]
            nxt1 = dp[e + 1][c + 1] if c < Cc else None
            for i, u, v, p in live:
                d = cur[u]
                if d == INF:
                    continue
                if d + p < nxt0[v]:
                    nxt0[v] = d + p
                    par[e + 1][c][v] = (c, i, False)
                if nxt1 is not None and d + p // 2 < nxt1[v]:
                    nxt1[v] = d + p // 2
                    par[e + 1][c + 1][v] = (c, i, True)
    best, be, bc = INF, -1, -1
    for e in range(1, L + 1):              # ties -> fewer flights, then fewer coupons
        for c in range(Cc + 1):
            if dp[e][c][n] < best:
                best, be, bc = dp[e][c][n], e, c
    if best == INF:
        return -1, []
    sel, v, e, c = [], n, be, bc
    while e > 0:
        pc, i, used = par[e][c][v]
        sel.append((i, used))
        v, e, c = edges[i][0], e - 1, pc
    sel.reverse()
    return best, sel


def route_nodes(edges, sel, n):
    return [1] + [edges[i][1] for i, _ in sel] if (sel or n > 1) else [1]
