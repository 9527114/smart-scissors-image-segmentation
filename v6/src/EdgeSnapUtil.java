public class EdgeSnapUtil {
    /**
     * 在鼠标周围自动寻找最近的强边缘点，实现吸附功能。
     * @param x 鼠标x坐标
     * @param y 鼠标y坐标
     * @param edgeMap 边缘强度图（如梯度图）
     * @param radius 搜索半径
     * @return 吸附后的边缘点坐标 [x, y]
     */
    public static int[] snapToEdge(int x, int y, double[][] edgeMap, int radius) {
        int height = edgeMap.length;
        int width = edgeMap[0].length;
        int bestX = x;
        int bestY = y;
        double maxGrad = -1.0;
        double sumGrad = 0.0;
        int count = 0;

        // 计算局部平均梯度
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    sumGrad += edgeMap[ny][nx];
                    count++;
                }
            }
        }
        double avgGrad = count > 0 ? sumGrad / count : 0.0;

        // 寻找局部最强边缘点
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    double g = edgeMap[ny][nx];
                    // 梯度需高于局部平均值一定倍数，且为局部最大值
                    if (g > avgGrad * 1.2 && isLocalMaximum(nx, ny, edgeMap)) {
                        if (g > maxGrad) {
                            maxGrad = g;
                            bestX = nx;
                            bestY = ny;
                        }
                    }
                }
            }
        }
        return new int[]{bestX, bestY};
    }

    // 判断是否为局部最大值
    private static boolean isLocalMaximum(int x, int y, double[][] edgeMap) {
        double center = edgeMap[y][x];
        int width = edgeMap[0].length;
        int height = edgeMap.length;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    if (edgeMap[ny][nx] >= center) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
} 