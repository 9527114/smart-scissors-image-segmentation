import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

public class ImagePanel extends JPanel {
    private BufferedImage originalImage;
    private BufferedImage displayImage;
    private BufferedImage cutoutImage;
    private boolean cutoutActive = false;

    private List<List<int[]>> paths;
    private List<int[]> currentLivePath;
    private int[] currentSeedPoint;

    private Preproc preprocessor;
    private PathFinder pathFinder;
    private AutoSeedPoint autoSeedPointHandler;

    private boolean allowAutoConnectForNextSegment = false;
    private boolean pathIsClosed = false;
    private List<int[]> firstPointOfContour = null;

    private static final Color SEED_COLOR = new Color(255, 50, 50);
    private static final Color LIVE_WIRE_COLOR = new Color(0, 200, 255, 180);
    private static final Color FINAL_PATH_COLOR = new Color(255, 255, 255, 200);
    private static final Color AUTO_SEED_COLOR = new Color(255, 215, 0, 200);
    private static final Color CLOSURE_HINT_COLOR = new Color(50, 255, 50, 150); // For closure hint
    private static final int SEED_RADIUS = 4;
    private static final int AUTO_SEED_RADIUS = 6;
    private static final int MOUSE_MOVE_THRESHOLD = 3;
    private static final int CLOSURE_SNAP_DISTANCE = 10;

    private int lastMouseX = -1;
    private int lastMouseY = -1;


    private List<int[]> highlightPathSegment = new ArrayList<>();

