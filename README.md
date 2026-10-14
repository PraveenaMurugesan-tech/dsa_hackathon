# CSE Hackathon on Data Structures and Algorithms

**CMR Institute of Technology, Bangalore**

**Date:** 14 October 2026

## About the Project

This repository contains implementations of Data Structures and Algorithms (DSA) problems for the CSE Hackathon. The project focuses on algorithm design, optimization, complexity analysis, testing, and visualization.

## Problems

### 1. Maze with Keys and Doors

Find the shortest path through a maze containing walls, keys, and locked doors using BFS and key-state tracking.

#### Build and run

From the repository root, compile and run the Java implementation:

```text
javac *.java
java Main
```

The program prompts for the number of key/door types (`T`, from 1 to 6), then asks whether to load a CSV (`1`) or generate a random maze (`2`).

To load the sample maze without opening the visualizer, run `java Main --no-gui` and enter:

```text
3
1
sample_maze.csv
```

Or run it with piped input:

```text
echo "3 1 sample_maze.csv" | java Main --no-gui
```

To generate a random 25 × 25 maze with a repeatable seed:

```text
echo "4 2 25" | java Main --seed=42 --no-gui
```

The shortest path length (or `-1` if no solution exists) is printed to standard output. The route is saved to `maze_solution.csv`; generated mazes are saved to `maze_generated.csv`.

Run the Java self-tests with:

```text
java MazeTests
```

### 2. Closest Pair of Points

Find the closest pair of points in a 2D plane using:

- Brute Force — `O(N²)`
- Divide and Conquer — `O(N log N)`

Includes performance benchmarking, corner-case testing, and graphical visualization.

### 3. Budget Traveller with Coupons

Find the minimum flight cost subject to stop limits and discount coupons using constrained shortest-path algorithms.

### 4. Exam Timetable Scheduling

Schedule exams without conflicts while respecting hall capacity, using greedy graph colouring and exact optimization algorithms.

## Technologies

- Python
- Java
- Git and GitHub
- Matplotlib and NetworkX
- CSV for data input and output

## Repository Structure

```text
.
├── Main.java
├── Maze.java
├── MazeGenerator.java
├── MazeSolver.java
├── MazeTests.java
├── MazeVisualizer.java
├── CsvUtil.java
├── sample_maze.csv
└── problem_2/
```

Problem implementations, tests, and relevant visualizations are organized in the repository root or their respective problem folders.

## Key Features

- Multiple algorithmic approaches
- Time and space complexity analysis
- Corner-case handling and correctness verification
- Performance benchmarking
- Algorithm visualizations
- Comparative analysis of solutions

## Objective

To apply fundamental DSA concepts to practical computational problems and demonstrate algorithmic correctness, efficiency, and optimization.

---

**Event:** CSE Hackathon on Data Structures and Algorithms

**Institution:** CMR Institute of Technology, Bangalore

**Date:** 14 October 2026
