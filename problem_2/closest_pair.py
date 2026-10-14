"""Compare brute-force and divide-and-conquer closest-pair algorithms."""
import math
import random
import sys
import time

BRUTE_FORCE_LIMIT = 30000


def brute_force(points):
    """Check every pair. Return (distance, point1, point2), or None if needed."""
    if len(points) < 2:
        return None

    best_distance, point1, point2 = min(
        (
            (math.hypot(a[0] - b[0], a[1] - b[1]), a, b)
            for i, a in enumerate(points) for b in points[i + 1:]
        ),
        key=lambda pair: pair[0],
    )
    return best_distance, min(point1, point2), max(point1, point2)


def divide_and_conquer(points, info=None):
    """Find the closest pair in O(n log n); optionally record plot details."""
    if len(points) < 2:
        return None

    points_by_x = sorted(points)

    def solve(start, end, is_root=False):
        size = end - start
        if size <= 3:
            result = brute_force(points_by_x[start:end])
            points_by_x[start:end] = sorted(
                points_by_x[start:end], key=lambda point: point[1]
            )
            return result

        middle = start + size // 2
        middle_x = points_by_x[middle][0]
        best = min(
            (solve(start, middle), solve(middle, end)), key=lambda result: result[0]
        )

        points_by_x[start:end] = sorted(
            points_by_x[start:end], key=lambda point: point[1]
        )
        best_distance = best[0]

        if is_root and info is not None:
            info["mid_x"], info["d"] = middle_x, best_distance
        if best_distance == 0:
            return best

        strip = [
            point for point in points_by_x[start:end]
            if abs(point[0] - middle_x) < best_distance
        ]
        for i, (x1, y1) in enumerate(strip[:-1]):
            for j in range(i + 1, len(strip)):
                x2, y2 = strip[j]
                if y2 - y1 >= best_distance:
                    break
                candidate = math.hypot(x1 - x2, y1 - y2)
                if candidate < best_distance:
                    best_distance = candidate
                    best = (candidate, strip[i], strip[j])
        return best

    distance, point1, point2 = solve(0, len(points_by_x), is_root=True)
    return distance, min(point1, point2), max(point1, point2)


def gen_points(n, lo=0, hi=1000):
    """Generate points with a mix of integer and decimal coordinates."""
    points = []
    for _ in range(n):
        if random.random() < 0.5:
            points.append((random.randint(lo, hi), random.randint(lo, hi)))
        else:
            x = round(random.uniform(lo, hi), 2)
            y = round(random.uniform(lo, hi), 2)
            points.append((x, y))
    return points


def num(s):
    """Parse a coordinate as an integer when possible, otherwise as a float."""
    try:
        return int(s)
    except ValueError:
        return float(s)


def fmt_pt(point):
    return "(%s, %s)" % (point[0], point[1])


def fmt(result):
    if result is None:
        return "N/A (fewer than 2 points)"
    distance, point1, point2 = result
    return "%.6f %s %s" % (distance, fmt_pt(point1), fmt_pt(point2))


def timed(function, *args):
    start = time.perf_counter()
    result = function(*args)
    elapsed_ms = (time.perf_counter() - start) * 1000
    return result, elapsed_ms


def agree(first, second):
    if first is None or second is None:
        return first is second
    return math.isclose(first[0], second[0], rel_tol=1e-9, abs_tol=1e-9)


def verify(trials=1000, out=sys.stderr):
    """Compare both algorithms on fixed edge cases and random inputs."""
    randomizer = random.Random(7)
    cases = [
        [], [(1, 1)], [(0, 0), (3, 4)], [(2, 2)] * 6,
        [(i, 0) for i in range(50)], [(0, i * 1.5) for i in range(50)],
        [(i, i) for i in range(50)], [(5, 5), (1, 1), (5, 5), (9, 9)],
        [(0, 0), (0, 0), (0, 0)], [(-5, -5), (5, 5), (-5, 5), (5, -5)],
        [(1e9, 1e9), (1e9 + 1, 1e9)], [(0.1, 0.2), (0.1, 0.2000001)],
    ]
    for _ in range(trials):
        count = randomizer.randint(0, 80)
        limit = randomizer.choice([3, 10, 100, 10 ** 6])
        if randomizer.random() < 0.5:
            points = [(randomizer.randint(-limit, limit), randomizer.randint(-limit, limit))
                      for _ in range(count)]
        else:
            points = [
                (randomizer.uniform(-limit, limit), randomizer.uniform(-limit, limit))
                for _ in range(count)
            ]
        cases.append(points)
    for points in cases:
        if not agree(brute_force(points), divide_and_conquer(points)):
            print("MISMATCH:", points, file=out)
            return False
    print("Verified %d cases: both algorithms agree." % len(cases), file=out)
    return True


def main():
    args = sys.argv[1:]
    if "--seed" in args:
        random.seed(int(args[args.index("--seed") + 1]))

    tokens = sys.stdin.read().split()
    if not tokens:
        print("N/A (no input)")
        return
    try:
        n = int(float(tokens[0]))
    except ValueError:
        print("Invalid N")
        return
    n = max(n, 0)
    coordinates = tokens[1:]
    if n and len(coordinates) >= 2 * n:
        points = [(num(coordinates[i]), num(coordinates[i + 1]))
                  for i in range(0, 2 * n, 2)]
    else:
        points = gen_points(n)

    info = {}
    bf, t_bf = (timed(brute_force, points) if n <= BRUTE_FORCE_LIMIT
                else (None, float("nan")))
    dc, t_dc = timed(divide_and_conquer, points, info)

    print(fmt(bf) if n <= BRUTE_FORCE_LIMIT
          else "SKIPPED (N too large for O(n^2)) " + fmt(dc))
    print(fmt(dc))
    print("Brute-Force: %.3f ms | Divide-and-Conquer: %.3f ms" % (t_bf, t_dc))

    if "--verify" in args:
        verify()
        if n <= BRUTE_FORCE_LIMIT:
            print("[verify] this run agrees:", agree(bf, dc), file=sys.stderr)
    if "--plot" in args and n >= 2:
        import visualize
        visualize.make_all(points, bf, dc, info)


if __name__ == "__main__":
    main()
