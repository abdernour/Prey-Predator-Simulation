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

    // MOVEMENT PERSISTENCE
    private double wanderAngle = Math.random() * 2 * Math.PI;

    // STAMINA SYSTEM
    private int stamina = 100;
    private static final int MAX_STAMINA = 100;

    // FLOCKING PARAMETERS
    private static final double SEPARATION_WEIGHT = 1.5;
    private static final double ALIGNMENT_WEIGHT = 1.0;
    private static final double COHESION_WEIGHT = 1.0;
    private static final double FLOCKING_RADIUS = 60.0;

    // Reference to shared parameters
    private static final int AGE_MAX = 1500;
    private static final double FOOD_SEARCH_RADIUS = 120.0;
    private static final double FOOD_EAT_DISTANCE = 20.0;
    private static final int REPRO_COOLDOWN = 300;

    protected void setup() {
        environment = Environment.getInstance();
        Object[] args = getArguments();

        // Default Genetics
        mySpeed = VisualizerAgent.SimParams.PREY_SPEED;
        myVision = 70.0;

        if (args != null && args.length >= 2) {
            position = new Position((Double) args[0], (Double) args[1]);
            
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

            age++;

            // Lose energy every 3 cycles
            if (age % 3 == 0) {
                energy -= 1; 
                if (mySpeed > VisualizerAgent.SimParams.PREY_SPEED * 1.2) {
                    energy -= 1;
                }
            }

            reproductionCooldown--;

            if (energy <= 0 || age > AGE_MAX) {
                myAgent.doDelete();
                return;
            }

            // TERRAIN CHECKS
            boolean inSwamp = environment.isInSwamp(position);

            // Perception
            List<AgentInfo> nearby = environment.getNearbyAgents(getAID(), position, myVision);
            List<AgentInfo> predators = nearby.stream().filter(AgentInfo::isPredator).toList();
            List<AgentInfo> nearbyPrey = nearby.stream().filter(AgentInfo::isPrey).toList();

            // BEHAVIOR
            if (!predators.isEmpty()) {
                flee(predators, inSwamp);
            } else {
                // Recover Stamina
                if (stamina < MAX_STAMINA) stamina++;

                Food nearestFood = environment.findNearestFood(position, FOOD_SEARCH_RADIUS);

                if (nearestFood != null) {
                    // FOOD PRIORITY
                    double dist = position.distance(nearestFood.getPosition());

                    if (dist <= FOOD_EAT_DISTANCE) {
                        if (environment.consumeFood(nearestFood)) {
                            energy = Math.min(VisualizerAgent.SimParams.PREY_ENERGY_MAX,
                                    energy + nearestFood.getEnergyValue());
                        }
                    } else {
                        double dx = nearestFood.getPosition().getX() - position.getX();
                        double dy = nearestFood.getPosition().getY() - position.getY();
                        
                        double speed = (energy < 50) ? mySpeed * 1.5 : mySpeed;
                        if (inSwamp) speed *= 0.5;
                        
                        position = position.moveTo(dx, dy, speed);
                        wanderAngle = Math.atan2(dy, dx);
                    }
                } else {
                    // FLOCKING BEHAVIOR
                    if (nearbyPrey.size() > 0) {
                        applyFlocking(nearbyPrey, inSwamp);
                    } else {
                        // Wander
                        wander(inSwamp);
                    }
                }

                // Try to reproduce
                if (energy >= VisualizerAgent.SimParams.PREY_REPRO_THRESHOLD && reproductionCooldown <= 0) {
                    if (Math.random() < 0.20) {
                        if (!nearbyPrey.isEmpty() && nearbyPrey.size() < 15) { // Allow larger herds
                            reproduce();
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

        private void applyFlocking(List<AgentInfo> flock, boolean inSwamp) {
            double sepX = 0, sepY = 0;
            double alignX = 0, alignY = 0;
            
            double cohX = 0, cohY = 0;
            int count = 0;

            for (AgentInfo other : flock) {
                double d = position.distance(other.getPosition());
                if (d > 0 && d < FLOCKING_RADIUS) {
                    // Separation: Move away from neighbors who are too close
                    if (d < 25.0) {
                        double pushX = position.getX() - other.getPosition().getX();
                        double pushY = position.getY() - other.getPosition().getY();
                        sepX += pushX / d; // Weight by distance
                        sepY += pushY / d;
                    }

                    // Cohesion: Move towards center of mass
                    cohX += other.getPosition().getX();
                    cohY += other.getPosition().getY();
                    
                    count++;
                }
            }

            if (count > 0) {
                // Finish Cohesion calculation
                cohX /= count;
                cohY /= count;
                // Vector towards center
                cohX = (cohX - position.getX()) / 100.0; // Move 1% towards center
                cohY = (cohY - position.getY()) / 100.0;
            }

            // Combine forces
            double moveX = (sepX * SEPARATION_WEIGHT) + (cohX * COHESION_WEIGHT);
            double moveY = (sepY * SEPARATION_WEIGHT) + (cohY * COHESION_WEIGHT);

            // Add a bit of wander so the flock keeps moving
            wanderAngle += (Math.random() - 0.5) * 0.2;
            moveX += Math.cos(wanderAngle) * 0.5;
            moveY += Math.sin(wanderAngle) * 0.5;

            // Apply movement
            double speed = mySpeed * 0.8; // Cruising speed
            if (inSwamp) speed *= 0.5;

            // Normalize and apply speed
            double dist = Math.sqrt(moveX * moveX + moveY * moveY);
            if (dist > 0) {
                moveX /= dist;
                moveY /= dist;
                position = new Position(
                    position.getX() + moveX * speed,
                    position.getY() + moveY * speed
                );
                // Update wander angle to match flock direction
                wanderAngle = Math.atan2(moveY, moveX);
            }
            
            checkBoundsBounce();
        }

        private void wander(boolean inSwamp) {
            wanderAngle += (Math.random() - 0.5) * 0.15;
            double dx = Math.cos(wanderAngle);
            double dy = Math.sin(wanderAngle);
            
            double speed = mySpeed * 0.8;
            if (inSwamp) speed *= 0.5;
            
            position = new Position(
                    position.getX() + dx * speed,
                    position.getY() + dy * speed
            );
            checkBoundsBounce();
        }

        private void checkBoundsBounce() {
            if (position.getX() <= 20 || position.getX() >= environment.getWidth() - 20) {
                wanderAngle = Math.PI - wanderAngle;
            }
            if (position.getY() <= 20 || position.getY() >= environment.getHeight() - 20) {
                wanderAngle = -wanderAngle;
            }
        }

        private void flee(List<AgentInfo> predators, boolean inSwamp) {
            double predX = 0, predY = 0;
            for (AgentInfo pred : predators) {
                predX += pred.getPosition().getX();
                predY += pred.getPosition().getY();
            }
            predX /= predators.size();
            predY /= predators.size();

            double fleeX = position.getX() - predX;
            double fleeY = position.getY() - predY;
            
            wanderAngle = Math.atan2(fleeY, fleeX);

            double currentSpeed = mySpeed;
            if (stamina > 5) { 
                currentSpeed = mySpeed * 1.4;
                stamina -= 2; 
            } else {
                currentSpeed = mySpeed * 0.9;
            }
            
            if (inSwamp) currentSpeed *= 0.5;

            position = position.moveTo(fleeX, fleeY, currentSpeed);
        }

        private void reproduce() {
            energy -= VisualizerAgent.SimParams.PREY_REPRO_COST;
            reproductionCooldown = REPRO_COOLDOWN;

            try {
                double childSpeed = mySpeed * (0.90 + Math.random() * 0.20);
                double childVision = myVision * (0.90 + Math.random() * 0.20);
                
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

        private void disperseFromCrowd(List<AgentInfo> nearbyAgents, boolean inSwamp) {
            // Handle extreme overcrowding
             double avgX = 0, avgY = 0;
            for (AgentInfo other : nearbyAgents) {
                avgX += other.getPosition().getX();
                avgY += other.getPosition().getY();
            }
            avgX /= nearbyAgents.size();
            avgY /= nearbyAgents.size();

            double disperseX = position.getX() - avgX;
            double disperseY = position.getY() - avgY;
            
            wanderAngle = Math.atan2(disperseY, disperseX);

            disperseX += (Math.random() - 0.5) * 100;
            disperseY += (Math.random() - 0.5) * 100;

            double speed = 0.05;
            if (inSwamp) speed *= 0.5;

            position = new Position(
                    position.getX() + disperseX * speed,
                    position.getY() + disperseY * speed
            );
        }
    }
}