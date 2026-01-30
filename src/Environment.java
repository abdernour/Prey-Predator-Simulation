import jade.core.AID;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

public class Environment {
    private static Environment instance;
    private int width = 800;
    private int height = 600;

    // Spatial Partitioning
    private static final int GRID_CELL_SIZE = 100;
    private Map<String, List<AgentInfo>> spatialGrid;

    private Map<AID, AgentInfo> agents;
    private List<Food> foods;

    // TERRAIN
    private List<Shape> forests;    // Forest zones (clusters of circles)
    private List<Shape> swamps;     // Swamp zones (irregular ovals)
    private List<Shape> rocks;      // Rock obstacles (varied shapes)

    private static final double COLLISION_DISTANCE = 10.0;
    private static final int FOOD_ENERGY = 35;

    private Environment() {
        agents = new ConcurrentHashMap<>();
        foods = new CopyOnWriteArrayList<>();
        spatialGrid = new ConcurrentHashMap<>();

        initTerrain();
    }

    private void initTerrain() {
        forests = new ArrayList<>();
        swamps = new ArrayList<>();
        rocks = new ArrayList<>();
        Random rand = new Random();

        // Track occupied center points to avoid overlap
        List<Point2D> occupied = new ArrayList<>();

        // 1. FORESTS
        int numForests = 3 + rand.nextInt(2);
        for (int f = 0; f < numForests; f++) {
            // Pick a forest center
            double centerX = 100 + rand.nextDouble() * (width - 200);
            double centerY = 100 + rand.nextDouble() * (height - 200);

            // Check if too close to existing features
            boolean validSpot = true;
            for (Point2D p : occupied) {
                if (Math.hypot(centerX - p.x, centerY - p.y) < 150) {
                    validSpot = false;
                    break;
                }
            }

            if (!validSpot) continue;
            occupied.add(new Point2D(centerX, centerY));

            // Create a cluster of trees around this center (5-8 trees per cluster)
            int numTrees = 5 + rand.nextInt(4);
            for (int t = 0; t < numTrees; t++) {
                double angle = rand.nextDouble() * 2 * Math.PI;
                double distance = rand.nextDouble() * 40; // Trees cluster within 40px radius
                double x = centerX + Math.cos(angle) * distance;
                double y = centerY + Math.sin(angle) * distance;

                // Create irregular tree shape
                Path2D.Double tree = new Path2D.Double();
                double size = 15 + rand.nextDouble() * 15;
                tree.append(new Ellipse2D.Double(x - size/2, y - size/2, size, size), false);

                // Add 1-2 more circles to make it irregular
                for (int extra = 0; extra < 1 + rand.nextInt(2); extra++) {
                    double ox = x + (rand.nextDouble() - 0.5) * size * 0.7;
                    double oy = y + (rand.nextDouble() - 0.5) * size * 0.7;
                    double extraSize = size * (0.6 + rand.nextDouble() * 0.4);
                    tree.append(new Ellipse2D.Double(ox - extraSize/2, oy - extraSize/2, extraSize, extraSize), false);
                }

                forests.add(tree);
            }
        }

        // 2. SWAMPS
        int numSwamps = 3 + rand.nextInt(2);
        for (int s = 0; s < numSwamps; s++) {
            double x = 100 + rand.nextDouble() * (width - 200);
            double y = 100 + rand.nextDouble() * (height - 200);

            // Check spacing
            boolean validSpot = true;
            for (Point2D p : occupied) {
                if (Math.hypot(x - p.x, y - p.y) < 120) {
                    validSpot = false;
                    break;
                }
            }

            if (!validSpot) continue;
            occupied.add(new Point2D(x, y));

            // Create organic swamp shape
            Path2D.Double swamp = new Path2D.Double();
            int numPoints = 8 + rand.nextInt(5);
            double baseRadius = 30 + rand.nextDouble() * 20;

            for (int i = 0; i < numPoints; i++) {
                double angle = (2 * Math.PI * i) / numPoints;
                // Vary radius for each point to create organic shape
                double radius = baseRadius * (0.7 + rand.nextDouble() * 0.6);
                double px = x + Math.cos(angle) * radius;
                double py = y + Math.sin(angle) * radius * 0.7; // Make it more oval

                if (i == 0) {
                    swamp.moveTo(px, py);
                } else {
                    swamp.lineTo(px, py);
                }
            }
            swamp.closePath();

            swamps.add(swamp);
        }

        // 3. ROCKS
        int numRocks = 5 + rand.nextInt(3);
        for (int r = 0; r < numRocks; r++) {
            double x = 80 + rand.nextDouble() * (width - 160);
            double y = 80 + rand.nextDouble() * (height - 160);

            // Check spacing
            boolean validSpot = true;
            for (Point2D p : occupied) {
                if (Math.hypot(x - p.x, y - p.y) < 100) {
                    validSpot = false;
                    break;
                }
            }

            if (!validSpot) continue;
            occupied.add(new Point2D(x, y));

            // Create irregular rock shape
            Path2D.Double rock = new Path2D.Double();
            int numSides = 5 + rand.nextInt(3);
            double size = 20 + rand.nextDouble() * 15;

            for (int i = 0; i < numSides; i++) {
                double angle = (2 * Math.PI * i) / numSides + rand.nextDouble() * 0.3;
                double radius = size * (0.8 + rand.nextDouble() * 0.4);
                double px = x + Math.cos(angle) * radius;
                double py = y + Math.sin(angle) * radius;

                if (i == 0) {
                    rock.moveTo(px, py);
                } else {
                    rock.lineTo(px, py);
                }
            }
            rock.closePath();

            rocks.add(rock);
        }
    }

