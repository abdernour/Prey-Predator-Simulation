import jade.core.AID;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.Polygon;

public class Environment {
    private static Environment instance;
    private int width = 800;
    private int height = 600;
    
    // Spatial Partitioning
    private static final int GRID_CELL_SIZE = 100;
    private Map<String, List<AgentInfo>> spatialGrid;
    
    private Map<AID, AgentInfo> agents;
    private List<Food> foods;

    // terrain clusters
    private List<Shape> forestTrees;
    private List<Shape> swamps;
    private List<Shape> rocks;

    // seasons
    public enum Season { SPRING, SUMMER, AUTUMN, WINTER }
    private Season currentSeason = Season.SPRING;
    private int seasonTick = 0;
    private static final int SEASON_DURATION = 300;

    // death statistics
    public static class DeathStats {
        public int preyHunted = 0;
        public int preyStarved = 0;
        public int preyOldAge = 0;
        public int predStarved = 0;
        
        public void reset() {
            preyHunted = 0;
            preyStarved = 0;
            preyOldAge = 0;
            predStarved = 0;
        }
    }
    private DeathStats stats = new DeathStats();

    private static final double COLLISION_DISTANCE = 10.0;
    private static final int FOOD_ENERGY = 35;

    private Environment() {
        agents = new ConcurrentHashMap<>();
        foods = new CopyOnWriteArrayList<>();
        spatialGrid = new ConcurrentHashMap<>();
        
        initTerrain();
    }

    private void initTerrain() {
        forestTrees = new ArrayList<>();
        swamps = new ArrayList<>();
        rocks = new ArrayList<>();
        Random rand = new Random();
        List<Position> featureCenters = new ArrayList<>();

        // generate organic swamps (smaller)
        int numSwamps = 3 + rand.nextInt(2);
        for (int i = 0; i < numSwamps; i++) {
            Position center = findValidPosition(featureCenters, 150, rand);
            if (center != null) {
                featureCenters.add(center);
                // Reduced radius from 60-100 to 40-70
                swamps.add(createOrganicBlob(center.getX(), center.getY(), 40, 70, 8, 13, rand));
            }
        }

        // generate natural rock obstacles
        int numRocks = 5 + rand.nextInt(3);
        for (int i = 0; i < numRocks; i++) {
            Position center = findValidPosition(featureCenters, 100, rand);
            if (center != null) {
                featureCenters.add(center);
                rocks.add(createPolygonRock(center.getX(), center.getY(), 30, 50, 5, 8, rand));
            }
        }

        // generate forest clusters (smaller)
        int numForests = 3 + rand.nextInt(2);
        for (int i = 0; i < numForests; i++) {
            Position center = findValidPosition(featureCenters, 150, rand);
            if (center != null) {
                featureCenters.add(center);
                // Reduced trees per cluster from 5-8 to 4-6
                int numTrees = 4 + rand.nextInt(3);
                for (int t = 0; t < numTrees; t++) {
                    double angle = rand.nextDouble() * Math.PI * 2;
                    // Reduced spread from 40 to 30
                    double dist = rand.nextDouble() * 30;
                    double tx = center.getX() + Math.cos(angle) * dist;
                    double ty = center.getY() + Math.sin(angle) * dist;

                    forestTrees.add(createOrganicTree(tx, ty, rand));
                }
            }
        }
    }

    private Position findValidPosition(List<Position> existing, double minDistance, Random rand) {
        for (int i = 0; i < 50; i++) {
            double x = 50 + rand.nextDouble() * (width - 100);
            double y = 50 + rand.nextDouble() * (height - 100);
            Position p = new Position(x, y);
            boolean valid = true;
            for (Position other : existing) {
                if (p.distance(other) < minDistance) {
                    valid = false;
                    break;
                }
            }
            if (valid) return p;
        }
        return null;
    }

