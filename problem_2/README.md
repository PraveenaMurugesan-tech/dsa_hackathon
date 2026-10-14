# Closest Pair of Points: Brute Force vs Divide & Conquer

## Run
```
pip install -r requirements.txt
echo 1000 | python closest_pair.py                  # 3-line output
echo 1000 | python closest_pair.py --plot --verify  # + plots, animation, stress test
python test_closest_pair.py                         # unit + stress tests
```
Input: `N` (random points generated) or `N x1 y1 ... xN yN` (your own points).

## Output (stdout, exactly 3 lines)
```
<BF distance 6dp> (x1, y1) (x2, y2)
<D&C distance 6dp> (x1, y1) (x2, y2)
Brute-Force: <ms> ms | Divide-and-Conquer: <ms> ms
```

## Approach
- **Brute force** O(n^2): check all pairs using `math.hypot` to avoid squared-distance underflow.
- **Divide & conquer** O(n log n): sort by x once; split at median; solve halves;
  d = min(dL, dR); scan only the strip |x - midX| < d in y-order, each point vs the next
  few points with y-gap < d (at most ~7). Halves are merged by y during recursion,
  so no re-sorting per level. Early exit when d = 0 (duplicates).
- **Corner cases:** N<2 -> "N/A", N=2, duplicates, collinear, same x / same y, negatives, floats.
- **Verification:** 1000+ random/corner cases, both must give identical min distance.
- Brute force is skipped above N = 30000 (O(n^2)) so large tests don't time out.

## Files
- `closest_pair.py`  algorithms + CLI
- `visualize.py`     plots + animation
- `test_closest_pair.py`  tests
- `closest_pair_plot.png`, `runtime_plot.png`, `divide_conquer_animation.gif`  sample outputs
