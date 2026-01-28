import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import java.util.List;

public class PredatorAgent extends Agent {
    private Position position;
    private int energy;
    private Environment environment;
    
    // GENETICS
    private double mySpeed;
    private double myVision;

    // BRAIN (State Machine)
    private enum State { SCOUTING, HUNTING, RESTING }
    private State currentState = State.SCOUTING;
    private int stamina = 100; // New resource!
    private static final int MAX_STAMINA = 100;

    // Static constants
    private static final int ENERGY_LOSS = 1;
    private static final double CATCH_DISTANCE = 25.0;
    private static final int REPRO_COOLDOWN = 800;
    private static final int EATING_COOLDOWN = 100;

    protected void setup() {
        environment = Environment.getInstance();
        Object[] args = getArguments();

        // Default Genetics
        mySpeed = VisualizerAgent.SimParams.PRED_SPEED;
        myVision = 110.0;

        if (args != null && args.length >= 2) {
            double x = (Double) args[0];
            double y = (Double) args[1];
            x = Math.max(50, Math.min(environment.getWidth() - 50, x));
            y = Math.max(50, Math.min(environment.getHeight() - 50, y));
            position = new Position(x, y);
            
            if (args.length >= 4) {
                mySpeed = (Double) args[2];
                myVision = (Double) args[3];
            }
        } else {
            position = new Position(
                    100 + Math.random() * (environment.getWidth() - 200),
                    100 + Math.random() * (environment.getHeight() - 200)
            );
        }

        energy = VisualizerAgent.SimParams.PRED_ENERGY_START;
        environment.registerAgent(getAID(), "PREDATOR", position, energy, mySpeed, myVision);

        addBehaviour(new PredatorBrain());
    }

    protected void takeDown() {
        environment.unregisterAgent(getAID());
    }

    private class PredatorBrain extends CyclicBehaviour {
        private int reproductionCooldown = 0;
        private int eatingCooldown = 0;
        private int cycleCount = 0;

        public void action() {
            cycleCount++;
            handleCooldowns();
            
            // 1. Check Vital Signs
            if (energy <= 0) {
                System.out.println("💀 " + getLocalName() + " starved");
                myAgent.doDelete();
                return;
            }

            // 2. Perception
            List<AgentInfo> nearby = environment.getNearbyAgents(getAID(), position, myVision);
            List<AgentInfo> preyList = nearby.stream()
                    .filter(info -> info.isPrey() && !info.getAID().equals(getAID()))
                    .toList();

            // 3. STATE MACHINE LOGIC
            switch (currentState) {
                case RESTING:
                    handleRestingState();
                    break;
                case HUNTING:
                    handleHuntingState(preyList);
                    break;
                case SCOUTING:
                default:
                    handleScoutingState(preyList, nearby);
                    break;
            }

            // 4. Physics & Updates
            updatePositionAndStats();
            
            // 5. Reproduction (Only if healthy)
            if (currentState != State.HUNTING && energy >= VisualizerAgent.SimParams.PRED_REPRO_THRESHOLD) {
                tryReproduce(nearby);
            }

            try { Thread.sleep(40); } catch (Exception e) {}
        }

        // --- STATE HANDLERS ---

        private void handleRestingState() {
            // Recover Stamina SLOWLY
            stamina += 1; // Was 2
            if (stamina >= MAX_STAMINA) {
                stamina = MAX_STAMINA;
                currentState = State.SCOUTING; // Back to work
            }
            // Don't move while resting!
        }

        private void handleHuntingState(List<AgentInfo> preyList) {
            // Burn Stamina very fast!
            stamina -= 4; // Was 2
            
            if (stamina <= 0) {
                currentState = State.RESTING; // Exhausted! Give up chase.
                return;
            }

            if (preyList.isEmpty()) {
                currentState = State.SCOUTING; // Lost target
                return;
            }

            // Chase closest prey
            AgentInfo target = findClosest(preyList);
            double dist = position.distance(target.getPosition());

            if (dist <= CATCH_DISTANCE && eatingCooldown <= 0) {
                capture(target);
                currentState = State.SCOUTING; // Relax after eating
            } else {
                // SPRINT: Move at 1.25x speed (nerfed from 1.4x)
                moveTo(target.getPosition(), mySpeed * 1.25);
            }
        }

