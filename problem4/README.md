# Exam Timetable Scheduling (Java)

Graph colouring with a capacity limit: no two conflicting courses share a slot, and each slot holds at most R exams.

## Build and run (Java 17+)
    javac ExamScheduler.java

    # Option 1: file with "N M R" then M edges (1-indexed or 0-indexed, auto-detected)
    java ExamScheduler sample_input.txt

    # Option 2: first line "N R", random conflict graph with given density
    echo "40 6" | java ExamScheduler -d 0.25 --seed 42

Options: `-d density`, `-s seed`, `-t exact-time-limit-seconds` (default 10), `-o outdir` (default `output`),
`--nobench`, `--bench-time sec`, `--bench-max maxN`. Input can also be piped through stdin.

## Algorithms
- Welsh-Powell (capacity aware): fills one slot at a time by decreasing degree.
- DSatur (capacity aware): most saturated course first, lowest feasible slot.
- DSatur multi-start: randomised tie-breaking, keeps the best.
- Exact branch and bound: fail-first DSatur ordering, one new slot opened at a time (symmetry breaking),
  capacity bound (courses that cannot fit in free hall space force new slots), lower bound = max(ceil(N/R), clique).
  Seeded with the best heuristic. If the time limit is hit it reports "No" for proven optimal.

## Outputs (in the output folder)
- Console: slots per algorithm, proven-optimal flag, time, final slot-wise list
- `slots.txt` (Slot 1: 2, 5, 9), `timetable.csv` (rows = slots, columns = halls), `results.txt`, `benchmark.csv`
- `report.html`: open in a browser. Coloured conflict graph, coloured timetable grid, slots/time charts vs N,
  and an animation (Play/Step) of the assignment order for any algorithm.

## Edge cases handled
N=0, no edges, R=1, R larger than N, duplicate edges, self-loops (ignored with warning), 0- or 1-indexed
vertices, out-of-range endpoints (clear error), density given as 0.3 or 30, fewer edge lines than M.
Course numbers in the output use the same indexing as the input.
