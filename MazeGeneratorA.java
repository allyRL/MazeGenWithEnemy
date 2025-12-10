
import java.util.Random;
import java.util.Scanner;

public class MazeGeneratorA {

    public static class UnionFind {
        public int[] parent;

        // union-find
        public UnionFind(int size) {
            parent = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        // path compression
        public int find(int p) {
            if (parent[p] != p) {
                parent[p] = find(parent[p]);
            }
            return parent[p];
        }

        // unite two elements if not already connected
        public boolean union(int p, int q) {
            int rootP = find(p);
            int rootQ = find(q);

            if (rootP == rootQ) {
                return false;
            }
            parent[rootQ] = rootP;
            return true;
        }
    }
    public static boolean[][][] generateMaze(int rows, int cols) {
        UnionFind uf = new UnionFind(rows * cols);
        boolean[][] rightWalls = new boolean[rows][cols]; // right walls grid
        boolean[][] bottomWalls = new boolean[rows][cols]; // bottom walls grid
        int[][] walls = new int[(rows * cols) * 2][3]; // store walls

        int wallCount = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                rightWalls[i][j] = true;  // right and bottom walls are generated
                bottomWalls[i][j] = true;

                // adds right wall if not last column
                if (j < cols - 1) {
                    walls[wallCount++] = new int[]{(i * cols) + j, (i * cols) + j + 1, 1};
                }
                // adds bottom wall if not last row
                if (i < rows - 1) {
                    walls[wallCount++] = new int[]{(i * cols) + j, (i + 1) * cols + j, 0};
                }
            }
        }

        // I used fisher-yates algorithm to shuffle walls
        // potential problem: if random number gen is not truly random, the shuffle may be biased (some arrangements will be more likely than others)
        Random random = new Random();
        for (int i = wallCount - 1; i > 0; i--) {
            int j = random.nextInt(i + 1); // Pick a random index from 0 to i
            int[] temp = walls[i];
            walls[i] = walls[j]; // swaps walls [i] and [j]
            walls[j] = temp;
        }

        for (int k = 0; k < wallCount; k++) {
            int cell1 = walls[k][0];
            int cell2 = walls[k][1];
            boolean isRightWall = walls[k][2] == 1;

            // connect cells if they are not already connected
            boolean connected = uf.union(cell1, cell2);

            if (connected) { // if cells were successfully connected
                int row = cell1 / cols;
                int col = cell1 % cols;

                // determine which wall to knock down
                if (isRightWall) {
                    rightWalls[row][col] = false; // knock down right wall
                } else {
                    bottomWalls[row][col] = false; // knock down bottom wall
                }
            }
        }

        return new boolean[][][]{rightWalls, bottomWalls};
    }

    public static void printMaze(boolean[][] rightWalls, boolean[][] bottomWalls, int rows, int cols) {
        StringBuilder maze = new StringBuilder(); // uses strings to construct maze

        // creates the top with top-left entrance
        maze.append("+"); // " + " is used for corners and connecting walls (before they are knocked down)
        maze.append("   +"); // entrance space
        maze.append("---+".repeat(cols - 1)).append("\n"); // adds walls to the right of the entrance

        for (int i = 0; i < rows; i++) {
            StringBuilder topRow = new StringBuilder("|"); // walls are indicated by "|"
            StringBuilder bottomRow = new StringBuilder("+"); // " + " is used for corners and connecting walls (before they are knocked down)

            for (int j = 0; j < cols; j++) {
                topRow.append(rightWalls[i][j] ? "   |" : "    "); // this is what the right wall will look like when printed
                bottomRow.append(bottomWalls[i][j] ? "---+" : "   +"); // this is what the bottom wall will look like when printed
            }

            maze.append(topRow).append("\n").append(bottomRow).append("\n");
        }

        maze.replace(maze.lastIndexOf("---"), maze.lastIndexOf("---") + 3, "   "); // creates an exit in the lower-right of the maze

        System.out.println(maze);
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in); // inputs for the N x M grid of cells
        System.out.print("Enter number of rows: \n");
        int rows = scanner.nextInt();
        System.out.print("Enter number of columns: \n");
        int cols = scanner.nextInt();
        boolean[][][] walls = generateMaze(rows, cols);
        printMaze(walls[0], walls[1], rows, cols);
    }
}