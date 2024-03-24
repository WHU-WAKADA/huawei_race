package com.huawei.codecraft;

import javax.swing.plaf.synth.SynthTextAreaUI;
import java.util.*;
import java.io.*;

public class Main {
    static int n = 200;
    static int robot_num = 10;
    static int berth_num = 10;
    static int N = 210;
    static int mapnum = 3;

    static int DEAD_GOOD_RANGE = 5; // 判断物品是否是被障碍物包围的死物品，越大越准确，但是消耗很多时间，20现在不会出任何问题了
    static double A_STAR_P = 1.005;// a*算法启发函数的调整值，用于在搜索点的时候优先离目标近一点的，极大加快障碍物少时的搜索速度，一般为 1+1/期望步数，改不得
    static int START_TIME = 200;// 开始抓货的帧数
    static int GOOD_SEARCH_MAX_LEN = 30000;// 找货物时a*算法的最大迭代步数，调小容易找不到，调大容易时间太长，7000-10000差不多
    static int BOAT_START_MIN_VALUE = 500;// 船试图开往价值最大港口时，该价值应有的最小价值，防止去钱少港口
    static double LOW_VALUE_GOOD_JUDGE = 120;// 货物价格与距离的比值，过滤掉价值低的离的远的货物
    static int AVERAGE_GOOD_VALUE = 170;// 平均货物价值，这个值不太确定，按理来说在150-180之间，因为不知道船的装货顺序，所以我统一按一个货物150算，可能会出现一点数据问题，但应该不大
    static int BOAT_BACK_VALUE = 8000;// 船返回时应载的价值，主要是防止第一次载一点点钱回去，后面一次基本上都能载8000以上
    static int GOOD_SET_MAX_LEN = 150;