    public static synchronized Environment getInstance() {
        if (instance == null) {
            instance = new Environment();
        }
        return instance;
    }

    // TERRAIN CHECKS
    public boolean isInForest(Position pos) {
        for (Shape forest : forests) {
            if (forest.contains(pos.getX(), pos.getY())) return true;
        }
        return false;
    }

    public boolean isInSwamp(Position pos) {
        for (Shape swamp : swamps) {
            if (swamp.contains(pos.getX(), pos.getY())) return true;
        }
        return false;
    }

    public boolean isObstacle(double x, double y) {
        for (Shape rock : rocks) {
            if (rock.contains(x, y)) return true;
        }
        return false;
    }

    // Getters for rendering
    public List<Shape> getForests() { return forests; }
    public List<Shape> getSwamps() { return swamps; }
    public List<Shape> getRocks() { return rocks; }

    // Legacy getters for compatibility (return empty lists)
    public List<Rectangle2D.Double> getTrees() { return new ArrayList<>(); }
    public List<Ellipse2D.Double> getSwamps_Old() { return new ArrayList<>(); }
    public List<Rectangle2D.Double> getRocks_Old() { return new ArrayList<>(); }

    // Helper class for 2D points
    private static class Point2D {
        double x, y;
        Point2D(double x, double y) { this.x = x; this.y = y; }
    }

    // Helper to get grid key
    private String getGridKey(Position pos) {
        int x = (int) (pos.getX() / GRID_CELL_SIZE);
        int y = (int) (pos.getY() / GRID_CELL_SIZE);
        return x + "," + y;
    }

    public synchronized void registerAgent(AID aid, String type, Position position, int energy, double speed, double visionRange) {
        // Ensure we don't spawn inside obstacles
        while(isObstacle(position.getX(), position.getY())) {
            position.setX(Math.random() * width);
            position.setY(Math.random() * height);
        }

        AgentInfo info = new AgentInfo(aid, type, position, energy, speed, visionRange);
        agents.put(aid, info);

        String key = getGridKey(position);
        spatialGrid.computeIfAbsent(key, k -> new ArrayList<>()).add(info);

        System.out.println("✓ Registered: " + info);
    }

