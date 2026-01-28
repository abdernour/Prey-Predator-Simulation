import jade.core.AID;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class Environment {
    private static Environment instance;
    private int width = 800;
    private int height = 600;
    
    // Spatial Partitioning: Grid-based optimization
    private static final int GRID_CELL_SIZE = 100;
    private Map<String, List<AgentInfo>> spatialGrid;
    
    private Map<AID, AgentInfo> agents;
    private List<Food> foods;  // FOOD SYSTEM

    private static final double COLLISION_DISTANCE = 10.0;
    private static final int FOOD_ENERGY = 35;

    private Environment() {
        agents = new ConcurrentHashMap<>();
        foods = new CopyOnWriteArrayList<>();
        spatialGrid = new ConcurrentHashMap<>();
    }

    public static synchronized Environment getInstance() {
        if (instance == null) {
            instance = new Environment();
        }
        return instance;
    }

    // Helper to get grid key
    private String getGridKey(Position pos) {
        int x = (int) (pos.getX() / GRID_CELL_SIZE);
        int y = (int) (pos.getY() / GRID_CELL_SIZE);
        return x + "," + y;
    }

    public synchronized void registerAgent(AID aid, String type, Position position, int energy, double speed, double visionRange) {
        AgentInfo info = new AgentInfo(aid, type, position, energy, speed, visionRange);
        agents.put(aid, info);
        
        // Add to grid
        String key = getGridKey(position);
        spatialGrid.computeIfAbsent(key, k -> new ArrayList<>()).add(info);
        
        System.out.println("✓ Registered: " + info);
    }

    public synchronized void unregisterAgent(AID aid) {
        AgentInfo removed = agents.remove(aid);
        if (removed != null) {
            // Remove from grid
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
            Position oldPos = info.getPosition();
            String oldKey = getGridKey(oldPos);
            
            // Keep within bounds
            double x = Math.max(0, Math.min(width, newPosition.getX()));
            double y = Math.max(0, Math.min(height, newPosition.getY()));
            Position clampedPos = new Position(x, y);
            
            info.setPosition(clampedPos);
            
            String newKey = getGridKey(clampedPos);
            
            // Update grid if cell changed
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

        // Check neighboring cells
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
        // Optimized collision check using grid
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
        foods.add(new Food(position, FOOD_ENERGY));
    }

    public synchronized Food findNearestFood(Position position, double radius) {
        Food nearest = null;
        double minDist = radius;

        // Optimization: Could also grid food, but list is usually smaller than agents
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