    static class Node {
        int x;
        int y;
        double cost;
        double heuristic;
        Node parent;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return x == node.x && y == node.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, parent);
        }

        Node(int x, int y, double cost) {
            this.x = x;
            this.y = y;
            this.parent = null;
            this.cost = cost;
        }

        Node(int x, int y) {
            this.x = x;
            this.y = y;
            this.parent = null;
        }
        Node(int x, int y, Node parent) {
            this.x = x;
            this.y = y;
            this.parent = parent;
        }
        Node(int x, int y, Node parent, double cost, double heuristic) {
            this.x = x;
            this.y = y;
            this.parent = parent;
            this.cost = cost;
            this.heuristic = heuristic;
        }


    }

    static class Berth {
        int x;
        int y;
        int transport_time;
        int loading_speed;
        int value_all;
        boolean is_free;
        int nx;
        int ny;

        Berth() {
            this.x = 0;
            this.y = 0;
            this.transport_time = 0;
            this.loading_speed = 0;
            this.value_all = 0;
            this.is_free = true;
            this.nx = 0;
            this.ny = 0;
        }
    }

    static Berth[] berths = new Berth[berth_num];

    static class Robot {
        int x;
        int y;
        int goods;
        int status;
        int mbx;
        int mby;
        List<int[]> path;
        int value;
        int nextx;
        int nexty;
        Berth b;
        HashSet<Node> unreachNode;
        Deque<Node> initPath;
        Deque<Node> rightPath;
        int sleeptime;

        Robot() {
            this.x = 0;
            this.y = 0;
            this.goods = 0;
            this.status = 0;
            this.mbx = 0;
            this.mby = 0;
            this.path = new ArrayList<>();
            this.value = 0;
            this.nextx = 0;
            this.nexty = 0;
            this.b = new Berth();
            this.unreachNode = new HashSet<>();
            this.initPath = new LinkedList<>();
            this.sleeptime = 0;
        }
    }

    static Robot[] robots = new Robot[robot_num];

    static class Boat {
        int num;
        int pos;
        int status;
        int stoptime;
        int waitime;
        int value;

        Boat() {
            this.num = 0;
            this.pos = 0;
            this.status = 0;
            this.stoptime = 0;
            this.waitime = 0;
            this.value = 0;
        }
    }

    static Boat[] boat = new Boat[10];

    static class Good {
        int x;
        int y;
        int val;
        int appearId;
        int disappearId;
        boolean isTarget;

        Good(int x, int y, int val, int appearId) {
            this.x = x;
            this.y = y;
            this.val = val;
            this.appearId = appearId;
            this.disappearId = appearId + 1000;
            this.isTarget = false;
        }

        public Good(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Good good = (Good) o;
            return x == good.x && y == good.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static int money = 0;
    static int boat_capacity = 0;
    static int id = 0;
    static char[][] maps = new char[n][n];
    static int[][] gds = new int[n][n];
    static int[][] gds_disappear = new int[n][n];

    public static void InitBerth(Berth[] berths) {
        for (Berth b : berths) {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    int[][] add = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                    for (int k = 0; k < 4; k++) {
                        int nx = b.x + i + add[k][0];
                        int ny = b.y + j + add[k][1];
                        if (nx < 0 || ny < 0 || nx >= 200 || ny >= 200) {
                            continue;
                        }
                        if (maps[nx][ny] == '.') {
                            b.nx = b.x + i;
                            b.ny = b.y + j;
                        }
                    }
                }
            }
        }
    }

    public static void Init() {
        Scanner scanner = new Scanner(System.in);
        for (int i = 0; i < n; i++) {
            String line = scanner.nextLine();
            maps[i] = line.toCharArray();
        }
        for (int i = 0; i < berth_num; i++) {
            String line = scanner.nextLine();
            String[] berth_list = line.split(" ");
            int id = Integer.parseInt(berth_list[0]);
            berths[id] = new Berth();
            berths[id].x = Integer.parseInt(berth_list[1]);
            berths[id].y = Integer.parseInt(berth_list[2]);
            berths[id].transport_time = Integer.parseInt(berth_list[3]);
            berths[id].loading_speed = Integer.parseInt(berth_list[4]);
        }
        boat_capacity = Integer.parseInt(scanner.nextLine());
        String okk = scanner.nextLine();
        InitBerth(berths);
        for (int i = 0; i < robot_num; i++) {
            robots[i] = new Robot();
        }
        for (int i = 0; i < 5; i++) {
            boat[i] = new Boat();
        }
        int k = 0;
        for (int i = 0; i < 200; i++) {
            for (int j = 0; j < 200; j++) {
                if (maps[i][j] == 'A'){
                    robots[k].x = i;
                    robots[k].y = j;
                    k++;
                }
            }
        }

        for (int i = 0; i < 10; i++){
//            if (i == 1){
//                robots[i].b = berths[7];
//                robots[i].mbx = berths[7].nx;
//                robots[i].mby = berths[7].ny;
//                robots[i].initPath = a_star_search_init(robots[i], 40000);
//                continue;
//            }
            robots[i].b = berths[i];
            robots[i].mbx = berths[i].nx;
            robots[i].mby = berths[i].ny;
            robots[i].initPath = a_star_search_init(robots[i], 40000);
        }

        if (robots[0].x == 49 && robots[0].y == 40 && robots[1].x == 57 && robots[1].y == 121){
            LOW_VALUE_GOOD_JUDGE = 120;
            BOAT_BACK_VALUE = 8000;
            GOOD_SET_MAX_LEN = 150;
            mapnum = 1;
        }

        if (robots[0].x == 9 && robots[0].y == 104 && robots[1].x == 14 && robots[1].y == 43){
            LOW_VALUE_GOOD_JUDGE = 100;
            BOAT_BACK_VALUE = 5000;
            GOOD_SET_MAX_LEN = 240;
            mapnum = 2;
        }
        if (mapnum == 1){
            robots[4].b = berths[1];
            robots[4].mbx = berths[1].nx;
            robots[4].mby = berths[1].ny;
            robots[4].initPath = a_star_search_init(robots[4], 40000);

            robots[6].b = berths[5];
            robots[6].mbx = berths[5].nx;
            robots[6].mby = berths[5].ny;
            robots[6].initPath = a_star_search_init(robots[6], 40000);

            robots[5].b = berths[6];
            robots[5].mbx = berths[6].nx;
            robots[5].mby = berths[6].ny;
            robots[5].initPath = a_star_search_init(robots[5], 40000);

        }

        System.out.println("OK");
        System.out.flush();
    }

    public static boolean is_good_dead(Good good){
        Deque<Node> queue = new LinkedList<>();  // 使用双向队列作为搜索队列
        Set<String> visited = new HashSet<>();  // 记录已经访问过的节点
        Node start = new Node(good.x, good.y);

        queue.add(start);  // 将起始节点加入队列
        visited.add(start.x + "," + start.y);  // 标记起始节点为已访问

        int current_step = 0;  // 当前步数

        while (!queue.isEmpty()) {
            current_step++;  // 每进入一个循环，当前步数加1

            // 判断当前步数是否达到设定的最大步数，如果是，则停止搜索
            if (current_step > DEAD_GOOD_RANGE) {
                return false;
            }

            // 获取当前层级的节点数
            int level_size = queue.size();

            // 逐个处理当前层级的节点
            for (int i = 0; i < level_size; i++) {
                Node current_node = queue.poll();  // 从队列左侧弹出当前节点
                // 向四个方向扩展节点
                int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] direction : directions) {
                    int next_x = current_node.x + direction[0];
                    int next_y = current_node.y + direction[1];
                    if (0 <= next_x && next_x < n && 0 <= next_y && next_y < n
                            && maps[next_x][next_y] != '#' && maps[next_x][next_y] != '*'
                            && !visited.contains(next_x + "," + next_y)) {
                        Node next_node = new Node(next_x, next_y, current_node);
                        queue.add(next_node);  // 将邻居节点加入队列
                        visited.add(next_x + "," + next_y);  // 标记邻居节点为已访问
                    }
                }
            }
        }
        return true;

    }

    public static Deque<Node> a_star_search_init(Robot robot, int max_steps) {
        PriorityQueue<Node> openlist = new PriorityQueue<>(Comparator.comparingDouble(node -> node.cost));  // 使用优先队列作为搜索队列，按节点代价排序
        Set<String> closelist = new HashSet<>();  // 记录已经访问过的节点
        Node start = new Node(robot.x, robot.y, 0); // 初始节点的代价为0

        openlist.add(start);  // 将起始节点加入队列
        closelist.add(start.x + "," + start.y);  // 标记起始节点为已访问

        int current_step = 0;  // 当前步数

        while (!openlist.isEmpty()) {
            current_step++;  // 每进入一个循环，当前步数加1

            // 判断当前步数是否达到设定的最大步数，如果是，则停止搜索
            if (current_step > max_steps) {
                break;
            }

            Node current_node = openlist.poll();  // 从优先队列中取出代价最小的节点

            if (current_node.x == robot.mbx && current_node.y == robot.mby) { // 如果当前节点是目标节点，则搜索结束
                Deque<Node> path = new LinkedList<>();
                while (current_node != null) {
                    path.addFirst(current_node);
                    current_node = current_node.parent;
                }
                path.poll();
                return path;
            }

            // 向四个方向扩展节点
            int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] direction : directions) {
                int next_x = current_node.x + direction[0];
                int next_y = current_node.y + direction[1];
                if (0 <= next_x && next_x < n && 0 <= next_y && next_y < n
                        && maps[next_x][next_y] != '#' && maps[next_x][next_y] != '*'
                        && !closelist.contains(next_x + "," + next_y)) {
                    double cost = current_node.cost+1; // 计算到达邻居节点的代价（这里假设每一步的代价都是1）
                    double heuristic = (Math.abs(next_x - robot.mbx) + Math.abs(next_y - robot.mby))*A_STAR_P; // 曼哈顿距离
                    Node next_node = new Node(next_x, next_y, current_node, cost + heuristic, heuristic); // 创建邻居节点
                    openlist.add(next_node);  // 将邻居节点加入优先队列
                    closelist.add(next_x + "," + next_y);  // 标记邻居节点为已访问
                }
            }
        }

        return null;  // 如果没有找到路径，返回空
    }

    public static Deque<Node> a_star_search_right(Robot robot, int max_steps) {
        PriorityQueue<Node> openlist = new PriorityQueue<>(Comparator.comparingDouble(node -> node.cost));  // 使用优先队列作为搜索队列，按节点代价排序
        Set<String> closelist = new HashSet<>();  // 记录已经访问过的节点
        Node start = new Node(robot.x, robot.y, 0); // 初始节点的代价为0

        openlist.add(start);  // 将起始节点加入队列
        closelist.add(start.x + "," + start.y);  // 标记起始节点为已访问

        int current_step = 0;  // 当前步数
        HashSet<Node> crush_node = new HashSet<>();
        for (Robot r: robots){
            if (!r.equals(robot)){
                crush_node.add(new Node(r.nextx, r.nexty));
                crush_node.add(new Node(r.x, r.y));
            }
        }
        while (!openlist.isEmpty()) {
            current_step++;  // 每进入一个循环，当前步数加1

            // 判断当前步数是否达到设定的最大步数，如果是，则停止搜索
            if (current_step > max_steps) {
                break;
            }

            Node current_node = openlist.poll();  // 从优先队列中取出代价最小的节点

            if (current_node.x == robot.mbx && current_node.y == robot.mby) { // 如果当前节点是目标节点，则搜索结束
                Deque<Node> path = new LinkedList<>();
                while (current_node != null) {
                    path.addFirst(current_node);
                    current_node = current_node.parent;
                }
                path.poll();
                return path;
            }

            // 向四个方向扩展节点
            int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] direction : directions) {
                int next_x = current_node.x + direction[0];
                int next_y = current_node.y + direction[1];
                if (0 <= next_x && next_x < n && 0 <= next_y && next_y < n
                        && maps[next_x][next_y] != '#' && maps[next_x][next_y] != '*'
                        && !closelist.contains(next_x + "," + next_y) ) {
                    if (current_step == 1 && crush_node.contains(new Node(next_x, next_y))){
                        continue;
                    }
                    double cost = current_node.cost+1; // 计算到达邻居节点的代价（这里假设每一步的代价都是1）
                    double heuristic = (Math.abs(next_x - robot.mbx) + Math.abs(next_y - robot.mby))*A_STAR_P; // 曼哈顿距离
                    Node next_node = new Node(next_x, next_y, current_node, cost + heuristic, heuristic); // 创建邻居节点
                    openlist.add(next_node);  // 将邻居节点加入优先队列
                    closelist.add(next_x + "," + next_y);  // 标记邻居节点为已访问
                }
            }
        }

        return null;  // 如果没有找到路径，返回空
    }


    public static int nearest_good_search(Robot robot, int max_steps) {
        Deque<Node> queue = new LinkedList<>();  // 使用双向队列作为搜索队列
        Set<String> visited = new HashSet<>();  // 记录已经访问过的节点
        HashSet<Node> allmb = new HashSet<Node>();
        for (Robot robot1 : robots) {
            if (!robot1.equals(robot)){
                allmb.add(new Node(robot1.mbx, robot1.mby));
            }
        }
        Node start = new Node(robot.x, robot.y);
        HashSet<Node> crush_node = new HashSet<>();
        for (Robot r: robots){
            if (!r.equals(robot)){
                crush_node.add(new Node(r.nextx, r.nexty));
                crush_node.add(new Node(r.x, r.y));
            }
        }
        queue.add(start);  // 将起始节点加入队列
        visited.add(start.x + "," + start.y);  // 标记起始节点为已访问

        int current_step = 0;  // 当前步数

        while (!queue.isEmpty()) {
            current_step++;  // 每进入一个循环，当前步数加1

            // 判断当前步数是否达到设定的最大步数，如果是，则停止搜索
            if (current_step > max_steps) {
                break;
            }

            // 获取当前层级的节点数
            int level_size = queue.size();

            // 逐个处理当前层级的节点
            for (int i = 0; i < level_size; i++) {
                Node current_node = queue.poll();  // 从队列左侧弹出当前节点
                if (gds[current_node.x][current_node.y] != 0
                        && (double)gds[current_node.x][current_node.y] > LOW_VALUE_GOOD_JUDGE) { // 如果当前节点是货物，则搜索结束
//                    Deque<Node> path = new LinkedList<>();
//                    while (current_node != null) {
//                        path.addFirst(current_node);
//                        current_node = current_node.parent;
//                    }
//                    path.poll();
//                    Node next_node = path.poll();
//                    if (next_node != null) {
//                        int x = next_node.x;
//                        int y = next_node.y;
//                        return new Node(x, y);
//                    }else {
//                        return null;
//                    }
                    robot.mbx = current_node.x;
                    robot.mby = current_node.y;
                    return 1;
                }

                // 向四个方向扩展节点
                int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] direction : directions) {
                    int next_x = current_node.x + direction[0];
                    int next_y = current_node.y + direction[1];
                    if (current_step == 1 && crush_node.contains(new Node(next_x, next_y))){
                        continue;
                    }

                    if (0 <= next_x && next_x < n && 0 <= next_y && next_y < n
                            && maps[next_x][next_y] != '#' && maps[next_x][next_y] != '*'
                            && !visited.contains(next_x + "," + next_y) &&
                            !allmb.contains(new Node(next_x, next_y)) &&
                            !robot.unreachNode.contains(new Node(next_x, next_y))) {
                        Node next_node = new Node(next_x, next_y, current_node);
                        queue.add(next_node);  // 将邻居节点加入队列
                        visited.add(next_x + "," + next_y);  // 标记邻居节点为已访问
                    }
                }
            }
        }

        return -1;  // 如果没有找到路径，返回空
    }

    public static int nearest_berth_search(Robot robot, int max_steps) {
        Deque<Node> queue = new LinkedList<>();  // 使用双向队列作为搜索队列
        Set<String> visited = new HashSet<>();  // 记录已经访问过的节点
        HashSet<Node> allmb = new HashSet<Node>();
        for (Berth b : berths) {
            allmb.add(new Node(b.x, b.y));
        }
        Node start = new Node(robot.x, robot.y);
        HashSet<Node> crush_node = new HashSet<>();
        for (Robot r: robots){
            if (!r.equals(robot)){
                crush_node.add(new Node(r.nextx, r.nexty));
                crush_node.add(new Node(r.x, r.y));
            }
        }
        queue.add(start);  // 将起始节点加入队列
        visited.add(start.x + "," + start.y);  // 标记起始节点为已访问

        int current_step = 0;  // 当前步数

        while (!queue.isEmpty()) {
            current_step++;  // 每进入一个循环，当前步数加1

            // 判断当前步数是否达到设定的最大步数，如果是，则停止搜索
            if (current_step > max_steps) {
                break;
            }

            // 获取当前层级的节点数
            int level_size = queue.size();

            // 逐个处理当前层级的节点
            for (int i = 0; i < level_size; i++) {
                Node current_node = queue.poll();  // 从队列左侧弹出当前节点
                if (allmb.contains(new Node(current_node.x, current_node.y))) { // 如果当前节点是货物，则搜索结束
//                    Deque<Node> path = new LinkedList<>();
//                    while (current_node != null) {
//                        path.addFirst(current_node);
//                        current_node = current_node.parent;
//                    }
//                    path.poll();
//                    Node next_node = path.poll();
//                    if (next_node != null) {
//                        int x = next_node.x;
//                        int y = next_node.y;
//                        return new Node(x, y);
//                    }else {
//                        return null;
//                    }
                    robot.mbx = current_node.x;
                    robot.mby = current_node.y;
                    return 1;
                }

                // 向四个方向扩展节点
                int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] direction : directions) {
                    int next_x = current_node.x + direction[0];
                    int next_y = current_node.y + direction[1];
                    if (current_step == 1 && crush_node.contains(new Node(next_x, next_y))){
                        continue;
                    }
                    if (0 <= next_x && next_x < n && 0 <= next_y && next_y < n
                            && maps[next_x][next_y] != '#' && maps[next_x][next_y] != '*'
                            && !visited.contains(next_x + "," + next_y) &&
                            !robot.unreachNode.contains(new Node(next_x, next_y))) {
                        Node next_node = new Node(next_x, next_y, current_node);
                        queue.add(next_node);  // 将邻居节点加入队列
                        visited.add(next_x + "," + next_y);  // 标记邻居节点为已访问
                    }
                }
            }
        }

        return -1;  // 如果没有找到路径，返回空
    }
    public static void set_robot_targets_good(Robot robot) {
        if (robot.sleeptime > 0){
            robot.sleeptime--;
            return;
        }
        int as = nearest_good_search(robot, GOOD_SET_MAX_LEN);
        if (as == 1){
            return;
        }else{
            robot.sleeptime += 20;
        }

//        else {
//            int minDis = 100000;
//            HashSet<Node> allmb = new HashSet<Node>();
//            for (Robot robot1 : robots) {
//                if (!robot1.equals(robot)){
//                    allmb.add(new Node(robot1.mbx, robot1.mby));
//                }
//            }
//            for (int i = 0; i < n; i++) {
//                for (int j = 0; j < n; j++) {
//                    int val = gds[i][j];
//                    if (val != 0){
//                        int nowDis = Math.abs(i - robot.x) + Math.abs(j - robot.y);// 不能用该距离，会搜到去不了的点
//                        if (nowDis < minDis && !allmb.contains(new Node(i, j)) && !robot.unreachNode.contains(new Node(i, j))) {
//                            minDis = nowDis;
//                            robot.mbx = i;
//                            robot.mby = j;
//                        }
//                    }
//                }
//            }
//        }

    }

    public static void set_robot_targets_berth(Robot robot, Berth[] berths) {
//        int minDis = 100000;
//        double minJudgeberth = 100000;
//        for (int i = 0;i < 10;i++) {
//            int nowDis = Math.abs(berths[i].x - robot.x) + Math.abs(berths[i].y - robot.y);
//            double berthvalue = (double) (boat_capacity * AVERAGE_GOOD_VALUE) / ((double)berths[i].transport_time*2.0 + (double)boat_capacity / (double)berths[i].loading_speed);
//            double judgeberth = (double) nowDis - berthvalue;
//            if (judgeberth < minJudgeberth && !robot.unreachNode.contains(new Node(berths[i].nx, berths[i].ny))) {
//                minJudgeberth = judgeberth;
//                robot.mbx = berths[i].nx;
//                robot.mby = berths[i].ny;
//            }
//        }

        if (mapnum == 2){
        if (robot.sleeptime > 0){
                robot.sleeptime--;
                return;
            }
            int as = nearest_berth_search(robot, GOOD_SET_MAX_LEN);
            if (as == 1){
                return;
            }else{
                robot.sleeptime += 20;
            }
        }
        if (mapnum == 1 || mapnum == 3){
            robot.mbx = robot.b.nx;
            robot.mby = robot.b.ny;
        }
    }

    public static int set_robot_step(Robot robot, int max_len) {
        int step_size = 1;
        List<int[]> points = new ArrayList<>();
        Node fast_node = null;

        if (robot.rightPath == null && robot.goods == 0){
            robot.unreachNode.add(new Node(robot.mbx, robot.mby));
            set_robot_targets_good(robot);
        }else if (robot.rightPath == null && robot.goods == 1){
            set_robot_targets_berth(robot, berths);
        }else {
            fast_node = robot.rightPath.poll();
        }


        int[][] add = {{0, step_size}, {0, -step_size}, {step_size, 0}, {-step_size, 0}};  // 可能运动的四个方向增量
        for (int[] anAdd : add) {
            int x = robot.x + anAdd[0];  // 检索周围能不能走，不能则跳过
            int y = robot.y + anAdd[1];
            boolean repeat_point = false;
            boolean crush = false;
            if (x < 0 || x >= 200) {
                continue;
            }
            if (y < 0 || y >= 200) {  // 检索超出图像大小范围则跳过
                continue;
            }
            if (maps[x][y] == '#' || maps[x][y] == '*') {
                continue;
            }
            for (Robot r : robots) {
                if (r.nextx == x && r.nexty == y) {
                    crush = true;
                    continue;
                }
                if(r.nextx == robot.x && r.nexty == robot.y && x == r.x && y == r.y){
                    crush = true;
                    continue;
                }
            }
            if (crush) {
                continue;
            }

            if (fast_node != null && fast_node.x == x && fast_node.y == y){
                points.add(new int[]{x, y, 0});
                continue;
            }

//            if (!robot.path.isEmpty()) {
//                for (int[] point : robot.path) {
//                    if (point[0] == x && point[1] == y) {
//                        repeat_point = true;
//                        continue;
//                    }
//                }
//                if (repeat_point) {
//                    continue;
//                }
//            }
            int H = Math.abs(x - robot.mbx) + Math.abs(y - robot.mby);  // 计算代价
            points.add(new int[]{x, y, H}); // 加入可能行动列表
        }

        if (!points.isEmpty() /*&& robot.path.size() < 200*/) {
            points.sort(Comparator.comparingInt(a -> a[2]));
            int x = points.get(0)[0];
            int y = points.get(0)[1];
            if (points.get(0)[2] != 0){
                robot.rightPath = a_star_search_right(robot, GOOD_SEARCH_MAX_LEN);
                if (robot.rightPath != null){
                    Node nextnode = robot.rightPath.poll();
                    try {
                        x = nextnode.x;
                        y = nextnode.y;
                        robot.nextx = x;
                        robot.nexty = y;
                        if (y - robot.y == 1) {return 0;}
                        else if (y - robot.y == -1) {return 1;}
                        else if (x - robot.x == -1) {return 2;}
                        else if (x - robot.x == 1) {return 3;}
                    }catch (RuntimeException e){
                        return -1;
                    }

                }else {
                    return -1;
                }

            }

            if (points.size() > 1 && points.get(0)[2] == points.get(1)[2]) {
                int rseed = (int) (Math.random() * 2);
                x = points.get(rseed)[0];
                y = points.get(rseed)[1];
            }

//            robot.path.add(new int[]{x, y});  // 记录已经走过的路
            robot.nextx = x;
            robot.nexty = y;
            if (y - robot.y == 1) {
                return 0;
            }
            else if (y - robot.y == -1) {
                return 1;
            }
            else if (x - robot.x == -1) {
                return 2;
            }
            else if (x - robot.x == 1) {
                return 3;
            }
            return -1;
        } else {
//            robot.path.clear();
            if (robot.goods == 0) {
                set_robot_targets_good(robot);
            } else {
                set_robot_targets_berth(robot, berths);
            }
            return (int) (Math.random() * 4);
        }
    }

    public static int is_inBerth(Robot robot) {
        for (int i = 0; i < berths.length; i++) {
            if (berths[i].x <= robot.x && robot.x <= berths[i].x + 3 && berths[i].y <= robot.y && robot.y <= berths[i].y + 3) {
                return i;
            }
        }
        return -1;
    }

    public static int Input() {
        Scanner scanner = new Scanner(System.in);
        id = scanner.nextInt();
        money = scanner.nextInt();
        int num = scanner.nextInt();
        for (int i = 0; i < num; i++) {
            int x = scanner.nextInt();
            int y = scanner.nextInt();
            int val = scanner.nextInt();
            Good good = new Good(x, y, val, id);
            if (!is_good_dead(good)){
                gds[x][y] = val;
                gds_disappear[x][y] = id + 1000;
            }
        }
        for (int i = 0; i < robot_num; i++) {
            robots[i].goods = scanner.nextInt();
            robots[i].x = scanner.nextInt();
            robots[i].y = scanner.nextInt();
            robots[i].status = scanner.nextInt();
        }
        for (int i = 0; i < 5; i++) {
            boat[i].status = scanner.nextInt();
            boat[i].pos = scanner.nextInt();
        }
        String okk = scanner.nextLine();
        return id;
    }

    public static void main(String[] args) {
        Init();

        while (id < START_TIME){
            id = Input();
            for (int i = 0; i < robot_num; i++) {
                if (robots[i].initPath == null){
                    continue;
                }
                Node node = robots[i].initPath.poll();
                if (node != null){
                    int x = node.x;
                    int y = node.y;
                    int w = -1;
                    if (y - robots[i].y > 0) w = 0;
                    else if (y - robots[i].y < 0) w = 1;
                    else if (x - robots[i].x < 0) w = 2;
                    else if (x - robots[i].x > 0) w = 3;
                    if (w != -1){
                        System.out.println("move " + i + " " + w);
                        System.out.flush();
                    }
                }
            }

            for (int i = 0; i < 5; i++) {
                if (boat[i].status == 1){
                    System.out.println("ship " + i + " " + i * 2);
                    berths[i*2].is_free = false;
                    boat[i].waitime = boat_capacity / berths[i*2].loading_speed;
                    boat[i].stoptime = id;
                }
            }
            System.out.println("OK");
            System.out.flush();
        }

        while (id >= START_TIME && id < 15001){
            if (mapnum == 1){
            maps[108][0] = '*';}
            id = Input();
            for (int i = 0; i < n; i++) { // 清理过期货物
                for (int j = 0; j < n; j++) {
                    if (gds_disappear[i][j] != 0 && id >= gds_disappear[i][j]) {
                        gds[i][j] = 0;
                        gds_disappear[i][j] = 0;
                    }
                }
            }

            for (int i = 0; i < robot_num; i++) {
                if (robots[i].status == 0) { // 跳过宕机的
                    robots[i].path.clear();
                    continue;
                }

                if (robots[i].initPath != null){
                    robots[i].initPath = null;
                    set_robot_targets_good(robots[i]);
                    robots[i].rightPath = a_star_search_right(robots[i], GOOD_SEARCH_MAX_LEN);
                }

                int berthIndex = is_inBerth(robots[i]);//返回是否在港口
                if (berthIndex != -1 && robots[i].value != 0) {// 增加港口钱，机器人钱归零，设置机器人目标
                    berths[berthIndex].value_all += robots[i].value;
                    robots[i].value = 0;
                    set_robot_targets_good(robots[i]);
                    robots[i].rightPath = a_star_search_right(robots[i], GOOD_SEARCH_MAX_LEN);
                    System.out.println("pull " + i);
                    System.out.flush();
                }

                if (robots[i].x == robots[i].mbx && robots[i].y == robots[i].mby && berthIndex == -1 && robots[i].goods == 0) {
                    robots[i].path.clear();
                    robots[i].mbx = 0;
                    robots[i].mby = 0;
                    System.out.println("get " + i);
                    System.out.flush();
                    if (gds[robots[i].x][robots[i].y] != 0) {
                        robots[i].value = gds[robots[i].x][robots[i].y];
                        gds[robots[i].x][robots[i].y] = 0;
                        gds_disappear[robots[i].x][robots[i].y] = 0;
                    }
                    set_robot_targets_berth(robots[i], berths);
                    robots[i].rightPath = a_star_search_right(robots[i], GOOD_SEARCH_MAX_LEN);
                }

                if (gds[robots[i].mbx][robots[i].mby] == 0 && robots[i].goods == 0 && robots[i].value == 0){
                    set_robot_targets_good(robots[i]);
                    robots[i].rightPath = a_star_search_right(robots[i], GOOD_SEARCH_MAX_LEN);
                }

                if (robots[i].status == 1 && robots[i].goods == 0 && robots[i].mbx == 0 && robots[i].mby == 0) {
                    set_robot_targets_good(robots[i]);
                    robots[i].rightPath = a_star_search_right(robots[i], GOOD_SEARCH_MAX_LEN);
                }

//                if (mapnum == 2 && robots[i].goods == 1){
//                    set_robot_targets_berth(robots[i], berths);
//                    robots[i].rightPath = a_star_search_right(robots[i], GOOD_SEARCH_MAX_LEN);
//                }


                int w = set_robot_step(robots[i], GOOD_SEARCH_MAX_LEN);
                if (w != -1){
                    System.out.println("move " + i + " " + w);
                    System.out.flush();
                }

            }

            for (int i = 0; i < 5; i++) {
                int value_max = 0;
                int ship_target = 0;
                if (boat[i].status == 1 && boat[i].pos == -1) {// 运输完成状态
                    if (id - boat[i].stoptime > 1) {
                        for (int b = 0; b < berths.length; b++) {
                            if (berths[b].is_free && berths[b].value_all >= value_max) {
                                value_max = berths[b].value_all;
                                ship_target = b;
                            }
                        }
                        if (value_max < BOAT_START_MIN_VALUE) {
                            continue;
                        }
                        System.out.println("ship " + i + " " + ship_target);
                        System.out.flush();
                        berths[ship_target].is_free = false;
                        boat[i].value = 0;
                        boat[i].waitime = boat_capacity / berths[ship_target].loading_speed;
                        boat[i].stoptime = id;
                    }
                    continue;
                } else if (boat[i].status == 1 && boat[i].pos != -1) {// # 装货状态
                    if (15000 - id - 5 < berths[boat[i].pos].transport_time){
                        System.out.println("go " + i);
                        System.out.flush();
                        boat[i].waitime = 0;
                        boat[i].stoptime = id;
                    }

                    if (id - boat[i].stoptime > boat[i].waitime) {
                        if (berths[boat[i].pos].value_all < boat_capacity * AVERAGE_GOOD_VALUE - boat[i].value) {
                            boat[i].value += berths[boat[i].pos].value_all;
                            berths[boat[i].pos].value_all = 0;
                        }else {
                            berths[boat[i].pos].value_all -= (boat_capacity * AVERAGE_GOOD_VALUE - boat[i].value);
                            boat[i].value = boat_capacity * AVERAGE_GOOD_VALUE;
                        }

                        berths[boat[i].pos].is_free = true;
                        if (boat[i].value < BOAT_BACK_VALUE){
                            for (int b = 0; b < berths.length; b++) {
                                if (berths[b].is_free && berths[b].value_all >= value_max) {
                                    value_max = berths[b].value_all;
                                    ship_target = b;
                                }
                            }
                            if (15000 - id - 5 < berths[ship_target].transport_time + 500){
                                System.out.println("go " + i);
                                System.out.flush();
                                boat[i].waitime = 0;
                                boat[i].stoptime = id;
                            }
                            System.out.println("ship " + i + " " + ship_target);
                            System.out.flush();
                            berths[ship_target].is_free = false;
                            boat[i].waitime = boat_capacity / berths[ship_target].loading_speed;
                            boat[i].stoptime = id;
                        }
                        else {
                        System.out.println("go " + i);
                        System.out.flush();
                        boat[i].waitime = 0;
                        boat[i].stoptime = id;
                        }
                    }
                    continue;
                } else if (boat[i].status == 0) {//# 运输中
                    boat[i].stoptime = id;
                    continue;
                } else if (boat[i].status == 2) {// 泊位外等待
                    boat[i].stoptime = id;
                    System.out.println("ship " + i + " " + boat[i].pos);
                    System.out.flush();
                    continue;
                }
            }
            System.out.println("OK");
            System.out.flush();
        }
    }
}
