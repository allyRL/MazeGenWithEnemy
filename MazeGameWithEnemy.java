import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
// inspired by maze generator made in CIS36A in Spring2024. Uses some previous code.
// MazeGameWithEnemy - version with expansion rules:
// first 4 collisions -> horizontal expansions (add columns to right)
// next 3 collisions  -> vertical expansions (add rows at bottom)
// after 3 vertical expansions/ 7 total collisions -> Game Over
public class MazeGameWithEnemy {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameFrame frame = new GameFrame();
            frame.setVisible(true);
        });
    }
}

// Frame
class GameFrame extends JFrame {
    public GameFrame() {
        setTitle("Maze");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // prompt for size
        int rows = 15, cols = 15;
        try {
            String r = JOptionPane.showInputDialog(this, "Rows (e.g. 15):", "15");
            String c = JOptionPane.showInputDialog(this, "Cols (e.g. 15):", "15");
            if (r != null) rows = Math.max(4, Integer.parseInt(r));
            if (c != null) cols = Math.max(4, Integer.parseInt(c));
        } catch (Exception ex) {
            // keep defaults if parse fails
        }

        GamePanel panel = new GamePanel(rows, cols);
        JScrollPane scroll = new JScrollPane(panel);
        add(scroll);
        pack();
        setLocationRelativeTo(null);
    }
}

// Cell
class Cell {
    boolean rightWall = true;   // wall to the right of the cell
    boolean bottomWall = true;  // wall to the bottom of the cell
}

// Enemy
class Enemy {
    int row, col;
    public Enemy(int r, int c) { row = r; col = c; }
}

// Maze generator: randomized DFS with controlled loops
class MazeGenerator {
    // returns rows x cols maze
    public static Cell[][] generateMaze(int rows, int cols){
        Cell[][] maze = new Cell[rows][cols];
        for(int r=0; r<rows; r++)
            for(int c=0; c<cols; c++)
                maze[r][c] = new Cell();


        // 1. generate perfect maze (DFS/Union-Find)

        UnionFind uf = new UnionFind(rows * cols);
        Random rand = new Random();

        int[][] walls = new int[(rows * cols) * 2][3];
        int wc = 0;

        for(int r = 0; r < rows; r++) {
            for(int c = 0; c < cols; c++) {
                if(c < cols - 1) walls[wc++] = new int[]{r*cols + c, r*cols + c + 1, 1};
                if(r < rows - 1) walls[wc++] = new int[]{r*cols + c, (r+1)*cols + c, 0};
            }
        }

        // shuffle
        for(int i = wc - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int[] t = walls[i]; walls[i] = walls[j]; walls[j] = t;
        }

        // carve perfect maze
        for(int i = 0; i < wc; i++) {
            int a = walls[i][0];
            int b = walls[i][1];
            boolean right = walls[i][2] == 1;

            if(uf.union(a, b)) {
                int r = a / cols;
                int c = a % cols;
                if(right) maze[r][c].rightWall = false;
                else      maze[r][c].bottomWall = false;
            }
        }

        // 2. loop creation

        int softLoops = (rows * cols) / 12;  // sparse & safe
        for(int i = 0; i < softLoops; i++){
            int r = rand.nextInt(rows - 1);
            int c = rand.nextInt(cols - 1);

            // small chance to carve, keeping structure intact, not too many openings
            if(rand.nextInt(100) < 30) {
                maze[r][c].rightWall = false;
            }
            if(rand.nextInt(100) < 30) {
                maze[r][c].bottomWall = false;
            }
        }

        // Main corridors

        int midR = rows / 2;
        int midC = cols / 2;

        // horizontal central corridor
        for(int c = 0; c < cols - 1; c++){
            if(rand.nextInt(100) < 35)
                maze[midR][c].rightWall = false;
        }

        // vertical central corridor
        for(int r = 0; r < rows - 1; r++){
            if(rand.nextInt(100) < 35)
                maze[r][midC].bottomWall = false;
        }

        // Remove accidental 2×2 solid blocks, still occurs don't know how to fix

        for(int r = 0; r < rows - 1; r++){
            for(int c = 0; c < cols - 1; c++){
                boolean a = maze[r][c].rightWall;
                boolean b = maze[r+1][c].rightWall;
                boolean d = maze[r][c].bottomWall;
                boolean e = maze[r][c+1].bottomWall;

                // If a full 2×2 block is closed, open 1 random spot
                if(a && b && d && e){
                    if(rand.nextBoolean()) maze[r][c].rightWall = false;
                    else                   maze[r][c].bottomWall = false;
                }
            }
        }

        return maze;
    }