        private void handleScoutingState(List<AgentInfo> preyList, List<AgentInfo> nearby) {
            // Recover Stamina slowly while walking
            if (stamina < MAX_STAMINA) stamina++;

            // Transition to HUNTING if prey seen and we have energy
            if (!preyList.isEmpty() && stamina > 30 && eatingCooldown <= 0) {
                currentState = State.HUNTING;
                return;
            }

            // Avoid Crowds
            List<AgentInfo> nearbyPredators = nearby.stream()
                    .filter(AgentInfo::isPredator).toList();
            
            if (nearbyPredators.size() > 3) {
                disperseFromCrowd(nearbyPredators);
            } else {
                // Random Patrol
                double angle = Math.random() * 2 * Math.PI;
                position = new Position(
                        position.getX() + Math.cos(angle) * mySpeed * 0.5, // Walk slower
                        position.getY() + Math.sin(angle) * mySpeed * 0.5
                );
            }
        }

        // --- HELPER METHODS ---

        private void handleCooldowns() {
            if (reproductionCooldown > 0) reproductionCooldown--;
            if (eatingCooldown > 0) eatingCooldown--;
            
            // Base metabolism
            if (cycleCount % 4 == 0) energy -= ENERGY_LOSS;
        }

        private void updatePositionAndStats() {
            // Keep bounds
            double x = Math.max(30, Math.min(environment.getWidth() - 30, position.getX()));
            double y = Math.max(30, Math.min(environment.getHeight() - 30, position.getY()));
            position.setX(x);
            position.setY(y);
            
            environment.updatePosition(getAID(), position);
        }

        private void moveTo(Position target, double speed) {
            double dx = target.getX() - position.getX();
            double dy = target.getY() - position.getY();
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
                dx /= dist;
                dy /= dist;
            }
            position = new Position(
                    position.getX() + dx * speed,
                    position.getY() + dy * speed
            );
        }

        private AgentInfo findClosest(List<AgentInfo> agents) {
            AgentInfo target = agents.get(0);
            double minDist = position.distance(target.getPosition());
            for (AgentInfo a : agents) {
                double d = position.distance(a.getPosition());
                if (d < minDist) {
                    minDist = d;
                    target = a;
                }
            }
            return target;
        }

        private void capture(AgentInfo prey) {
            energy = Math.min(VisualizerAgent.SimParams.PRED_ENERGY_MAX,
                    energy + VisualizerAgent.SimParams.PRED_ENERGY_GAIN);
            environment.unregisterAgent(prey.getAID());
            
            jade.lang.acl.ACLMessage killMsg = new jade.lang.acl.ACLMessage(jade.lang.acl.ACLMessage.REQUEST);
            killMsg.addReceiver(prey.getAID());
            killMsg.setContent("DIE");
            send(killMsg);
            
            eatingCooldown = EATING_COOLDOWN;
            System.out.println("🦁 " + getLocalName() + " ate prey");
        }

        private void tryReproduce(List<AgentInfo> nearby) {
            if (reproductionCooldown > 0) return;
            
            List<AgentInfo> partners = nearby.stream().filter(AgentInfo::isPredator).toList();
            if (partners.size() < 3 && Math.random() < 0.05) {
                reproduce();
            }
        }

        private void reproduce() {
            energy -= VisualizerAgent.SimParams.PRED_REPRO_COST;
            reproductionCooldown = REPRO_COOLDOWN;
            try {
                double childSpeed = mySpeed * (0.90 + Math.random() * 0.20);
                double childVision = myVision * (0.90 + Math.random() * 0.20);
                Object[] args = new Object[]{
                        position.getX() + (Math.random() - 0.5) * 60,
                        position.getY() + (Math.random() - 0.5) * 60,
                        childSpeed, childVision
                };
                getContainerController().createNewAgent("Predator_" + System.nanoTime(), "PredatorAgent", args).start();
            } catch (Exception e) {}
        }

        private void disperseFromCrowd(List<AgentInfo> nearbyPredators) {
            double avgX = 0, avgY = 0;
            for (AgentInfo other : nearbyPredators) {
                avgX += other.getPosition().getX();
                avgY += other.getPosition().getY();
            }
            avgX /= nearbyPredators.size();
            avgY /= nearbyPredators.size();
            
            double dx = position.getX() - avgX;
            double dy = position.getY() - avgY;
            
            position = new Position(
                    position.getX() + dx * 0.1 + (Math.random()-0.5)*10,
                    position.getY() + dy * 0.1 + (Math.random()-0.5)*10
            );
        }
    }
}