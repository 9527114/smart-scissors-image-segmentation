import java.util.List;
import java.util.ArrayList;

public class AutoSeedPoint {
    private static double CURVATURE_THRESHOLD = 0.09;  // 曲率阈值，适度灵敏
    private static double MIN_PATH_LENGTH_FOR_AUTO_SEED = 50.0;  // 最小路径长度 (renamed for clarity)
    private static final int CURVE_WINDOW = 7;  // 曲率计算窗口大小
    private static final int MIN_POINTS_BETWEEN_SEEDS = 30;  // 种子点最小间距
    private List<int[]> currentLivePathPoints;  // 用于计算曲率的点集 (renamed for clarity)
    private List<int[]> autoSeedPoints;  // 存储自动添加的种子点
    private int lastAutoSeedOnPathIndex = -1;  // 记录上一个自动种子点在currentLivePathPoints中的索引 (renamed for clarity)

    public AutoSeedPoint() {
        currentLivePathPoints = new ArrayList<>();
        autoSeedPoints = new ArrayList<>();
    }

    public void updatePathPoints(List<int[]> newLivePath) {
        if (newLivePath == null || newLivePath.size() < CURVE_WINDOW) {
            currentLivePathPoints.clear();
            return;
        }
        currentLivePathPoints.clear();
        currentLivePathPoints.addAll(newLivePath);
    }

    public boolean shouldAddSeedPoint() {
        if (currentLivePathPoints.size() < CURVE_WINDOW) return false;

        // 检查与上一个自动种子点的距离 (based on points along the live path)
        // This logic might need refinement if lastAutoSeedOnPathIndex refers to an old path configuration.
        // For simplicity, we check if enough new points have been added since the last auto-seed.
        // A more robust way would be to track the actual last auto-seed point's coordinates.
        if (lastAutoSeedOnPathIndex >= 0 && (currentLivePathPoints.size() - 1) - lastAutoSeedOnPathIndex < MIN_POINTS_BETWEEN_SEEDS) {
            // If the current path's end is not far enough from where the last auto seed was placed (on that path structure)
            // This simple index check assumes the path grows mostly linearly.
            // A better check would be geometric distance if the path can change drastically.
            // For now, sticking to an index-based check reflecting number of new points.
        }


        double pathLength = PathUtils.calculatePathLength(currentLivePathPoints);
        if (pathLength < MIN_PATH_LENGTH_FOR_AUTO_SEED) return false;

        double curvature = PathUtils.calculateAverageCurvature(currentLivePathPoints, CURVE_WINDOW);
        if (curvature > CURVATURE_THRESHOLD) {
            // Check distance from the *last manually placed seed point* or *start of the current segment*
            // to avoid placing auto-seeds too close to manual seeds.
            // The current logic in ImagePanel handles adding the point if this returns true.
            // The MIN_POINTS_BETWEEN_SEEDS check above is more about auto-seed to auto-seed distance.
            return true;
        }
        return false;
    }

    // Call this when an auto seed point is actually added based on shouldAddSeedPoint()
    public void recordAutoSeedAddition(int[] seedPointCoordinates) {
        if (!currentLivePathPoints.isEmpty()) {
            // Find the index of this seed point in the currentLivePathPoints or use the last point
            // For simplicity, assume it's the end of the currentLivePathPoints when shouldAddSeedPoint was true
            lastAutoSeedOnPathIndex = currentLivePathPoints.size() - 1;
            autoSeedPoints.add(new int[]{seedPointCoordinates[0], seedPointCoordinates[1]});
        }
    }


    public double getCurrentCurvature() {
        return PathUtils.calculateAverageCurvature(currentLivePathPoints, CURVE_WINDOW);
    }

    public double getCurrentPathLength() {
        return PathUtils.calculatePathLength(currentLivePathPoints);
    }

    public void setCurvatureThreshold(double threshold) {
        CURVATURE_THRESHOLD = threshold;
    }

    public void setMinPathLength(double length) {
        MIN_PATH_LENGTH_FOR_AUTO_SEED = length;
    }

    public List<int[]> getAutoSeedPoints() {
        return autoSeedPoints;
    }

    public void clearAutoSeedPoints() {
        autoSeedPoints.clear();
        lastAutoSeedOnPathIndex = -1;
        // currentLivePathPoints is cleared by updatePathPoints
    }
}