    static class UnionFind {
        int[] parent;

        UnionFind(int size){
            parent = new int[size];
            for(int i=0;i<size;i++) parent[i] = i;
        }

        int find(int x){
            if(parent[x] != x)
                parent[x] = find(parent[x]);
            return parent[x];
        }

        boolean union(int a, int b){
            int pa = find(a);
            int pb = find(b);
            if(pa == pb) return false;
            parent[pb] = pa;
            return true;
        }
    }
}

// Game Panel (drawing, input)
class GamePanel extends JPanel implements KeyListener {

    private Cell[][] maze;
    private int rows, cols;
    private final int cellSize = 36;

    private int playerRow = 0, playerCol = 0;
    private java.util.List<Enemy> enemies = new ArrayList<>();
    private final Random rnd = new Random();

    private javax.swing.Timer enemyTimer;
    private javax.swing.Timer victoryAnimTimer;

    // Victory & Game Over State
    private boolean victoryMode = false;
    private boolean gameOver = false;
    private float victoryScale = 1f;      // animated "VICTORY!" size
    private float scaleDirection = 0.02f; // bounce speed

    // High score tracking
    private double bestTime = -1;      // store the best time in seconds
    private long startTime;            // track when current game started
    private boolean newRecord = false; // mark if new record was set


    private JButton replayButton; // appears on victory or game over

    // expansion counters and limits
    private int horizontalExpansions = 0;
    private int verticalExpansions = 0;
    private final int maxHorizontal = 4; // max horiz and vert = 7 total
    private final int maxVertical = 3;
    private double lastTimeTaken = 0; // stores time for the last completed run

    private boolean isPaused = false;


    public GamePanel(int initialRows, int initialCols) {
        this.rows = Math.max(4, initialRows);
        this.cols = Math.max(4, initialCols);
        maze = MazeGenerator.generateMaze(rows, cols);

        setPreferredSize(new Dimension(cols * cellSize, rows * cellSize));
        setBackground(Color.LIGHT_GRAY);
        setFocusable(true);
        addKeyListener(this);

        spawnEnemyFarFromPlayer();

        // Enemy movement timer (slower so player can escape), adjustable for difficulty settings?
        enemyTimer = new javax.swing.Timer(425, e -> {
            if (isPaused) return;
            if (!victoryMode && !gameOver) {
                moveEnemiesBFS();
                checkEnemyCollisions();
            }
            repaint();

        });
        enemyTimer.start();

        // Victory animation (pulsating text)
        victoryAnimTimer = new javax.swing.Timer(30, e -> {
            if (victoryMode) {
                victoryScale += scaleDirection;
                if (victoryScale > 1.3f || victoryScale < 0.8f)
                    scaleDirection *= -1;
                repaint();
            }
        });
        victoryAnimTimer.start();

        setupReplayButton();
        startTime = System.currentTimeMillis();

    }

    private double loadHighScore() {
        try {
            java.io.File f = new java.io.File("highscore.txt");
            if (f.exists()) {
                java.util.Scanner sc = new java.util.Scanner(f);
                if (sc.hasNextDouble()) {
                    double score = sc.nextDouble();
                    sc.close();
                    return score;
                }
                sc.close();
            }
        } catch (Exception e) { }
        return -1;
    }

    private void saveHighScore(double score) {
        try {
            java.io.PrintWriter pw = new java.io.PrintWriter("highscore.txt");
            pw.println(score);
            pw.close();
        } catch (Exception e) { e.printStackTrace(); }
    }


    // Replay button
    private void setupReplayButton() {
        replayButton = new JButton("Replay");
        replayButton.setFocusable(false);
        replayButton.setVisible(false);

        replayButton.addActionListener(e -> restartGame());

        setLayout(null);
        add(replayButton);
    }

    private void restartGame() {
        // reset state
        victoryMode = false;
        gameOver = false;
        victoryScale = 1f;
        horizontalExpansions = 0;
        verticalExpansions = 0;

        // reset maze & player
        maze = MazeGenerator.generateMaze(rows, cols); // keep the current rows/cols size
        playerRow = 0;
        playerCol = 0;

        // reset enemies
        enemies.clear();
        spawnEnemyFarFromPlayer();

        replayButton.setVisible(false);

        if (enemyTimer != null) enemyTimer.start();
        repaint();
    }

