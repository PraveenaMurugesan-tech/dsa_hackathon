import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;

/**
 * Animated viewer.
 *  white = open, black = wall, green = start, red = exit, each key/door pair has its own colour.
 *  Yellow cells + dark line = path taken. Keys vanish when picked up (inventory panel updates),
 *  doors turn white with an open-padlock mark when unlocked.
 */
public final class MazeVisualizer {
    // a, b, c, d, e, f  (none of them white / black / green / red)
    private static final Color[] TYPE_COLOR = {
            new Color(33, 102, 235),   // a blue
            new Color(245, 130, 20),   // b orange
            new Color(150, 70, 200),   // c purple
            new Color(0, 175, 185),    // d teal
            new Color(140, 90, 40),    // e brown
            new Color(235, 90, 160)    // f pink
    };
    private static final Color PATH_VISITED = new Color(255, 236, 130);
    private static final Color PATH_PREVIEW = new Color(255, 248, 210);
    private static final Color START_COLOR = new Color(40, 170, 70);
    private static final Color EXIT_COLOR = new Color(220, 45, 45);
    private static final Color GRID_LINE = new Color(225, 225, 225);
    private static final long FX_NANOS = 650_000_000L;

    private static final int FX_NONE = 0, FX_PICKUP = 1, FX_UNLOCK = 2;

    private final Maze maze;
    private final List<int[]> path;
    private final boolean solved;
    private final int pathLen;

    private final boolean[][] onPath, visited, keyTaken, doorOpen;
    private final boolean[] typeUnlocked = new boolean[6];
    private int step;
    private int heldMask;
    private boolean showPreview = true;

    private int fxType = FX_NONE, fxR, fxC;
    private long fxStart;

    private final JFrame frame = new JFrame("Maze with Keys and Doors");
    private final MazePanel mazePanel = new MazePanel();
    private final SidePanel sidePanel = new SidePanel();
    private final JTextArea log = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final JButton playBtn = new JButton("Pause");
    private final javax.swing.Timer moveTimer;
    private final javax.swing.Timer fxTimer;

    public MazeVisualizer(Maze maze, MazeSolver.Result res) {
        this.maze = maze;
        this.path = res.path;
        this.solved = res.length >= 0;
        this.pathLen = solved ? res.length : 0;
        int R = maze.rows, C = maze.cols;
        onPath = new boolean[R][C];
        visited = new boolean[R][C];
        keyTaken = new boolean[R][C];
        doorOpen = new boolean[R][C];
        for (int[] p : path) onPath[p[0]][p[1]] = true;

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(log);
        JPanel east = new JPanel(new BorderLayout());
        east.setPreferredSize(new Dimension(270, 0));
        east.add(sidePanel, BorderLayout.NORTH);
        east.add(scroll, BorderLayout.CENTER);

        JButton backBtn = new JButton("Back");
        JButton stepBtn = new JButton("Step");
        JButton resetBtn = new JButton("Reset");
        JCheckBox preview = new JCheckBox("Show full route", true);
        final JSlider speed = new JSlider(1, 10, 6);
        speed.setPreferredSize(new Dimension(120, 24));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        controls.add(playBtn);
        controls.add(backBtn);
        controls.add(stepBtn);
        controls.add(resetBtn);
        controls.add(new JLabel("Speed"));
        controls.add(speed);
        controls.add(preview);
        JPanel south = new JPanel(new BorderLayout());
        south.add(controls, BorderLayout.NORTH);
        status.setBorder(BorderFactory.createEmptyBorder(2, 10, 8, 10));
        status.setFont(status.getFont().deriveFont(Font.BOLD, 13f));
        south.add(status, BorderLayout.SOUTH);

        frame.add(mazePanel, BorderLayout.CENTER);
        frame.add(east, BorderLayout.EAST);
        frame.add(south, BorderLayout.SOUTH);
        frame.setSize(1050, 760);
        frame.setLocationRelativeTo(null);

        moveTimer = new javax.swing.Timer(500, e -> tick());
        moveTimer.setInitialDelay(700);
        fxTimer = new javax.swing.Timer(30, e -> {
            if (fxType != FX_NONE && System.nanoTime() - fxStart > FX_NANOS) fxType = FX_NONE;
            mazePanel.repaint();
            if (fxType == FX_NONE) ((javax.swing.Timer) e.getSource()).stop();
        });

        speed.addChangeListener(e -> moveTimer.setDelay(1100 - speed.getValue() * 100));
        moveTimer.setDelay(1100 - speed.getValue() * 100);
        preview.addActionListener(e -> {
            showPreview = preview.isSelected();
            mazePanel.repaint();
        });
        playBtn.addActionListener(e -> {
            if (moveTimer.isRunning()) {
                pause();
            } else {
                if (step >= pathLen) rebuild(0, false);
                play();
            }
        });
        stepBtn.addActionListener(e -> {
            pause();
            if (step < pathLen) rebuild(step + 1, true);
        });
        backBtn.addActionListener(e -> {
            pause();
            if (step > 0) rebuild(step - 1, false);
        });
        resetBtn.addActionListener(e -> {
            pause();
            rebuild(0, false);
        });

        if (!solved) {
            playBtn.setEnabled(false);
            backBtn.setEnabled(false);
            stepBtn.setEnabled(false);
            resetBtn.setEnabled(false);
            speed.setEnabled(false);
        }
        rebuild(0, false);
    }