    public synchronized void unregisterAgent(AID aid) {
        AgentInfo removed = agents.remove(aid);
        if (removed != null) {
            String key = getGridKey(removed.getPosition());
            List<AgentInfo> cell = spatialGrid.get(key);
            if (cell != null) {
                cell.remove(removed);
                if (cell.isEmpty()) spatialGrid.remove(key);
            }
            System.out.println("✗ Removed: " + removed);
        }
    }

    public synchronized void updatePosition(AID aid, Position newPosition) {
        AgentInfo info = agents.get(aid);
        if (info != null) {
            // Check Obstacle Collision
            if (isObstacle(newPosition.getX(), newPosition.getY())) {
                // Hit a rock! Don't move.
                return;
            }

            Position oldPos = info.getPosition();
            String oldKey = getGridKey(oldPos);

            double x = Math.max(0, Math.min(width, newPosition.getX()));
            double y = Math.max(0, Math.min(height, newPosition.getY()));
            Position clampedPos = new Position(x, y);

            info.setPosition(clampedPos);

            String newKey = getGridKey(clampedPos);

            if (!oldKey.equals(newKey)) {
                List<AgentInfo> oldCell = spatialGrid.get(oldKey);
                if (oldCell != null) {
                    oldCell.remove(info);
                    if (oldCell.isEmpty()) spatialGrid.remove(oldKey);
                }
                spatialGrid.computeIfAbsent(newKey, k -> new ArrayList<>()).add(info);
            }
        }
    }

    public synchronized List<AgentInfo> getNearbyAgents(AID requester, Position position, double radius) {
        List<AgentInfo> nearby = new ArrayList<>();

        int cellX = (int) (position.getX() / GRID_CELL_SIZE);
        int cellY = (int) (position.getY() / GRID_CELL_SIZE);
        int searchRadius = (int) Math.ceil(radius / GRID_CELL_SIZE);

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                String key = (cellX + dx) + "," + (cellY + dy);
                List<AgentInfo> cellAgents = spatialGrid.get(key);

                if (cellAgents != null) {
                    for (AgentInfo info : cellAgents) {
                        if (!info.getAID().equals(requester)) {
                            if (info.getPosition().distance(position) <= radius) {
                                nearby.add(info);
                            }
                        }
                    }
                }
            }
        }
        return nearby;
    }

    public synchronized AgentInfo checkPreyCollision(Position predatorPos) {
        List<AgentInfo> nearby = getNearbyAgents(null, predatorPos, COLLISION_DISTANCE);
        for (AgentInfo info : nearby) {
            if (info.isPrey()) {
                return info;
            }
        }
        return null;
    }

    public synchronized List<AgentInfo> getAllPrey() {
        return agents.values().stream()
                .filter(AgentInfo::isPrey)
                .collect(Collectors.toList());
    }

    public synchronized List<AgentInfo> getAllPredators() {
        return agents.values().stream()
                .filter(AgentInfo::isPredator)
                .collect(Collectors.toList());
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public Map<AID, AgentInfo> getAllAgents() {
        return new HashMap<>(agents);
    }

    public int getPreyCount() {
        return (int) agents.values().stream().filter(AgentInfo::isPrey).count();
    }

    public int getPredatorCount() {
        return (int) agents.values().stream().filter(AgentInfo::isPredator).count();
    }

    // FOOD MANAGEMENT
    public synchronized void spawnFood(Position position) {
        // Don't spawn food inside rocks
        if (!isObstacle(position.getX(), position.getY())) {
            foods.add(new Food(position, FOOD_ENERGY));
        }
    }

    public synchronized Food findNearestFood(Position position, double radius) {
        Food nearest = null;
        double minDist = radius;

        for (Food food : foods) {
            if (!food.isConsumed()) {
                double dist = position.distance(food.getPosition());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = food;
                }
            }
        }
        return nearest;
    }

    public synchronized boolean consumeFood(Food food) {
        if (food != null && !food.isConsumed()) {
            food.consume();
            foods.remove(food);
            return true;
        }
        return false;
    }

    public synchronized List<Food> getAllFoods() {
        return new ArrayList<>(foods);
    }

    public synchronized int getFoodCount() {
        return foods.size();
    }
}