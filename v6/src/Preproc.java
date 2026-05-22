import java.awt.image.BufferedImage;
import java.awt.Color;

// Use Sobel kernel and Laplacian kernel to extract edge
public class Preproc {
    private BufferedImage image;
    private double[][] costMap;
    private double[][] gradDir;
    private double[][] gradMag;

    public Preproc(BufferedImage image) {
        this.image = image;
        this.costMap = new double[image.getHeight()][image.getWidth()];
        this.gradDir = new double[image.getHeight()][image.getWidth()];
        this.gradMag = new double[image.getHeight()][image.getWidth()];
        computeGradients();
    }

    private void computeGradients() {
        // Sobel 算子
        double[][] sobelX = {
                {-1, 0, 1},
                {-2, 0, 2},
                {-1, 0, 1}
        };
        double[][] sobelY = {
                {-1, -2, -1},
                {0, 0, 0},
                {1, 2, 1}
        };
        double[][] laplacianKernel = {
                {0, 1, 0},
                {1, -4, 1},
                {0, 1, 0}
        };

        double alpha = 0.8;
        double beta = 0.2;

        int H = image.getHeight();
        int W = image.getWidth();
        boolean isGray = isGrayImage(image);
        double[][] grayImg = new double[H][W];
        if (isGray) {
            // 灰度图：先做直方图均衡化
            int[] hist = new int[256];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    hist[r]++;
                    grayImg[y][x] = r / 255.0;
                }
            }
            // 累积分布
            int total = H * W;
            int[] cdf = new int[256];
            cdf[0] = hist[0];
            for (int i = 1; i < 256; i++) cdf[i] = cdf[i-1] + hist[i];
            // 均衡化
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int v = (int)(grayImg[y][x] * 255);
                    grayImg[y][x] = (cdf[v] - cdf[0]) * 1.0 / (total - cdf[0]);
                }
            }
        }
        for (int y = 1; y < H - 1; y++) {
            for (int x = 1; x < W - 1; x++) {
                if (isGray) {
                    // 灰度图Sobel
                    double gx = 0, gy = 0,L=0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            gx += grayImg[y + dy][x + dx] * sobelX[dy + 1][dx + 1];
                            gy += grayImg[y + dy][x + dx] * sobelY[dy + 1][dx + 1];
                            L += grayImg[y + dy][x + dx] * laplacianKernel[dy + 1][dx + 1];
                        }
                    }
                    gradMag[y][x] = alpha*Math.sqrt(gx * gx + gy * gy)+beta * Math.abs(L);
                    gradDir[y][x] = Math.atan2(gy, gx);
                } else {
                    // 彩色图多通道Sobel
                    double[] gx = new double[3];
                    double[] gy = new double[3];
                    double[] L = new double[3];
                    for (int c = 0; c < 3; c++) { gx[c] = 0; gy[c] = 0; }
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int rgb = image.getRGB(x + dx, y + dy);
                            int r = (rgb >> 16) & 0xFF;
                            int g = (rgb >> 8) & 0xFF;
                            int b = rgb & 0xFF;
                            double[] gray = {r / 255.0, g / 255.0, b / 255.0};
                            for (int c = 0; c < 3; c++) {
                                gx[c] += gray[c] * sobelX[dy + 1][dx + 1];
                                gy[c] += gray[c] * sobelY[dy + 1][dx + 1];
                                double v = grayImg[y + dy][x + dx] * laplacianKernel[dy + 1][dx + 1];
                                L[c] +=v;
                            }
                        }
                    }
                    double[] mag = new double[3];
                    double[] dir = new double[3];
                    for (int c = 0; c < 3; c++) {
                        mag[c] = alpha * Math.sqrt(gx[c] * gx[c] + gy[c] * gy[c])+beta*Math.abs(L[c]);
                        dir[c] = Math.atan2(gy[c], gx[c]);
                    }
                    // 融合最大值和均值
                    double maxMag = Math.max(mag[0], Math.max(mag[1], mag[2]));
                    double meanMag = (mag[0] + mag[1] + mag[2]) / 3.0;
                    gradMag[y][x] = 0.7 * maxMag + 0.3 * meanMag;
                    // 方向取最大通道
                    int maxIdx = 0;
                    for (int c = 1; c < 3; c++) if (mag[c] > mag[maxIdx]) maxIdx = c;
                    gradDir[y][x] = dir[maxIdx];
                }
            }
        }
        // 计算代价图
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                costMap[y][x] = 1.0 - gradMag[y][x];
            }
        }
    }

    // 判断是否为灰度图
    private boolean isGrayImage(BufferedImage img) {
        int H = img.getHeight(), W = img.getWidth();
        for (int y = 0; y < H; y += Math.max(1, H/10)) {
            for (int x = 0; x < W; x += Math.max(1, W/10)) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (!(r == g && g == b)) return false;
            }
        }
        return true;
    }

    public double[][] getCostMap() {
        return costMap;
    }

    public double[][] getGradDir() {
        return gradDir;
    }

    public double[][] getGradMag() {
        return gradMag;
    }

    public double[][] getLocalCostMap() {
        int blockSize = 50;
        double[][] localCostMap = new double[image.getHeight()][image.getWidth()];

        for (int y = 0; y < image.getHeight(); y += blockSize) {
            for (int x = 0; x < image.getWidth(); x += blockSize) {
                double maxGrad = 0;
                // 在块内找到最大梯度
                for (int dy = 0; dy < blockSize && y + dy < image.getHeight(); dy++) {
                    for (int dx = 0; dx < blockSize && x + dx < image.getWidth(); dx++) {
                        maxGrad = Math.max(maxGrad, gradMag[y + dy][x + dx]);
                    }
                }
                // 归一化块内的梯度
                for (int dy = 0; dy < blockSize && y + dy < image.getHeight(); dy++) {
                    for (int dx = 0; dx < blockSize && x + dx < image.getWidth(); dx++) {
                        if (maxGrad > 0) {
                            localCostMap[y + dy][x + dx] = 1.0 - (gradMag[y + dy][x + dx] / maxGrad);
                        } else {
                            localCostMap[y + dy][x + dx] = 1.0;
                        }
                    }
                }
            }
        }
        return localCostMap;
    }
}