    public void show() {
        frame.setVisible(true);
        if (solved && pathLen > 0) play();
    }

    private void play() {
        playBtn.setText("Pause");
        moveTimer.start();
    }

    private void pause() {
        moveTimer.stop();
        playBtn.setText("Play");
    }

    private void tick() {
        if (step >= pathLen) {
            pause();
            return;
        }
        rebuild(step + 1, true);
        if (step >= pathLen) pause();
    }

    /** Recomputes the whole animation state for 's' moves made. */
    private void rebuild(int s, boolean animate) {
        step = s;
        for (int r = 0; r < maze.rows; r++)
            for (int c = 0; c < maze.cols; c++) {
                visited[r][c] = false;
                keyTaken[r][c] = false;
                doorOpen[r][c] = false;
            }
        java.util.Arrays.fill(typeUnlocked, false);
        heldMask = 0;
        fxType = FX_NONE;
        StringBuilder sb = new StringBuilder();
        int newFx = FX_NONE;
        for (int i = 0; i <= s && i < path.size(); i++) {
            int r = path.get(i)[0], c = path.get(i)[1];
            visited[r][c] = true;
            char ch = maze.grid[r][c];
            if (maze.isKey(r, c) && !keyTaken[r][c]) {
                keyTaken[r][c] = true;
                heldMask |= 1 << (ch - 'a');
                sb.append("Step ").append(i).append(": picked up key '").append(ch).append("'\n");
                if (i == s && animate) {
                    newFx = FX_PICKUP;
                    fxR = r;
                    fxC = c;
                }
            }
            if (maze.isDoor(r, c) && !doorOpen[r][c]) {
                doorOpen[r][c] = true;
                typeUnlocked[ch - 'A'] = true;
                sb.append("Step ").append(i).append(": unlocked door '").append(ch).append("'\n");
                if (i == s && animate) {
                    newFx = FX_UNLOCK;
                    fxR = r;
                    fxC = c;
                }
            }
        }
        if (solved && s == pathLen) sb.append("Reached the exit in ").append(pathLen).append(" steps.\n");
        log.setText(sb.toString());
        log.setCaretPosition(log.getDocument().getLength());

        if (!solved) status.setText("No path to the exit exists (answer: -1)");
        else if (s == pathLen) status.setText("Done - shortest path length = " + pathLen);
        else status.setText("Step " + s + " / " + pathLen);

        if (newFx != FX_NONE) {
            fxType = newFx;
            fxStart = System.nanoTime();
            fxTimer.start();
        }
        mazePanel.repaint();
        sidePanel.repaint();
    }

    // ------------------------------------------------------------------ drawing helpers

