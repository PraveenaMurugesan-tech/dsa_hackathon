# Maze with Keys and Doors (Java)

## Build & run
    javac *.java
    java Main                 # interactive via stdin (prompts go to stderr, stdout = answer only)
    java MazeTests            # self-tests (~11k checks)

## Input (stdin)
    T                 # key/door types, 1..6
    1 <csv path>      # option 1: read grid from CSV   e.g.  1 sample_maze.csv
    2 <N>             # option 2: random solvable N x N maze

    echo "3 1 sample_maze.csv" | java Main --no-gui
    echo "4 2 25"              | java Main --seed=42

## Output
- stdout: shortest path length, or -1
- maze_solution.csv: route marked with 'x'. S, E, keys and doors on the route keep their
  letters, so picked-up keys / opened doors stay identifiable.
- maze_generated.csv: the random maze (option 2)

## Flags
--no-gui (skip the window; also automatic on headless machines), --out=FILE, --gen-out=FILE, --seed=N

## Notes
- Exit is 'E' and door type E is also 'E'. Rule: one 'E' = exit; several = the first in
  row-major order is the exit, the rest are doors. The generator only uses door E when T = 6.
- A door whose key is absent anywhere in the grid is treated as impassable.
- 'x' cells are read as open, so a solved CSV can be fed back in.
