import java.util.List;

public class PathUtils {

    public static double calculatePathLength(List<int[]> pathPoints) {
        if (pathPoints == null || pathPoints.size() < 2) {
            return 0;
        }
        double length = 0;
        for (int i = 1; i < pathPoints.size(); i++) {
            int[] p1 = pathPoints.get(i - 1);
            int[] p2 = pathPoints.get(i);
            length += Math.sqrt(
                    Math.pow(p2[0] - p1[0], 2) +
                            Math.pow(p2[1] - p1[1], 2)
            );
        }
        return length;
    }

    public static double calculateAverageCurvature(List<int[]> pathPoints, int curveWindow) {
        if (pathPoints == null || pathPoints.size() < curveWindow || curveWindow < 3) {
            return 0;
        }

        double totalCurvature = 0;
        int count = 0;

        for (int i = curveWindow - 1; i < pathPoints.size(); i++) {
            int[] p1 = pathPoints.get(i - 2);
            int[] p2 = pathPoints.get(i - 1);
            int[] p3 = pathPoints.get(i);

            double[] v1 = {p2[0] - p1[0], p2[1] - p1[1]};
            double[] v2 = {p3[0] - p2[0], p3[1] - p2[1]};

            double len1 = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1]);
            double len2 = Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1]);

            if (len1 == 0 || len2 == 0) continue;

            double dotProduct = v1[0] * v2[0] + v1[1] * v2[1];
            double cosAngle = dotProduct / (len1 * len2);

            cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));

            double angle = Math.acos(cosAngle);

            if (len1 + len2 > 0) {
                totalCurvature += angle / (len1 + len2);

                count++;
            }
        }
        return count > 0 ? totalCurvature / count : 0;
    }

    public static double calculateCurvatureAtEnd(List<int[]> pathPoints) {
        if (pathPoints == null || pathPoints.size() < 3) return 0;

        int[] p1 = pathPoints.get(pathPoints.size() - 3);
        int[] p2 = pathPoints.get(pathPoints.size() - 2);
        int[] p3 = pathPoints.get(pathPoints.size() - 1);

        double[] v1 = {p2[0] - p1[0], p2[1] - p1[1]};
        double[] v2 = {p3[0] - p2[0], p3[1] - p2[1]};

        double len1 = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1]);
        double len2 = Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1]);

        if (len1 == 0 || len2 == 0) return 0;

        double dotProduct = v1[0] * v2[0] + v1[1] * v2[1];
        double cosAngle = dotProduct / (len1 * len2);
        cosAngle = Math.max(-1, Math.min(1, cosAngle));
        double angle = Math.acos(cosAngle);

        return (len1 + len2 > 0) ? angle / (len1 + len2) : 0;
    }
}