    // enemy spawning
    private void spawnEnemyFarFromPlayer() {
        int minDist = Math.max(3, Math.max(rows, cols) / 2);
        int er, ec;
        int attempts = 0;
        do {
            er = rnd.nextInt(rows);
            ec = rnd.nextInt(cols);
            attempts++;
            if (attempts > 300) break;
        } while (Math.abs(er - playerRow) + Math.abs(ec - playerCol) < minDist);

        enemies.add(new Enemy(er, ec));
    }

    // enemy movement
    private void moveEnemiesBFS() {
        if (isPaused) return;

        for (Enemy en : enemies) {
            Point step = bfsNextStep(en.row, en.col, playerRow, playerCol);
            if (step != null) {
                en.row = step.x;
                en.col = step.y;
            }
        }
    }
    // BFS calc for enemy movement stored in LL
    private Point bfsNextStep(int sr, int sc, int tr, int tc) {
        if (sr == tr && sc == tc) return null;

        boolean[][] visited = new boolean[rows][cols];
        Point[][] parent = new Point[rows][cols];
        Queue<Point> q = new LinkedList<>();
        q.add(new Point(sr, sc));
        visited[sr][sc] = true;

        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};

        while (!q.isEmpty()) {
            Point p = q.poll();
            if (p.x == tr && p.y == tc) break;

            for (int i = 0; i < 4; i++) {
                int nr = p.x + dr[i], nc = p.y + dc[i];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                if (visited[nr][nc]) continue;
                if (isBlocked(p.x, p.y, nr, nc)) continue;

                visited[nr][nc] = true;
                parent[nr][nc] = p;
                q.add(new Point(nr, nc));
            }
        }

        if (!visited[tr][tc]) return null;

