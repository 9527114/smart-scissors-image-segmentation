import java.util.*;

public class PathFinder {
    private static class Node implements Comparable<Node> {
        final int x, y;
        final double cost;
        final Node parent;
        final double gradient;  // 存储梯度值
        final int direction;  // 移动方向

        Node(int x, int y, double cost, Node parent, double gradient, int direction) {
            this.x = x;
            this.y = y;
            this.cost = cost;
            this.parent = parent;
            this.gradient = gradient;
            this.direction = direction;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.cost, other.cost);
        }
    }

    // 定义移动方向：上、右、下、左
    private static final int[][] CARDINAL_DIRECTIONS = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
    // 定义对角线方向
    private static final int[][] DIAGONAL_DIRECTIONS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

    // 梯度阈值
    private static final double GRADIENT_THRESHOLD = 0.12;  // 提高阈值，减少误检
    // 方向权重
    private static final double DIRECTION_WEIGHT = 0.4;  // 增加方向权重
    // 平滑度权重
    private static final double SMOOTHNESS_WEIGHT = 0.12;  // 增加平滑度权重
    // 边缘权重
    private static final double EDGE_WEIGHT = 0.5;  // 降低边缘权重
    // 距离权重
    private static final double DISTANCE_WEIGHT = 0.5;  // 增加距离权重
    // 局部搜索半径
    private static final int LOCAL_SEARCH_RADIUS = 6;  // 减小搜索半径
    // 平滑窗口大小
    private static final int SMOOTH_WINDOW = 2;
    // 曲线检测窗口
    private static final int CURVE_WINDOW = 7;
    // 最大搜索半径
    private static final int MAX_SEARCH_RADIUS = 30;  // 限制最大搜索半径
    // 细小边缘检测窗口
    private static final int THIN_EDGE_WINDOW = 3;
    // 细小边缘方向一致性阈值
    private static final double THIN_EDGE_DIRECTION_THRESHOLD = 0.92;  // 提高方向一致性要求

    public static List<int[]> findPath(double[][] costMap, double[][] gradDir, double[][] gradMag, int[] start, int[] end) {
        int height = costMap.length;
        int width = costMap[0].length;
        PriorityQueue<Node> pq = new PriorityQueue<>();
        boolean[][] visited = new boolean[height][width];
        double[][] minCost = new double[height][width];

        // 初始化
        for (int y = 0; y < height; y++) {
            Arrays.fill(minCost[y], Double.POSITIVE_INFINITY);
        }

        // 检查起点和终点是否有效
        if (!isValidPosition(start[0], start[1], width, height) ||
                !isValidPosition(end[0], end[1], width, height)) {
            return new LinkedList<>();
        }

        // 调整起点和终点到最近的强边缘
        start = findNearestStrongEdge(start, costMap);
        end = findNearestStrongEdge(end, costMap);

        // 计算初始方向
        int initialDir = calculateDirection(start, end);
        pq.offer(new Node(start[0], start[1], 0.0, null, costMap[start[1]][start[0]], initialDir));
        minCost[start[1]][start[0]] = 0.0;

        while (!pq.isEmpty()) {
            Node current = pq.poll();

            if (current.x == end[0] && current.y == end[1]) {
                return smoothPath(getPath(current));
            }

            if (visited[current.y][current.x]) continue;
            visited[current.y][current.x] = true;

            // 处理四个主要方向
            for (int i = 0; i < CARDINAL_DIRECTIONS.length; i++) {
                int[] dir = CARDINAL_DIRECTIONS[i];
                processNeighbor(current, dir[0], dir[1], 1.0, i, costMap, gradDir, gradMag, visited, minCost, pq, end);
            }

            // 处理对角线方向
            for (int i = 0; i < DIAGONAL_DIRECTIONS.length; i++) {
                int[] dir = DIAGONAL_DIRECTIONS[i];
                processNeighbor(current, dir[0], dir[1], Math.sqrt(2), i + 4, costMap, gradDir, gradMag, visited, minCost, pq, end);
            }
        }
        return new LinkedList<>();
    }

    private static int[] findNearestStrongEdge(int[] point, double[][] costMap) {
        int bestX = point[0];
        int bestY = point[1];
        double maxGrad = -1.0;
        double sumGrad = 0.0;
        int count = 0;

        // 计算局部平均值
        for (int dy = -LOCAL_SEARCH_RADIUS; dy <= LOCAL_SEARCH_RADIUS; dy++) {
            for (int dx = -LOCAL_SEARCH_RADIUS; dx <= LOCAL_SEARCH_RADIUS; dx++) {
                int nx = point[0] + dx;
                int ny = point[1] + dy;
                if (isValidPosition(nx, ny, costMap[0].length, costMap.length)) {
                    sumGrad += costMap[ny][nx];
                    count++;
                }
            }
        }
        double avgGrad = sumGrad / count;

        // 寻找最强的边缘点
        for (int dy = -LOCAL_SEARCH_RADIUS; dy <= LOCAL_SEARCH_RADIUS; dy++) {
            for (int dx = -LOCAL_SEARCH_RADIUS; dx <= LOCAL_SEARCH_RADIUS; dx++) {
                int nx = point[0] + dx;
                int ny = point[1] + dy;
                if (isValidPosition(nx, ny, costMap[0].length, costMap.length)) {
                    double g = costMap[ny][nx];
                    // 如果梯度显著高于平均值，且是局部最大值
                    if (g > avgGrad * 1.5 && isLocalMaximum(nx, ny, costMap)) {
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

    private static boolean isLocalMaximum(int x, int y, double[][] costMap) {
        double center = costMap[y][x];
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (isValidPosition(nx, ny, costMap[0].length, costMap.length)) {
                    if (costMap[ny][nx] >= center) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void processNeighbor(Node current, int dy, int dx, double distanceMultiplier,
                                      int direction, double[][] costMap, double[][] gradDir, double[][] gradMag, boolean[][] visited,
                                      double[][] minCost, PriorityQueue<Node> pq, int[] end) {
        int ny = current.y + dy;
        int nx = current.x + dx;

        if (isValidPosition(nx, ny, costMap[0].length, costMap.length) &&
                !visited[ny][nx] && costMap[ny][nx] < Double.POSITIVE_INFINITY) {

            // 计算到起点的距离
            double distanceToStart = Math.sqrt(
                Math.pow(nx - current.x, 2) + 
                Math.pow(ny - current.y, 2)
            );

            // 跳跃距离惩罚
            double jumpDistance = Math.sqrt(Math.pow(nx - current.x, 2) + Math.pow(ny - current.y, 2));
            double jumpPenalty = 0.0;
            if (jumpDistance > 1.0) {
                jumpPenalty = 40.0;  // 增加跳跃惩罚
            }

            // 如果距离太远，跳过
            if (distanceToStart > MAX_SEARCH_RADIUS) {
                return;
            }

            // 计算方向变化代价
            double directionCost = calculateDirectionCost(current.direction, direction);

            // 计算平滑度代价
            double smoothnessCost = calculateSmoothnessCost(current, nx, ny);

            // 计算边缘代价
            double edgeCost = calculateEdgeCost(nx, ny, costMap);

            // 计算局部曲率代价
            double curvatureCost = calculateCurvatureCost(current, nx, ny);

            // 计算到终点的方向一致性代价
            double targetDirectionCost = calculateTargetDirectionCost(current, nx, ny, end);

            // 计算方向性Cost
            double edgeOri = gradDir[ny][nx];
            double moveOri = Math.atan2(ny - current.y, nx - current.x);
            double directionality = Math.abs(Math.cos(edgeOri - moveOri));
            double directionalityCost = 1.0 - directionality;

            // 升级：弱边缘惩罚
            double weakEdgePenalty = 0.0;
            double directionalityWeight = 1.0;  // 降低方向性权重
            double edgeWeight = EDGE_WEIGHT;

            // 检查是否为细小边缘
            boolean isThinEdge = isThinEdge(nx, ny, costMap);
            
            if (isThinEdge) {
                weakEdgePenalty = 0.0;
                directionalityWeight = 1.5;  // 降低方向性权重
                edgeWeight = 0.7;  // 降低边缘权重
                
                // 如果方向变化太大，增加惩罚
                if (current.parent != null) {
                    double prevOri = Math.atan2(current.y - current.parent.y, current.x - current.parent.x);
                    double currentOri = Math.atan2(ny - current.y, nx - current.x);
                    double angleDiff = Math.abs(Math.cos(prevOri - currentOri));
                    if (angleDiff < 0.8) {  // 提高角度变化阈值
                        weakEdgePenalty = 15.0;  // 增加惩罚
                    }
                }
            } else if (gradMag[ny][nx] < 0.2) {  // 提高弱边缘阈值
                weakEdgePenalty = 30.0;  // 增加弱边缘惩罚
                directionalityWeight = 0.2;
                edgeWeight = 0.1;
            }

            // 计算新的总代价
            double newCost = current.cost +
                    (costMap[ny][nx] * distanceMultiplier) +
                    (directionCost * DIRECTION_WEIGHT) +
                    (smoothnessCost * SMOOTHNESS_WEIGHT) +
                    (edgeCost * edgeWeight) +
                    (curvatureCost * 0.15) +  // 增加曲率权重
                    (targetDirectionCost * 0.4) +  // 增加目标方向权重
                    (distanceToStart / MAX_SEARCH_RADIUS * DISTANCE_WEIGHT) +
                    (directionalityCost * directionalityWeight) +
                    weakEdgePenalty +
                    jumpPenalty;

            if (newCost < minCost[ny][nx]) {
                minCost[ny][nx] = newCost;
                pq.offer(new Node(nx, ny, newCost, current, costMap[ny][nx], direction));
            }
        }
    }

    private static double calculateDirectionCost(int currentDir, int newDir) {
        int diff = Math.abs(currentDir - newDir);
        return Math.min(diff, 8 - diff) / 4.0;  // 归一化到[0,1]
    }

    private static double calculateSmoothnessCost(Node current, int nx, int ny) {
        if (current.parent == null) return 0;

        double angle1 = calculateAngle(current.parent.x, current.parent.y, current.x, current.y);
        double angle2 = calculateAngle(current.x, current.y, nx, ny);
        return Math.abs(angle1 - angle2) / Math.PI;  // 归一化到[0,1]
    }

    private static double calculateEdgeCost(int x, int y, double[][] costMap) {
        double maxGrad = 0.0;
        double sumGrad = 0.0;
        int count = 0;
        int height = costMap.length;
        int width = costMap[0].length;

        // 计算局部梯度和平均值
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (isValidPosition(nx, ny, width, height)) {
                    double grad = costMap[ny][nx];
                    maxGrad = Math.max(maxGrad, grad);
                    sumGrad += grad;
                    count++;
                }
            }
        }

        double avgGrad = sumGrad / count;
        double currentGrad = costMap[y][x];

        // 检测曲线特征
        boolean isCurvePoint = isCurvePoint(x, y, costMap);
        boolean isStrongEdge = isStrongEdge(x, y, costMap);
        boolean isThinEdge = isThinEdge(x, y, costMap);

        // 如果是细小边缘
        if (isThinEdge) {
            if (isStrongEdge) {
                return 0.01;  // 提高细小边缘的代价
            }
            if (currentGrad > avgGrad * 1.2) {  // 提高梯度要求
                return 0.05;
            }
            return 0.1;
        }

        // 如果是强边缘点
        if (isStrongEdge) {
            return isCurvePoint ? 0.02 : 0.05;
        }

        // 如果当前点梯度显著高于平均值
        if (currentGrad > avgGrad * 1.3) {  // 提高梯度要求
            return isCurvePoint ? 0.1 : 0.2;
        }

        // 如果当前点梯度高于平均值
        if (currentGrad > avgGrad) {
            return isCurvePoint ? 0.3 : 0.5;
        }

        // 否则根据梯度强度计算代价
        return 1.0 - (maxGrad * 1.5);  // 降低梯度影响
    }

    private static boolean isStrongEdge(int x, int y, double[][] costMap) {
        int height = costMap.length;
        int width = costMap[0].length;
        double centerGrad = costMap[y][x];

        // 计算局部梯度方向
        double[] gradients = new double[8];
        int[] dx = {-1, -1, -1, 0, 1, 1, 1, 0};
        int[] dy = {-1, 0, 1, 1, 1, 0, -1, -1};

        // 计算8个方向的梯度
        for (int i = 0; i < 8; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];
            if (isValidPosition(nx, ny, width, height)) {
                gradients[i] = costMap[ny][nx];
            }
        }

        // 计算梯度变化
        int strongCount = 0;
        for (int i = 0; i < 8; i++) {
            if (gradients[i] > centerGrad * 0.8) {  // 如果周围点梯度接近中心点
                strongCount++;
            }
        }

        return strongCount >= 3;  // 至少3个方向有强梯度
    }

    private static boolean isCurvePoint(int x, int y, double[][] costMap) {
        int height = costMap.length;
        int width = costMap[0].length;
        double centerGrad = costMap[y][x];

        // 计算局部梯度方向
        double[] gradients = new double[16];  // 增加采样点
        int[] dx = {-2, -2, -2, -1, -1, -1, 0, 0, 1, 1, 1, 2, 2, 2, 0, 0};
        int[] dy = {-1, 0, 1, -2, 0, 2, -2, 2, -2, 0, 2, -1, 0, 1, -2, 2};

        for (int i = 0; i < 16; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];
            if (isValidPosition(nx, ny, width, height)) {
                gradients[i] = costMap[ny][nx];
            }
        }

        // 检测梯度变化模式
        int signChanges = 0;
        for (int i = 0; i < 16; i++) {
            int next = (i + 1) % 16;
            if ((gradients[i] - centerGrad) * (gradients[next] - centerGrad) < 0) {
                signChanges++;
            }
        }

        // 计算梯度变化率
        double maxChange = 0;
        for (int i = 0; i < 16; i++) {
            int next = (i + 1) % 16;
            double change = Math.abs(gradients[i] - gradients[next]);
            maxChange = Math.max(maxChange, change);
        }

        // 如果梯度变化模式符合曲线特征且变化率较大
        return signChanges >= 6 && maxChange > centerGrad * 0.5;
    }

    private static double calculateCurvatureCost(Node current, int nx, int ny) {
        if (current.parent == null) return 0;

        // 计算三点形成的角度
        double angle1 = calculateAngle(current.parent.x, current.parent.y, current.x, current.y);
        double angle2 = calculateAngle(current.x, current.y, nx, ny);
        double angleDiff = Math.abs(angle1 - angle2);

        // 如果角度变化太大，增加代价
        if (angleDiff > Math.PI / 2) {  // 90度
            return 1.0;
        }
        return angleDiff / (Math.PI / 2);  // 归一化到[0,1]
    }

    private static int calculateDirection(int[] start, int[] end) {
        double dx = end[0] - start[0];
        double dy = end[1] - start[1];
        double angle = Math.atan2(dy, dx);
        return (int) Math.round(angle * 4 / Math.PI) % 8;
    }

    private static double calculateAngle(int x1, int y1, int x2, int y2) {
        return Math.atan2(y2 - y1, x2 - x1);
    }

    private static boolean isValidPosition(int x, int y, int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private static List<int[]> getPath(Node node) {
        LinkedList<int[]> path = new LinkedList<>();
        while (node != null) {
            path.addFirst(new int[]{node.x, node.y});
            node = node.parent;
        }
        return path;
    }

    private static List<int[]> smoothPath(List<int[]> path) {
        if (path.size() <= 2) return path;

        List<int[]> smoothed = new LinkedList<>();
        smoothed.add(path.get(0));

        // 使用滑动窗口进行平滑
        for (int i = 1; i < path.size() - 1; i++) {
            int[] prev = path.get(Math.max(0, i - SMOOTH_WINDOW/2));
            int[] curr = path.get(i);
            int[] next = path.get(Math.min(path.size() - 1, i + SMOOTH_WINDOW/2));

            double angle1 = calculateAngle(prev[0], prev[1], curr[0], curr[1]);
            double angle2 = calculateAngle(curr[0], curr[1], next[0], next[1]);
            double angleDiff = Math.abs(angle1 - angle2);

            if (angleDiff > Math.PI / 6) {
                smoothed.add(curr);
            }
        }
        smoothed.add(path.get(path.size() - 1));

        // 添加插值点使路径更平滑
        List<int[]> interpolated = interpolatePath(smoothed);

        // 简单均值滤波平滑，减少毛刺
        int window = 3;
        List<int[]> filtered = new LinkedList<>();
        for (int i = 0; i < interpolated.size(); i++) {
            int sumX = 0, sumY = 0, count = 0;
            for (int j = -window/2; j <= window/2; j++) {
                int idx = i + j;
                if (idx >= 0 && idx < interpolated.size()) {
                    sumX += interpolated.get(idx)[0];
                    sumY += interpolated.get(idx)[1];
                    count++;
                }
            }
            filtered.add(new int[]{sumX / count, sumY / count});
        }
        if (filtered.size() > 2) {
            filtered.set(0, new int[]{
                (2 * filtered.get(0)[0] + filtered.get(1)[0]) / 3,
                (2 * filtered.get(0)[1] + filtered.get(1)[1]) / 3
            });
            int n = filtered.size();
            filtered.set(n-1, new int[]{
                (2 * filtered.get(n-1)[0] + filtered.get(n-2)[0]) / 3,
                (2 * filtered.get(n-1)[1] + filtered.get(n-2)[1]) / 3
            });
        }

        // 跳跃修复：检测相邻点距离过大，进行插值补点
        List<int[]> jumpFixed = new LinkedList<>();
        jumpFixed.add(filtered.get(0));
        for (int i = 1; i < filtered.size(); i++) {
            int[] prev = jumpFixed.get(jumpFixed.size() - 1);
            int[] curr = filtered.get(i);
            double dist = Math.hypot(curr[0] - prev[0], curr[1] - prev[1]);
            if (dist > 2.0) { // 跳跃阈值
                int steps = (int) Math.ceil(dist);
                for (int s = 1; s < steps; s++) {
                    double t = (double) s / steps;
                    int x = (int) Math.round(prev[0] + t * (curr[0] - prev[0]));
                    int y = (int) Math.round(prev[1] + t * (curr[1] - prev[1]));
                    jumpFixed.add(new int[]{x, y});
                }
            }
            jumpFixed.add(curr);
        }
        return jumpFixed;
    }

    private static List<int[]> interpolatePath(List<int[]> path) {
        List<int[]> interpolated = new LinkedList<>();
        interpolated.add(path.get(0));

        for (int i = 0; i < path.size() - 1; i++) {
            int[] p1 = path.get(i);
            int[] p2 = path.get(i + 1);

            // 计算两点之间的距离
            double dist = Math.sqrt(Math.pow(p2[0] - p1[0], 2) + Math.pow(p2[1] - p1[1], 2));

            // 如果距离大于阈值，添加插值点
            if (dist > 1.5) {  // 进一步减小距离阈值
                int steps = (int) (dist / 0.8);  // 减小步长，增加插值点密度
                for (int j = 1; j < steps; j++) {
                    double t = (double) j / steps;
                    // 使用贝塞尔插值使曲线更平滑
                    int x = (int) (p1[0] + t * (p2[0] - p1[0]));
                    int y = (int) (p1[1] + t * (p2[1] - p1[1]));
                    interpolated.add(new int[]{x, y});
                }
            }

            interpolated.add(p2);
        }

        return interpolated;
    }

    // 修改目标方向计算逻辑
    private static double calculateTargetDirectionCost(Node current, int nx, int ny, int[] end) {
        // 计算当前移动方向
        double dx = nx - current.x;
        double dy = ny - current.y;
        
        // 计算到终点的方向
        double targetDx = end[0] - current.x;
        double targetDy = end[1] - current.y;
        
        // 计算两个向量的夹角余弦值
        double dotProduct = dx * targetDx + dy * targetDy;
        double magnitude1 = Math.sqrt(dx * dx + dy * dy);
        double magnitude2 = Math.sqrt(targetDx * targetDx + targetDy * targetDy);
        
        if (magnitude1 == 0 || magnitude2 == 0) {
            return 1.0;
        }
        
        double cosAngle = dotProduct / (magnitude1 * magnitude2);
        // 将余弦值转换为[0,1]范围的代价，1表示完全同向，0表示完全反向
        return 1.0 - (cosAngle + 1.0) / 2.0;
    }

    private static boolean isThinEdge(int x, int y, double[][] costMap) {
        int height = costMap.length;
        int width = costMap[0].length;
        double centerGrad = costMap[y][x];
        
        if (centerGrad < GRADIENT_THRESHOLD * 0.9) {  // 提高基础梯度要求
            return false;
        }

        // 计算局部梯度方向
        int[] dx = {-1, -1, -1, 0, 1, 1, 1, 0};
        int[] dy = {-1, 0, 1, 1, 1, 0, -1, -1};
        double[] gradients = new double[8];
        double[] directions = new double[8];
        
        // 计算8个方向的梯度和方向
        for (int i = 0; i < 8; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];
            if (isValidPosition(nx, ny, width, height)) {
                gradients[i] = costMap[ny][nx];
                directions[i] = Math.atan2(dy[i], dx[i]);
            }
        }

        // 计算梯度变化和方向一致性
        int strongCount = 0;
        int weakCount = 0;
        double maxNeighborGrad = 0.0;
        double directionConsistency = 0.0;
        
        // 计算主要方向
        double mainDirection = 0.0;
        double maxGradSum = 0.0;
        for (int i = 0; i < 8; i++) {
            if (gradients[i] > centerGrad * 0.8) {  // 提高强梯度点的要求
                mainDirection += directions[i] * gradients[i];
                maxGradSum += gradients[i];
            }
        }
        if (maxGradSum > 0) {
            mainDirection /= maxGradSum;
        }

        // 计算方向一致性
        for (int i = 0; i < 8; i++) {
            maxNeighborGrad = Math.max(maxNeighborGrad, gradients[i]);
            if (gradients[i] > centerGrad * 0.8) {  // 提高强梯度点的要求
                strongCount++;
                // 计算与主方向的一致性
                double dirDiff = Math.abs(Math.cos(directions[i] - mainDirection));
                directionConsistency += dirDiff * gradients[i];
            } else if (gradients[i] < centerGrad * 0.4) {  // 提高弱梯度点的要求
                weakCount++;
            }
        }

        // 归一化方向一致性
        if (maxGradSum > 0) {
            directionConsistency /= maxGradSum;
        }

        // 判断是否为细小边缘
        return strongCount <= 2 && 
               weakCount >= 5 &&  // 增加弱梯度点的要求
               maxNeighborGrad < centerGrad * 1.2 &&  // 降低最大邻居梯度限制
               directionConsistency > THIN_EDGE_DIRECTION_THRESHOLD;
    }
}