    private Shape createOrganicBlob(double cx, double cy, double minR, double maxR, int minPts, int maxPts, Random rand) {
        GeneralPath path = new GeneralPath();
        int points = minPts + rand.nextInt(maxPts - minPts + 1);
        double angleStep = (Math.PI * 2) / points;
        for (int i = 0; i < points; i++) {
            double angle = i * angleStep;
            double r = minR + rand.nextDouble() * (maxR - minR);
            double x = cx + Math.cos(angle) * r;
            double y = cy + Math.sin(angle) * r;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.closePath();
        return path;
    }

    private Shape createPolygonRock(double cx, double cy, double minR, double maxR, int minSides, int maxSides, Random rand) {
        Polygon poly = new Polygon();
        int sides = minSides + rand.nextInt(maxSides - minSides + 1);
        double angleStep = (Math.PI * 2) / sides;
        for (int i = 0; i < sides; i++) {
            double angle = i * angleStep + (rand.nextDouble() - 0.5) * 0.5;
            double r = minR + rand.nextDouble() * (maxR - minR);
            int x = (int)(cx + Math.cos(angle) * r);
            int y = (int)(cy + Math.sin(angle) * r);
            poly.addPoint(x, y);
        }
        return poly;
    }

    private Shape createOrganicTree(double x, double y, Random rand) {
        Area tree = new Area();
        int blobs = 3 + rand.nextInt(3);
        for(int i=0; i<blobs; i++) {
            double r = 15 + rand.nextDouble() * 15;
            double ox = (rand.nextDouble() - 0.5) * 20;
            double oy = (rand.nextDouble() - 0.5) * 20;
            tree.add(new Area(new Ellipse2D.Double(x + ox - r, y + oy - r, r*2, r*2)));
        }
        return tree;
    }

    public static synchronized Environment getInstance() {
        if (instance == null) {
            instance = new Environment();
        }
        return instance;
    }

    // SEASONAL LOGIC
    public synchronized void updateSeason() {
        seasonTick++;
        if (seasonTick >= SEASON_DURATION) {
            seasonTick = 0;
            currentSeason = Season.values()[(currentSeason.ordinal() + 1) % Season.values().length];
            System.out.println("🍂 Season changed to: " + currentSeason);
        }
    }

    public Season getCurrentSeason() { return currentSeason; }

    // death tracking
    public synchronized void recordDeath(String type, String cause) {
        if (type.equals("PREY")) {
            switch (cause) {
                case "HUNTED": stats.preyHunted++; break;
                case "STARVED": stats.preyStarved++; break;
                case "OLD_AGE": stats.preyOldAge++; break;
            }
        } else if (type.equals("PREDATOR")) {
            if (cause.equals("STARVED")) stats.predStarved++;
        }
    }
    
    public synchronized DeathStats getStats() { return stats; }
    public synchronized void resetStats() { stats.reset(); }

    // TERRAIN CHECKS
    public boolean isInForest(Position pos) {
        for (Shape tree : forestTrees) {
            if (tree.contains(pos.getX(), pos.getY())) return true;
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

    public List<Shape> getTrees() { return forestTrees; }
    public List<Shape> getSwamps() { return swamps; }
    public List<Shape> getRocks() { return rocks; }

    // Helper to get grid key
    private String getGridKey(Position pos) {
        int x = (int) (pos.getX() / GRID_CELL_SIZE);
        int y = (int) (pos.getY() / GRID_CELL_SIZE);
        return x + "," + y;
    }

    public synchronized void registerAgent(AID aid, String type, Position position, int energy, double speed, double visionRange) {
        int attempts = 0;
        while(isObstacle(position.getX(), position.getY()) && attempts < 10) {
            position.setX(Math.random() * width);
            position.setY(Math.random() * height);
            attempts++;
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

    public synchronized void updatePosition(AID aid, Position newPosition, int energy) {
        AgentInfo info = agents.get(aid);
        if (info != null) {
            if (isObstacle(newPosition.getX(), newPosition.getY())) return; 
            info.setEnergy(energy);
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
            if (info.isPrey()) return info;
        }
        return null;
    }

    public synchronized List<AgentInfo> getAllPrey() {
        return agents.values().stream().filter(AgentInfo::isPrey).collect(Collectors.toList());
    }

    public synchronized List<AgentInfo> getAllPredators() {
        return agents.values().stream().filter(AgentInfo::isPredator).collect(Collectors.toList());
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