        Point cur = new Point(tr, tc);
        Point prev = parent[cur.x][cur.y];
        while (prev != null && !(prev.x == sr && prev.y == sc)) {
            cur = prev;
            prev = parent[cur.x][cur.y];
        }
        return new Point(cur.x, cur.y);
    }

    // collision
    private void checkEnemyCollisions() {
        for (Enemy en : enemies) {
            if (en.row == playerRow && en.col == playerCol) {
                // expand according to current phase
                expandMazeWithRules();
                return;
            }
        }
    }

    // expansion with rules (horizontal then vertical then lose)
    private void expandMazeWithRules() {
        if (gameOver || victoryMode) return;

        // Horizontal phase
        if (horizontalExpansions < maxHorizontal) {
            horizontalExpansions++;
            expandHorizontally(4); // add 4 columns at a time
            spawnEnemyInNewSectionSafelyHorizontal(4);
            return;
        }

        // Vertical phase
        if (verticalExpansions < maxVertical) {
            verticalExpansions++;
            expandVertically(3); // add 3 rows at bottom each time
            spawnEnemyInNewSectionSafelyVertical(3);
            return;
        }

        // If we reach here, 7 expansions, game over
        gameOver = true;
        if (enemyTimer != null) enemyTimer.stop();
        replayButton.setBounds(getWidth() / 2 - 60, getHeight() / 2 + 80, 120, 40);
        replayButton.setVisible(true);
        repaint();
    }

    // horizontal expansion
    private void expandHorizontally(int extraCols) {
        Cell[][] newSection = MazeGenerator.generateMaze(rows, extraCols);
        Cell[][] newMaze = new Cell[rows][cols + extraCols];

        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                newMaze[r][c] = maze[r][c];

        for (int r = 0; r < rows; r++)
            for (int c = 0; c < extraCols; c++)
                newMaze[r][cols + c] = newSection[r][c];

        // more openings (6–10 evenly spaced)
        int openings = 6 + rnd.nextInt(5); // 6–10 openings
        for (int i = 0; i < openings; i++) {
            int r = i * (rows - 1) / (openings - 1); // spread evenly vertically
            newMaze[r][cols - 1].rightWall = false;
        }

        maze = newMaze;
        cols += extraCols;

        setPreferredSize(new Dimension(cols * cellSize, rows * cellSize));
        revalidate();
        repaint();
    }


    private void spawnEnemyInNewSectionSafelyHorizontal(int extraCols) {
        int attempts = 0;
        int er, ec;
        int minDist = Math.max(3, Math.max(rows, cols) / 4);
        do {
            er = rnd.nextInt(rows);
            ec = cols - extraCols + rnd.nextInt(extraCols);
            attempts++;
            if (attempts > 500) break;
        } while (Math.abs(er - playerRow) + Math.abs(ec - playerCol) < minDist);
        enemies.add(new Enemy(er, ec));
    }

    // vertical expansion: add new rows at bottom
    private void expandVertically(int extraRows) {
        // Generate the new vertical chunk
        Cell[][] newSection = MazeGenerator.generateMaze(extraRows, cols);
        Cell[][] newMaze = new Cell[rows + extraRows][cols];

        // Copy old maze
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                newMaze[r][c] = maze[r][c];

        // Copy new section below
        for (int r = 0; r < extraRows; r++)
            for (int c = 0; c < cols; c++)
                newMaze[rows + r][c] = newSection[r][c];

        // more openings
        int openings = 6 + rnd.nextInt(5); // 6–10 openings
        for (int i = 0; i < openings; i++) {
            int c = i * (cols - 1) / (openings - 1); // spread evenly horizontally
            newMaze[rows - 1][c].bottomWall = false; // connect old bottom to new top
        }

        // Install new maze
        maze = newMaze;
        rows += extraRows;

        setPreferredSize(new Dimension(cols * cellSize, rows * cellSize));
        revalidate();
        repaint();
    }

    private void spawnEnemyInNewSectionSafelyVertical(int extraRows) {
        int attempts = 0;
        int er, ec;
        int minDist = Math.max(3, Math.max(rows, cols) / 4);

        do {
            er = rows - extraRows + rnd.nextInt(extraRows);
            ec = rnd.nextInt(cols);
            attempts++;

            if (attempts > 500) break;
        } while (Math.abs(er - playerRow) + Math.abs(ec - playerCol) < minDist);

        enemies.add(new Enemy(er, ec));
    }

    private void pauseGame() {
        isPaused = true;

        if (enemyTimer != null) enemyTimer.stop();
        // No movement while paused, player can still move for demonstration purposes
        repaint();
    }

    private void resumeGame() {
        isPaused = false;

        if (enemyTimer != null && !victoryMode && !gameOver) enemyTimer.start();
        repaint();
    }



    // input
    @Override
    public void keyPressed(KeyEvent e) {

        // Pause toggle
        if (e.getKeyCode() == KeyEvent.VK_P) {
            if (isPaused) resumeGame();
            else pauseGame();
            return;
        }

        if (victoryMode || gameOver) return;

        int nr = playerRow, nc = playerCol;
        if (e.getKeyCode() == KeyEvent.VK_W) nr--;
        if (e.getKeyCode() == KeyEvent.VK_S) nr++;
        if (e.getKeyCode() == KeyEvent.VK_A) nc--;
        if (e.getKeyCode() == KeyEvent.VK_D) nc++;

        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !isBlocked(playerRow, playerCol, nr, nc)) {
            playerRow = nr;
            playerCol = nc;

            scrollRectToVisible(new Rectangle(playerCol * cellSize, playerRow * cellSize, cellSize, cellSize));
        }


        // victory check
        if (playerRow == rows - 1 && playerCol == cols - 1) {
            enterVictoryMode();
        }

        repaint(); // regenerates map after every move
    }

    private void enterVictoryMode() {
        victoryMode = true;
        if (enemyTimer != null) enemyTimer.stop();

        // Calculate time taken
        long endTime = System.currentTimeMillis();
        lastTimeTaken = (endTime - startTime) / 1000.0; // seconds


        // Load previous best if not already loaded
        if (bestTime < 0) bestTime = loadHighScore();

        // Check for new record
        if (bestTime < 0 || lastTimeTaken < bestTime) {
            bestTime = lastTimeTaken;
            saveHighScore(bestTime);
            newRecord = true;
        } else {
            newRecord = false;
        }

        replayButton.setBounds(getWidth() / 2 - 60, getHeight() / 2 + 80, 120, 40);
        replayButton.setVisible(true);
    }


    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    // wall check
    private boolean isBlocked(int r1, int c1, int r2, int c2) {
        if (r2 == r1 - 1 && c2 == c1) return maze[r2][c2].bottomWall;
        if (r2 == r1 + 1 && c2 == c1) return maze[r1][c1].bottomWall;
        if (c2 == c1 - 1 && r2 == r1) return maze[r2][c2].rightWall;
        if (c2 == c1 + 1 && r2 == r1) return maze[r1][c1].rightWall;
        return true;
    }

    // drawing 2D maze/hud/animations
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        // Draw maze, enemies, player
        drawMaze(g2);
        drawExit(g2);
        drawEnemies(g2);
        drawPlayer(g2);

        // Victory overlay overrides everything
        if (victoryMode) {
            drawVictoryOverlay(g2);
        } else if (gameOver) {
            drawGameOverOverlay(g2);
        }

        g2.dispose();
    }

    private void drawMaze(Graphics2D g2) {
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(4f));

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = c * cellSize;
                int y = r * cellSize;

                if (r == 0) g2.drawLine(x, y, x + cellSize, y);
                if (c == 0) g2.drawLine(x, y, x, y + cellSize);
                if (maze[r][c].rightWall) g2.drawLine(x + cellSize, y, x + cellSize, y + cellSize);
                if (maze[r][c].bottomWall) g2.drawLine(x, y + cellSize, x + cellSize, y + cellSize);
            }
        }
    }


    private void drawExit(Graphics2D g2) {
        g2.setColor(Color.GREEN);
        int ex = (cols - 1) * cellSize + (cellSize / 6);
        int ey = (rows - 1) * cellSize + (cellSize / 6);
        int esz = cellSize - (cellSize / 3);
        g2.fillRect(ex, ey, esz, esz);
    }

    private void drawEnemies(Graphics2D g2) {
        g2.setColor(Color.BLUE);
        for (Enemy en : enemies) {
            int x = en.col * cellSize + cellSize / 8;
            int y = en.row * cellSize + cellSize / 8;
            g2.fillOval(x, y, cellSize - cellSize / 4, cellSize - cellSize / 4);
        }
    }

    private void drawPlayer(Graphics2D g2) {
        g2.setColor(Color.RED);
        int x = playerCol * cellSize + cellSize / 8;
        int y = playerRow * cellSize + cellSize / 8;
        g2.fillOval(x, y, cellSize - cellSize / 4, cellSize - cellSize / 4);
    }

    // Victory Overlay
    private void drawVictoryOverlay(Graphics2D g2) {
        // Darken world
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, getWidth(), getHeight());

        int sx = (cols - 1) * cellSize + cellSize / 2;
        int sy = (rows - 1) * cellSize + cellSize / 2;

        g2.setComposite(AlphaComposite.Clear);
        g2.fillOval(sx - 80, sy - 80, 160, 160);
        g2.setComposite(AlphaComposite.SrcOver);

        // Draw pulsating text - ISSUE (Victory doesn't pulse, but shows victory)
        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Arial", Font.BOLD, (int) (60 * victoryScale)));

        String msg = "VICTORY!";

        if (newRecord) {
            g2.setFont(new Font("Arial", Font.BOLD, 30));
            String msg2 = "New Record!";
            FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(msg2)) / 2;
            int ty = getHeight() / 3 + 70;
            g2.setColor(Color.CYAN);
            g2.drawString(msg2, tx, ty);
        }

        // Show times
        g2.setFont(new Font("Arial", Font.PLAIN, 24));
        g2.setColor(Color.WHITE);

        String timeMsg = String.format("Time: %.2f s", lastTimeTaken);
        String bestMsg = bestTime > 0 ? String.format("Best: %.2f s", bestTime) : "";

        FontMetrics fm = g2.getFontMetrics();
        int tx1 = (getWidth() - fm.stringWidth(timeMsg)) / 2;
        g2.drawString(timeMsg, tx1, getHeight() / 3 + 120);

        if (!bestMsg.isEmpty()) {
            int tx2 = (getWidth() - fm.stringWidth(bestMsg)) / 2;
            g2.drawString(bestMsg, tx2, getHeight() / 3 + 150);
        }


        FontMetrics fm2 = g2.getFontMetrics();
        int tx = (getWidth() - fm2.stringWidth(msg)) / 2;
        int ty = getHeight() / 3;

        g2.drawString(msg, tx, ty);
    }

    // Game Over Overlay
    private void drawGameOverOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, getWidth(), getHeight());

        // Game Over text
        g2.setColor(Color.RED);
        g2.setFont(new Font("Arial", Font.BOLD, 64));
        String msg = "GAME OVER";
        FontMetrics fm = g2.getFontMetrics();
        int tx = (getWidth() - fm.stringWidth(msg)) / 2;
        int ty = getHeight() / 2;
        g2.drawString(msg, tx, ty);

        // show a small text when game over
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        String reason = "Too many expansions — you lose";
        int rx = (getWidth() - g2.getFontMetrics().stringWidth(reason)) / 2;
        g2.drawString(reason, rx, ty + 36);

        replayButton.setBounds(getWidth() / 2 - 60, getHeight() / 2 + 80, 120, 40);
        replayButton.setVisible(true);
    }
}