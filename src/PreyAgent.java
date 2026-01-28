import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import java.util.List;

public class PreyAgent extends Agent {
    private Position position;
    private int energy;
    private int age;
    private Environment environment;
    
    // GENETICS
    private double mySpeed;
    private double myVision;

    // STAMINA SYSTEM
    private int stamina = 100;
    private static final int MAX_STAMINA = 100;

    // Reference to shared parameters (will be updated dynamically)
    private static final int AGE_MAX = 1500;
    private static final double FOOD_SEARCH_RADIUS = 120.0;
    private static final double FOOD_EAT_DISTANCE = 20.0;
    private static final int REPRO_COOLDOWN = 300;

    protected void setup() {
        environment = Environment.getInstance();
        Object[] args = getArguments();

        // Default Genetics
        mySpeed = VisualizerAgent.SimParams.PREY_SPEED;
        myVision = 100.0;

        if (args != null && args.length >= 2) {
            position = new Position((Double) args[0], (Double) args[1]);
            
            // Inherit genetics if provided
            if (args.length >= 4) {
                mySpeed = (Double) args[2];
                myVision = (Double) args[3];
            }
        } else {
            position = new Position(
                    Math.random() * environment.getWidth(),
                    Math.random() * environment.getHeight()
            );
        }

        energy = VisualizerAgent.SimParams.PREY_ENERGY_START;
        age = 0;
        environment.registerAgent(getAID(), "PREY", position, energy, mySpeed, myVision);

        addBehaviour(new PreyBehaviour());
    }

    protected void takeDown() {
        environment.unregisterAgent(getAID());
    }

    private class PreyBehaviour extends CyclicBehaviour {
        private int reproductionCooldown = 0;

        public void action() {
            // Check for DIE message
            jade.lang.acl.ACLMessage msg = receive();
            if (msg != null && "DIE".equals(msg.getContent())) {
                myAgent.doDelete();
                return;
            }

            // Age & Energy
            age++;

            // Lose energy every 3 cycles
            if (age % 3 == 0) {
                energy -= 1; 
                // Faster agents burn more energy!
                if (mySpeed > VisualizerAgent.SimParams.PREY_SPEED * 1.2) {
                    energy -= 1; // Extra cost for high speed
                }
            }

            reproductionCooldown--;

            // Death conditions
            if (energy <= 0 || age > AGE_MAX) {
                myAgent.doDelete();
                return;
            }

            // Perception - Use GENETIC vision
            List<AgentInfo> nearby = environment.getNearbyAgents(getAID(), position, myVision);
            List<AgentInfo> predators = nearby.stream().filter(AgentInfo::isPredator).toList();
            List<AgentInfo> nearbyPrey = nearby.stream().filter(AgentInfo::isPrey).toList();

            // BEHAVIOR
            if (!predators.isEmpty()) {
                // FLEE from predators (priority #1)
                flee(predators);
            } else {
                // Recover Stamina when safe
                if (stamina < MAX_STAMINA) stamina++;

                if (nearbyPrey.size() > 8) {
                    // TOO CROWDED - disperse! (priority #2)
                    disperseFromCrowd(nearbyPrey);
                } else {
                    // Look for food when safe
                    Food nearestFood = environment.findNearestFood(position, FOOD_SEARCH_RADIUS);

                    if (nearestFood != null) {
                        // Move towards food
                        double dist = position.distance(nearestFood.getPosition());

                        if (dist <= FOOD_EAT_DISTANCE) {
                            // EAT THE FOOD!
                            if (environment.consumeFood(nearestFood)) {
                                energy = Math.min(VisualizerAgent.SimParams.PREY_ENERGY_MAX,
                                        energy + nearestFood.getEnergyValue());
                            }
                        } else {
                            // Chase the food
                            double dx = nearestFood.getPosition().getX() - position.getX();
                            double dy = nearestFood.getPosition().getY() - position.getY();
                            double foodSpeed = (energy < 50) ? mySpeed * 1.5 : mySpeed;
                            position = position.moveTo(dx, dy, foodSpeed);
                        }
                    } else {
                        // No food nearby - minimal grazing
                        if (Math.random() < 0.10) { 
                            energy = Math.min(VisualizerAgent.SimParams.PREY_ENERGY_MAX, energy + 2);
                        }

                        // Random walk
                        position = position.randomMove(mySpeed * 0.7,
                                environment.getWidth(),
                                environment.getHeight());
                    }

                    // Try to reproduce
                    if (energy >= VisualizerAgent.SimParams.PREY_REPRO_THRESHOLD && reproductionCooldown <= 0) {
                        if (Math.random() < 0.20) {
                            List<AgentInfo> partners = nearby.stream().filter(AgentInfo::isPrey).toList();
                            if (!partners.isEmpty() && partners.size() < 8) {
                                reproduce();
                            }
                        }
                    }
                }
            }

            // Keep in bounds
            position.setX(Math.max(20, Math.min(environment.getWidth() - 20, position.getX())));
            position.setY(Math.max(20, Math.min(environment.getHeight() - 20, position.getY())));

            environment.updatePosition(getAID(), position);

            try { Thread.sleep(30); } catch (Exception e) {}
        }

        private void flee(List<AgentInfo> predators) {
            double predX = 0, predY = 0;
            for (AgentInfo pred : predators) {
                predX += pred.getPosition().getX();
                predY += pred.getPosition().getY();
            }
            predX /= predators.size();
            predY /= predators.size();

            double fleeX = position.getX() - predX;
            double fleeY = position.getY() - predY;

            // STAMINA LOGIC
            double currentSpeed = mySpeed;
            if (stamina > 10) {
                currentSpeed = mySpeed * 1.5; // Sprint!
                stamina -= 2; // Burn stamina
            } else {
                // Exhausted - can't sprint
                currentSpeed = mySpeed * 0.8; // Slower when tired
            }

            position = position.moveTo(fleeX, fleeY, currentSpeed);
        }

        private void reproduce() {
            energy -= VisualizerAgent.SimParams.PREY_REPRO_COST;
            reproductionCooldown = REPRO_COOLDOWN;

            try {
                // MUTATION: +/- 10%
                double childSpeed = mySpeed * (0.90 + Math.random() * 0.20);
                double childVision = myVision * (0.90 + Math.random() * 0.20);
                
                // Clamp values
                childSpeed = Math.max(1.0, Math.min(5.0, childSpeed));
                childVision = Math.max(50, Math.min(200, childVision));

                Object[] args = new Object[]{
                        position.getX() + (Math.random() - 0.5) * 40,
                        position.getY() + (Math.random() - 0.5) * 40,
                        childSpeed,
                        childVision
                };
                String name = "Prey_" + System.nanoTime();
                getContainerController().createNewAgent(name, "PreyAgent", args).start();
            } catch (Exception e) {}
        }

        private void disperseFromCrowd(List<AgentInfo> nearbyAgents) {
            double avgX = 0, avgY = 0;
            for (AgentInfo other : nearbyAgents) {
                avgX += other.getPosition().getX();
                avgY += other.getPosition().getY();
            }
            avgX /= nearbyAgents.size();
            avgY /= nearbyAgents.size();

            double disperseX = position.getX() - avgX;
            double disperseY = position.getY() - avgY;

            disperseX += (Math.random() - 0.5) * 100;
            disperseY += (Math.random() - 0.5) * 100;

            position = new Position(
                    position.getX() + disperseX * 0.05,
                    position.getY() + disperseY * 0.05
            );
        }
    }
}