    private static void setAlpha(Graphics2D g, float a) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, a))));
    }

    private static void drawLabel(Graphics2D g, String text, double cx, double cy, double size, Color col) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(6, (int) size)));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(col);
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0),
                (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
    }

    private static void drawKey(Graphics2D g, double cx, double cy, double s, Color col, float alpha) {
        Composite old = g.getComposite();
        setAlpha(g, alpha);
        g.setColor(col);
        g.setStroke(new BasicStroke((float) Math.max(1.5, s * 0.09), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double bowR = s * 0.16, bx = cx - s * 0.18;
        g.draw(new Ellipse2D.Double(bx - bowR, cy - bowR, 2 * bowR, 2 * bowR));
        g.draw(new Line2D.Double(bx + bowR, cy, cx + s * 0.30, cy));
        g.draw(new Line2D.Double(cx + s * 0.30, cy, cx + s * 0.30, cy + s * 0.13));
        g.draw(new Line2D.Double(cx + s * 0.19, cy, cx + s * 0.19, cy + s * 0.10));
        g.setComposite(old);
    }

    private static void drawPadlock(Graphics2D g, double cx, double cy, double s, Color col, boolean open, float alpha) {
        Composite old = g.getComposite();
        setAlpha(g, alpha);
        g.setColor(col);
        g.setStroke(new BasicStroke((float) Math.max(1.5, s * 0.07), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double bw = s * 0.44, bh = s * 0.30, bx = cx - bw / 2, by = cy - s * 0.02;
        g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, s * 0.08, s * 0.08));
        double sw = bw * 0.62, sh = s * 0.34, sx = cx - sw / 2;
        double lift = open ? s * 0.10 : 0;
        double top = by - sh - lift;
        double midY = top + sh / 2;
        g.draw(new Arc2D.Double(sx, top, sw, sh, 0, 180, Arc2D.OPEN));
        g.draw(new Line2D.Double(sx + sw, midY, sx + sw, by));
        g.draw(new Line2D.Double(sx, midY, sx, open ? midY + sh * 0.22 : by));
        g.setComposite(old);
    }

    // ------------------------------------------------------------------ maze panel

    private final class MazePanel extends JPanel {
        MazePanel() {
            setBackground(new Color(238, 238, 240));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int R = maze.rows, C = maze.cols;
            int cs = Math.max(4, Math.min((getWidth() - 16) / C, (getHeight() - 16) / R));
            int ox = (getWidth() - cs * C) / 2, oy = (getHeight() - cs * R) / 2;
            double fxT = fxType == FX_NONE ? 1.0
                    : Math.min(1.0, (System.nanoTime() - fxStart) / (double) FX_NANOS);

            for (int r = 0; r < R; r++) {
                for (int c = 0; c < C; c++) {
                    int x = ox + c * cs, y = oy + r * cs;
                    double cx = x + cs / 2.0, cy = y + cs / 2.0;
                    char ch = maze.grid[r][c];
                    if (ch == '#') {
                        g.setColor(Color.BLACK);
                        g.fillRect(x, y, cs, cs);
                        continue;
                    }
                    Color bg = Color.WHITE;
                    if (visited[r][c]) bg = PATH_VISITED;
                    else if (showPreview && onPath[r][c]) bg = PATH_PREVIEW;
                    g.setColor(bg);
                    g.fillRect(x, y, cs, cs);
                    if (cs >= 8) {
                        g.setColor(GRID_LINE);
                        g.drawRect(x, y, cs, cs);
                    }

                    boolean isStart = r == maze.sr && c == maze.sc;
                    boolean isExit = r == maze.er && c == maze.ec;
                    if (isStart || isExit) {
                        g.setColor(isStart ? START_COLOR : EXIT_COLOR);
                        g.fillRect(x + 1, y + 1, cs - 1, cs - 1);
                        drawLabel(g, isStart ? "S" : "E", cx, cy, cs * 0.6, Color.WHITE);
                        continue;
                    }
                    if (maze.isKey(r, c)) {
                        Color col = TYPE_COLOR[ch - 'a'];
                        if (!keyTaken[r][c]) {
                            drawKey(g, cx, cy, cs, col, 1f);
                        } else if (fxType == FX_PICKUP && fxR == r && fxC == c && fxT < 1.0) {
                            drawKey(g, cx, cy - fxT * cs * 0.6, cs, col, (float) (1.0 - fxT)); // key floats away
                        }
                    } else if (maze.isDoor(r, c)) {
                        Color col = TYPE_COLOR[ch - 'A'];
                        if (!doorOpen[r][c]) {
                            g.setColor(col);
                            g.fillRect(x + 1, y + 1, cs - 1, cs - 1);
                            drawLabel(g, String.valueOf(ch), cx, cy, cs * 0.55, Color.WHITE);
                        } else {
                            g.setColor(Color.WHITE);
                            g.fillRect(x + 1, y + 1, cs - 1, cs - 1);
                            g.setColor(col);
                            g.setStroke(new BasicStroke(Math.max(1f, cs * 0.05f)));
                            g.drawRect(x + 2, y + 2, cs - 4, cs - 4);
                            drawPadlock(g, cx, cy + cs * 0.05, cs, col, true, 1f);
                            if (fxType == FX_UNLOCK && fxR == r && fxC == c && fxT < 1.0) {
                                Composite old = g.getComposite();
                                setAlpha(g, (float) (1.0 - fxT));
                                g.setColor(col);
                                g.fillRect(x + 1, y + 1, cs - 1, cs - 1);
                                g.setComposite(old);
                            }
                        }
                    }
                }
            }

            // trail
            if (step > 0 && solved) {
                Path2D trail = new Path2D.Double();
                for (int i = 0; i <= step && i < path.size(); i++) {
                    double px = ox + path.get(i)[1] * cs + cs / 2.0, py = oy + path.get(i)[0] * cs + cs / 2.0;
                    if (i == 0) trail.moveTo(px, py);
                    else trail.lineTo(px, py);
                }
                g.setColor(new Color(50, 50, 50, 170));
                g.setStroke(new BasicStroke(Math.max(2f, cs * 0.14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(trail);
            }

            // pulse ring on pick-up / unlock
            if (fxType != FX_NONE && fxT < 1.0) {
                char fc = maze.grid[fxR][fxC];
                Color col = TYPE_COLOR[Character.toLowerCase(fc) - 'a'];
                double rad = cs * (0.3 + 0.8 * fxT);
                double px = ox + fxC * cs + cs / 2.0, py = oy + fxR * cs + cs / 2.0;
                Composite old = g.getComposite();
                setAlpha(g, (float) (1.0 - fxT));
                g.setColor(col);
                g.setStroke(new BasicStroke(3f));
                g.draw(new Ellipse2D.Double(px - rad, py - rad, 2 * rad, 2 * rad));
                g.setComposite(old);
            }

            // agent
            if (solved && !path.isEmpty()) {
                int[] p = path.get(Math.min(step, path.size() - 1));
                double px = ox + p[1] * cs + cs / 2.0, py = oy + p[0] * cs + cs / 2.0, rad = cs * 0.27;
                g.setColor(new Color(20, 20, 90));
                g.fill(new Ellipse2D.Double(px - rad, py - rad, 2 * rad, 2 * rad));
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(Math.max(1f, cs * 0.05f)));
                g.draw(new Ellipse2D.Double(px - rad, py - rad, 2 * rad, 2 * rad));
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ inventory + legend

    private final class SidePanel extends JPanel {
        SidePanel() {
            setPreferredSize(new Dimension(270, 370));
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int y = 24;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.setColor(Color.DARK_GRAY);
            g.drawString("Key inventory", 12, y);
            y += 10;

            boolean any = false;
            for (int t = 0; t < 6; t++) {
                if (!maze.hasKey[t] && !maze.hasDoor[t]) continue;
                any = true;
                boolean held = ((heldMask >> t) & 1) != 0;
                Color col = TYPE_COLOR[t];
                g.setColor(held ? new Color(col.getRed(), col.getGreen(), col.getBlue(), 45) : new Color(242, 242, 242));
                g.fill(new RoundRectangle2D.Double(8, y, w - 16, 30, 10, 10));
                g.setStroke(new BasicStroke(1f));
                g.setColor(held ? col : new Color(215, 215, 215));
                g.draw(new RoundRectangle2D.Double(8, y, w - 16, 30, 10, 10));
                drawKey(g, 34, y + 15, 30, held ? col : new Color(170, 170, 170), held ? 1f : 0.55f);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                g.setColor(Color.DARK_GRAY);
                g.drawString((char) ('a' + t) + " -> " + (char) ('A' + t), 60, y + 19);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                g.setColor(held ? col.darker() : Color.GRAY);
                g.drawString(held ? "held" : "not yet", 128, y + 19);
                if (maze.hasDoor[t]) drawPadlock(g, w - 32, y + 19, 26, typeUnlocked[t] ? col : Color.GRAY, typeUnlocked[t], 1f);
                y += 36;
            }
            if (!any) {
                g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
                g.setColor(Color.GRAY);
                g.drawString("(no keys or doors in this maze)", 12, y + 20);
                y += 30;
            }

            y += 8;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.setColor(Color.DARK_GRAY);
            g.drawString("Legend", 12, y);
            y += 8;
            Object[][] legend = {
                    {Color.WHITE, "Open cell"}, {Color.BLACK, "Wall"}, {START_COLOR, "Start"},
                    {EXIT_COLOR, "Exit"}, {PATH_VISITED, "Path taken"}};
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            for (Object[] item : legend) {
                g.setColor((Color) item[0]);
                g.fillRect(14, y, 14, 14);
                g.setColor(Color.GRAY);
                g.drawRect(14, y, 14, 14);
                g.setColor(Color.DARK_GRAY);
                g.drawString((String) item[1], 38, y + 12);
                y += 19;
            }
            g.dispose();
        }
    }
}
