import java.awt.image.BufferedImage;
import java.awt.Color;
import java.util.List;
import java.util.ArrayList;

public class IntelligentScissors {
    private BufferedImage image;
    private Preproc preprocessor;
    private PathFinder pathFinder;

    public IntelligentScissors(BufferedImage image) {
        this.image = image;
        this.preprocessor = new Preproc(image);
        this.pathFinder = new PathFinder();
    }

    public List<int[]> findPath(int[] start, int[] end) {
        return pathFinder.findPath(preprocessor.getCostMap(), preprocessor.getGradDir(), preprocessor.getGradMag(), start, end);
    }

    public BufferedImage getResultImage(List<int[]> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);

        // 复制原始图像
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                result.setRGB(x, y, image.getRGB(x, y));
            }
        }

        // 绘制路径
        for (int i = 1; i < path.size(); i++) {
            int[] p1 = path.get(i-1);
            int[] p2 = path.get(i);
            drawLine(result, p1[0], p1[1], p2[0], p2[1]);
        }

        return result;
    }

    private void drawLine(BufferedImage image, int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = (x1 < x2) ? 1 : -1;
        int sy = (y1 < y2) ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (x1 >= 0 && x1 < image.getWidth() && y1 >= 0 && y1 < image.getHeight()) {
                image.setRGB(x1, y1, Color.RED.getRGB());
            }

            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    public double[][] getCostMap() {
        return preprocessor.getCostMap();
    }

    public double[][] getLocalCostMap() {
        return preprocessor.getLocalCostMap();
    }
}
