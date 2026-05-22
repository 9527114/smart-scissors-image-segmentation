import java.util.LinkedList;
import java.util.List;

public class PathManager {
    private final double[][] costMap;
    private final double[][] gradDir;
    private final double[][] gradMag;
    private final List<List<int[]>> paths = new LinkedList<>();
    private int[] currentSeed = null;
    private List<int[]> currentLivePath = null;

    // Path validation constants
    private static final double MIN_ACCEPTABLE_PATH_LENGTH = 5.0;
    private static final double MAX_ACCEPTABLE_PATH_LENGTH = 2000.0;
    private static final double MAX_AVERAGE_COST_PER_PIXEL = 0.85;

    public PathManager(double[][] costMap, double[][] gradDir, double[][] gradMag) {
        this.costMap = costMap;
        this.gradDir = gradDir;
        this.gradMag = gradMag;
    }

    public void setSeed(int[] seed) {
        this.currentSeed = seed;
        this.currentLivePath = null;
    }

    public int[] getSeed() {
        return this.currentSeed;
    }

    public List<int[]> getLivePath(int[] mouseCoords) {
        if (currentSeed == null) return null;
        currentLivePath = PathFinder.findPath(costMap, gradDir, gradMag, currentSeed, mouseCoords);
        return currentLivePath;
    }

    public List<int[]> finalizeAndAddSegment(int[] target) {
        if (currentSeed == null) return null;

        List<int[]> finalSegment = PathFinder.findPath(costMap, gradDir, gradMag, currentSeed, target);

        if (finalSegment != null && !finalSegment.isEmpty()) {
            if (isValidPathSegment(finalSegment)) {
                List<int[]> optimizedSegment = optimizePathSegment(finalSegment);
                paths.add(optimizedSegment);
                currentSeed = target;
                return optimizedSegment;
            }
        }
        return null;
    }

    public void clearCurrentContourSegments() {
        paths.clear();
        currentSeed = null;
        currentLivePath = null;
    }

    public boolean undoLastSegment() {
        if (paths.isEmpty()) {
            return false;
        }
        paths.remove(paths.size() - 1);

        if (paths.isEmpty()) {
            currentSeed = null;
        } else {

            List<int[]> lastSegment = paths.get(paths.size() - 1);
            if (!lastSegment.isEmpty()) {
                currentSeed = lastSegment.get(lastSegment.size() - 1);
            } else {
                paths.remove(paths.size()-1);
                return undoLastSegment();
            }
        }
        currentLivePath = null;
        return true;
    }

    private boolean isValidPathSegment(List<int[]> segment) {
        if (segment.size() < 2) return false;

        double pathLength = PathUtils.calculatePathLength(segment);
        if (pathLength < MIN_ACCEPTABLE_PATH_LENGTH || pathLength > MAX_ACCEPTABLE_PATH_LENGTH) {

            return false;
        }

        double avgCost = calculateAverageCostOnPath(segment);
        if (avgCost > MAX_AVERAGE_COST_PER_PIXEL) {

            return false;
        }
        return true;
    }

    private List<int[]> optimizePathSegment(List<int[]> segment) {
        if (segment.size() <= 2) return segment;

        List<int[]> optimized = new LinkedList<>();
        optimized.add(segment.get(0));

        for (int i = 1; i < segment.size() - 1; i++) {
            int[] prev = segment.get(i - 1);
            int[] curr = segment.get(i);
            int[] next = segment.get(i + 1);

            if (!arePointsCollinear(prev, curr, next, 1.0)) {
                optimized.add(curr);
            }
        }
        optimized.add(segment.get(segment.size() - 1));
        return optimized;
    }

    private boolean arePointsCollinear(int[] p1, int[] p2, int[] p3, double tolerance) {

        long val = (long)(p2[1] - p1[1]) * (p3[0] - p2[0]) - (long)(p3[1] - p2[1]) * (p2[0] - p1[0]);
        return Math.abs(val) < tolerance;
    }


    private double calculateAverageCostOnPath(List<int[]> pathSegment) {
        if (pathSegment.isEmpty()) return 1.0; // Max cost if no path
        double totalCost = 0;
        int pointCount = 0;
        for (int[] point : pathSegment) {

            if (point[1] >= 0 && point[1] < costMap.length && point[0] >= 0 && point[0] < costMap[0].length) {
                totalCost += costMap[point[1]][point[0]];
                pointCount++;
            }
        }
        return (pointCount > 0) ? totalCost / pointCount : 1.0;
    }

    public List<List<int[]>> getCurrentContourSegments() {
        return paths;
    }
}