    public ImagePanel() {
        paths = new ArrayList<>();
        currentLivePath = new ArrayList<>();
        autoSeedPointHandler = new AutoSeedPoint();
        setBackground(Color.LIGHT_GRAY);
        setPreferredSize(new Dimension(800, 600));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && currentSeedPoint != null) {
                    currentLivePath.clear();
                    repaint();
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (originalImage == null || preprocessor == null || pathFinder == null) return;
                requestFocusInWindow();

                int x = e.getX();
                int y = e.getY();
                int[] snappedPoint = EdgeSnapUtil.snapToEdge(x, y, preprocessor.getGradMag(), 8);

                if (SwingUtilities.isRightMouseButton(e)) {
                    undoLastPathSegment();
                } else if (SwingUtilities.isMiddleMouseButton(e)) {
                    if (currentSeedPoint != null && !paths.isEmpty()) {
                        completeAndCloseContour(true);
                    }
                    resetForNewContour();
                    allowAutoConnectForNextSegment = false;

                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    if (pathIsClosed && cutoutActive) {
                        clearPath();
                    }

                    if (currentSeedPoint == null) {
                        startNewContour(snappedPoint[0], snappedPoint[1]);

                    } else {
                        if (firstPointOfContour != null && !firstPointOfContour.isEmpty()) {
                            int[] firstP = firstPointOfContour.get(0);
                            double distToStart = Math.hypot(snappedPoint[0] - firstP[0], snappedPoint[1] - firstP[1]);
                            if (distToStart < CLOSURE_SNAP_DISTANCE && paths.size() > 0) {
                                completeAndCloseContour(false);
                                return;
                            }
                        }
                        addSegmentToCurrentContour(snappedPoint[0], snappedPoint[1]);
                    }
                }
                repaint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (currentSeedPoint == null || pathIsClosed || preprocessor == null || pathFinder == null) {
                    currentLivePath.clear();
                    return;
                }

                int x = e.getX();
                int y = e.getY();

                if (lastMouseX != -1 && lastMouseY != -1) {
                    double distance = Math.hypot(x - lastMouseX, y - lastMouseY);
                    if (distance < MOUSE_MOVE_THRESHOLD) {
                        return;
                    }
                }
                lastMouseX = x;
                lastMouseY = y;

                int[] snappedMousePos = EdgeSnapUtil.snapToEdge(x, y, preprocessor.getGradMag(), 8);

                List<int[]> newLive = pathFinder.findPath(
                        preprocessor.getCostMap(),
                        preprocessor.getGradDir(),
                        preprocessor.getGradMag(),
                        currentSeedPoint,
                        snappedMousePos
                );

                if (newLive != null) {
                    currentLivePath.clear();
                    currentLivePath.addAll(newLive);
                    autoSeedPointHandler.updatePathPoints(newLive);

                    if (autoSeedPointHandler.shouldAddSeedPoint()) {
                        addSegmentToCurrentContour(snappedMousePos[0], snappedMousePos[1]);
                        autoSeedPointHandler.recordAutoSeedAddition(snappedMousePos); // Pass coordinates
                    }
                } else {
                    currentLivePath.clear();
                }
                repaint();
            }
        });
    }

    private void startNewContour(int x, int y) {
        currentSeedPoint = new int[]{x, y};
        currentLivePath.clear();
        highlightPathSegment.clear();
        autoSeedPointHandler.clearAutoSeedPoints();
        lastMouseX = -1;
        lastMouseY = -1;
        allowAutoConnectForNextSegment = false;
        pathIsClosed = false;
        cutoutActive = false;
        cutoutImage = null;
        paths.clear();
        firstPointOfContour = new ArrayList<>();
        firstPointOfContour.add(new int[]{x,y});
    }

    private void addSegmentToCurrentContour(int endX, int endY) {
        highlightPathSegment.clear();
        int[] targetPoint = {endX, endY};

        List<int[]> segmentPath = null;
        if (pathFinder != null && preprocessor != null && currentSeedPoint != null) {
            segmentPath = pathFinder.findPath(
                    preprocessor.getCostMap(),
                    preprocessor.getGradDir(),
                    preprocessor.getGradMag(),
                    currentSeedPoint,
                    targetPoint
            );
        }

        if (segmentPath != null && !segmentPath.isEmpty()) {
            paths.add(new ArrayList<>(segmentPath));
            allowAutoConnectForNextSegment = true;
        }

        currentSeedPoint = new int[]{endX, endY};
        currentLivePath.clear();
        autoSeedPointHandler.updatePathPoints(new ArrayList<>());
        lastMouseX = -1;
        lastMouseY = -1;
    }

    private void completeAndCloseContour(boolean forceCloseEvenIfNotNearStart) {
        if (currentSeedPoint == null || firstPointOfContour == null || firstPointOfContour.isEmpty()) {
            resetForNewContour();
            return;
        }

        int[] finalTargetPoint = firstPointOfContour.get(0);

        List<int[]> closingSegment = pathFinder.findPath(
                preprocessor.getCostMap(),
                preprocessor.getGradDir(),
                preprocessor.getGradMag(),
                currentSeedPoint,
                finalTargetPoint
        );

        if (closingSegment != null && !closingSegment.isEmpty()) {
            paths.add(closingSegment);
        } else if (forceCloseEvenIfNotNearStart || paths.size() > 0) {
            List<int[]> straightLine = new ArrayList<>();
            straightLine.add(currentSeedPoint);
            straightLine.add(finalTargetPoint);
            paths.add(straightLine);
        }

        pathIsClosed = true;
        currentSeedPoint = null;
        currentLivePath.clear();
        autoSeedPointHandler.clearAutoSeedPoints();
        highlightPathSegment.clear();
        allowAutoConnectForNextSegment = false;
        repaint();
    }

    private void resetForNewContour() {
        currentSeedPoint = null;
        currentLivePath.clear();
        autoSeedPointHandler.clearAutoSeedPoints();
        highlightPathSegment.clear();
        pathIsClosed = false;
        lastMouseX = -1;
        lastMouseY = -1;
        allowAutoConnectForNextSegment = false;

        repaint();
    }

    public void setImage(BufferedImage image) {
        this.originalImage = image;
        if (image != null) {
            this.displayImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = displayImage.createGraphics();
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();
            setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
        } else {
            this.displayImage = null;
            setPreferredSize(new Dimension(800,600));
        }
        clearPath();
        revalidate();
        repaint();
    }

    public BufferedImage getImage() {
        return this.originalImage;
    }


    public void setPreprocessor(Preproc preprocessor) {
        this.preprocessor = preprocessor;
    }

    public void setPathFinder(PathFinder pathFinder) {
        this.pathFinder = pathFinder;
    }

    public void clearPath() {
        paths.clear();
        currentLivePath.clear();
        currentSeedPoint = null;
        autoSeedPointHandler.clearAutoSeedPoints();
        highlightPathSegment.clear();
        firstPointOfContour = null;
        pathIsClosed = false;
        cutoutActive = false;
        cutoutImage = null;
        allowAutoConnectForNextSegment = false;
        lastMouseX = -1;
        lastMouseY = -1;
        if (originalImage != null && displayImage != null) {
            Graphics2D g2d = displayImage.createGraphics();
            g2d.drawImage(originalImage, 0, 0, null);
            g2d.dispose();
        }
        repaint();
    }

    public void undoLastPathSegment() {
        if (!paths.isEmpty()) {
            paths.remove(paths.size() - 1);
            highlightPathSegment.clear();


            if (paths.isEmpty()) {
                currentSeedPoint = (firstPointOfContour != null && !firstPointOfContour.isEmpty()) ? firstPointOfContour.get(0) : null;
                allowAutoConnectForNextSegment = false;
                if(firstPointOfContour != null && firstPointOfContour.isEmpty() && currentSeedPoint == null){
                    firstPointOfContour = null;
                }
            } else {
                List<int[]> lastSegment = paths.get(paths.size() - 1);
                if (!lastSegment.isEmpty()) {
                    currentSeedPoint = lastSegment.get(lastSegment.size() - 1);
                } else {
                    currentSeedPoint = (firstPointOfContour != null && !firstPointOfContour.isEmpty()) ? firstPointOfContour.get(0) : null;
                }
            }
            pathIsClosed = false;
            cutoutActive = false;
            cutoutImage = null;
            currentLivePath.clear();
            lastMouseX = -1;
            lastMouseY = -1;

        } else if (currentSeedPoint != null && firstPointOfContour != null) {

            clearPath();
        }
        repaint();
    }

    public boolean hasPath() {
        return !paths.isEmpty() || currentSeedPoint != null;
    }

    private List<int[]> getCombinedPath() {
        List<int[]> fullPath = new LinkedList<>();
        if (paths.isEmpty()){
            if (firstPointOfContour != null && !firstPointOfContour.isEmpty()) {

                fullPath.add(firstPointOfContour.get(0));
            }
            return fullPath;
        }

        boolean firstSegment = true;
        for (List<int[]> segment : paths) {
            if (segment == null || segment.isEmpty()) continue;
            if (firstSegment) {
                fullPath.addAll(segment);
                firstSegment = false;
            } else {

                if (segment.size() > 1) {
                    fullPath.addAll(segment.subList(1, segment.size()));
                }

            }
        }
        return fullPath;
    }

    public BufferedImage getResultImage() {
        if (originalImage == null) return null;

        if (cutoutActive && cutoutImage != null) {
            return cutoutImage;
        }

        BufferedImage resultWithOverlay = new BufferedImage(originalImage.getWidth(),
                originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resultWithOverlay.createGraphics();
        g2d.drawImage(originalImage, 0, 0, null);

        if (hasPath()) {

            for (List<int[]> segment : paths) {
                drawPathSegment(g2d, segment, FINAL_PATH_COLOR, 2);
            }

        }
        g2d.dispose();
        return resultWithOverlay;
    }

    public void performCutout() {
        if (!pathIsClosed || originalImage == null || paths.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Path must be closed to perform cutout.", "Cutout Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<int[]> fullContourPoints = getCombinedPath();
        if (fullContourPoints.size() < 3) {
            JOptionPane.showMessageDialog(this,
                    "Not enough points to form a polygon for cutout.", "Cutout Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Polygon polygon = new Polygon();
        for (int[] p : fullContourPoints) {
            polygon.addPoint(p[0], p[1]);
        }

        cutoutImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cutoutImage.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0,0,cutoutImage.getWidth(), cutoutImage.getHeight());
        g.setComposite(AlphaComposite.SrcOver);
        g.setClip(polygon);
        g.drawImage(originalImage, 0, 0, null);
        g.dispose();

        cutoutActive = true;
        repaint();
        JOptionPane.showMessageDialog(this,
                "Cutout applied. You can now save the result.", "Cutout Successful", JOptionPane.INFORMATION_MESSAGE);
    }

    private void drawPathSegment(Graphics2D g2d, List<int[]> path, Color color, int strokeWidth) {
        if (path == null || path.size() < 2) return;
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(strokeWidth));
        for (int i = 1; i < path.size(); i++) {
            int[] p1 = path.get(i - 1);
            int[] p2 = path.get(i);
            g2d.drawLine(p1[0], p1[1], p2[0], p2[1]);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (cutoutActive && cutoutImage != null) {
            g2d.drawImage(cutoutImage, 0, 0, this);
        } else if (displayImage != null) {
            g2d.drawImage(displayImage, 0, 0, this);
        } else {
            g2d.setColor(Color.GRAY);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setColor(Color.WHITE);
            g2d.drawString("No image loaded.", getWidth()/2 - 50, getHeight()/2);
        }

        for (List<int[]> segment : paths) {
            drawPathSegment(g2d, segment, FINAL_PATH_COLOR, 2);
        }

        if (!currentLivePath.isEmpty() && currentSeedPoint != null && !pathIsClosed) {
            drawPathSegment(g2d, currentLivePath, LIVE_WIRE_COLOR, 2);
        }

        if (!highlightPathSegment.isEmpty()){
            drawPathSegment(g2d, highlightPathSegment, Color.ORANGE, 3);
        }

        for (int[] point : autoSeedPointHandler.getAutoSeedPoints()) {
            g2d.setColor(AUTO_SEED_COLOR);
            g2d.fillOval(point[0] - AUTO_SEED_RADIUS, point[1] - AUTO_SEED_RADIUS, AUTO_SEED_RADIUS * 2, AUTO_SEED_RADIUS * 2);
            g2d.setColor(Color.BLACK);
            g2d.drawOval(point[0] - AUTO_SEED_RADIUS, point[1] - AUTO_SEED_RADIUS, AUTO_SEED_RADIUS * 2, AUTO_SEED_RADIUS * 2);
        }

        if (currentSeedPoint != null && !pathIsClosed) {
            g2d.setColor(SEED_COLOR);
            g2d.fillOval(currentSeedPoint[0] - SEED_RADIUS, currentSeedPoint[1] - SEED_RADIUS, SEED_RADIUS * 2, SEED_RADIUS * 2);
        }

        if (currentSeedPoint != null && !pathIsClosed && firstPointOfContour != null && !firstPointOfContour.isEmpty() && lastMouseX != -1 && paths.size() >0) {
            int[] firstP = firstPointOfContour.get(0);

            int currentMouseX = lastMouseX;
            int currentMouseY = lastMouseY;

            if (preprocessor != null && preprocessor.getGradMag() != null) {
                int[] snappedLiveMouse = EdgeSnapUtil.snapToEdge(lastMouseX, lastMouseY, preprocessor.getGradMag(), 8);
                currentMouseX = snappedLiveMouse[0];
                currentMouseY = snappedLiveMouse[1];
            }


            double distToStart = Math.hypot(currentMouseX - firstP[0], currentMouseY - firstP[1]);
            if (distToStart < CLOSURE_SNAP_DISTANCE ) {
                g2d.setColor(CLOSURE_HINT_COLOR);
                g2d.fillOval(firstP[0] - SEED_RADIUS * 2, firstP[1] - SEED_RADIUS * 2, SEED_RADIUS * 4, SEED_RADIUS * 4);
            }
        }

        if (lastMouseX >= 0 && lastMouseY >= 0 && originalImage != null && !pathIsClosed && currentSeedPoint != null) {
            g2d.setColor(new Color(0, 180, 255, 60));
            int r = 8;
            g2d.fillOval(lastMouseX - r, lastMouseY - r, r * 2, r * 2);
            g2d.setColor(new Color(0, 120, 200, 120));
            g2d.drawOval(lastMouseX - r, lastMouseY - r, r * 2, r * 2);
        }

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        String instruction = "";
        if (pathIsClosed && !cutoutActive) instruction += " Path closed. Click 'Perform Cutout'.";
        if (cutoutActive) instruction = "Cutout applied. Save or Left-click to start new selection.";
        g2d.drawString(instruction, 10, 20);

        g2d.dispose();
    }
}