import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Scanner;
import javax.swing.SwingUtilities;

/**
 * Maze with Keys and Doors.
 *
 * stdin (prompts go to stderr, so stdout carries ONLY the answer):
 *   T                      number of key/door types (1..6)
 *   1  <csv path>          option 1: read the grid from a CSV file
 *   2  <N>                 option 2: generate a random solvable N x N maze
 *
 * stdout : the shortest path length, or -1
 * files  : maze_solution.csv (route marked with 'x'), maze_generated.csv (option 2)
 *
 * flags  : --no-gui  --out=<file>  --gen-out=<file>  --seed=<long>
 */
public final class Main {
    public static void main(String[] args) {
        boolean gui = true;
        String out = "maze_solution.csv";
        String genOut = "maze_generated.csv";
        Long seed = null;
        for (String a : args) {
            if (a.equals("--no-gui")) gui = false;
            else if (a.startsWith("--out=")) out = a.substring(6);
            else if (a.startsWith("--gen-out=")) genOut = a.substring(10);
            else if (a.startsWith("--seed=")) seed = Long.parseLong(a.substring(7));
        }

        try {
            Scanner in = new Scanner(System.in);
            System.err.println("Enter T (number of key/door types, 1-6):");
            int t = Integer.parseInt(in.next().trim());
            if (t < 1 || t > 6) throw new IllegalArgumentException("T must be between 1 and 6");

            System.err.println("Option: 1 = read grid from CSV, 2 = generate random grid:");
            String opt = in.next().trim().toLowerCase();

            char[][] grid;
            if (opt.equals("1") || opt.equals("csv")) {
                System.err.println("CSV file path:");
                String path = "";
                while (path.isEmpty()) path = in.nextLine().trim();
                if (path.length() > 1 && path.startsWith("\"") && path.endsWith("\""))
                    path = path.substring(1, path.length() - 1);
                grid = CsvUtil.read(path);
            } else if (opt.equals("2") || opt.equals("random")) {
                System.err.println("Grid size N:");
                int n = Integer.parseInt(in.next().trim());
                Random rnd = seed == null ? new Random() : new Random(seed);
                grid = MazeGenerator.generate(n, t, rnd);
                CsvUtil.write(genOut, grid);
                System.err.println("Generated maze saved to " + genOut);
            } else {
                throw new IllegalArgumentException("Option must be 1 or 2");
            }

            final Maze maze = new Maze(grid);
            final MazeSolver.Result result = MazeSolver.solve(maze);

            System.out.println(result.length);
            System.out.flush();

            CsvUtil.write(out, MazeSolver.solutionGrid(maze, result));
            System.err.println("Solution CSV saved to " + out);

            if (gui && !GraphicsEnvironment.isHeadless()) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        new MazeVisualizer(maze, result).show();
                    }
                });
            }
        } catch (NoSuchElementException e) {
            System.err.println("Error: missing input");
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("Error: expected a number (" + e.getMessage() + ")");
            System.exit(1);
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
