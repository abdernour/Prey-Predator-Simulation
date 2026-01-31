import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import java.util.List;
import java.util.stream.Collectors;

public class PredatorAgent extends Agent {
    private Position position;
    private int energy;
    private Environment environment;
    
    // GENETICS
    private double mySpeed;
    private double myVision;

    // MOVEMENT PERSISTENCE
    private double wanderAngle = Math.random() * 2 * Math.PI;

    // BRAIN (State Machine)
    private enum State { SCOUTING, HUNTING, RESTING }
    private State currentState = State.SCOUTING;
    private int stamina = 100;
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
            
            if (energy <= 0) {
                System.out.println("💀 " + getLocalName() + " starved");
                myAgent.doDelete();
                return;
            }

            // TERRAIN CHECKS
            boolean inSwamp = environment.isInSwamp(position);

            // PERCEPTION
            List<AgentInfo> nearby = environment.getNearbyAgents(getAID(), position, myVision);
            
            // Filter visible prey (Forest Logic)
            List<AgentInfo> preyList = nearby.stream()
                    .filter(info -> {
                        if (!info.isPrey()) return false;
                        if (info.getAID().equals(getAID())) return false;
                        
                        if (environment.isInForest(info.getPosition())) {
                            return position.distance(info.getPosition()) < (myVision * 0.3);
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            // PACK TACTICS
            switch (currentState) {
                case RESTING:
                    handleRestingState(inSwamp);
                    break;
                case HUNTING:
                    handleHuntingState(preyList, inSwamp);
                    break;
                case SCOUTING:
                default:
                    handleScoutingState(preyList, nearby, inSwamp);
                    break;
            }

            updatePositionAndStats();
            
            if (currentState != State.HUNTING && energy >= VisualizerAgent.SimParams.PRED_REPRO_THRESHOLD) {
                tryReproduce(nearby);
            }

            try { Thread.sleep(40); } catch (Exception e) {}
        }

        private void handleRestingState(boolean inSwamp) {
            stamina += 1;
            if (stamina >= MAX_STAMINA) {
                stamina = MAX_STAMINA;
                currentState = State.SCOUTING;
            }
            
            wanderAngle += (Math.random() - 0.5) * 0.2;
            double dx = Math.cos(wanderAngle);
            double dy = Math.sin(wanderAngle);
            
            double speed = mySpeed * 0.2;
            if (inSwamp) speed *= 0.5;

            position = new Position(
                    position.getX() + dx * speed,
                    position.getY() + dy * speed
            );
            
            checkBoundsBounce();
        }

        private void handleHuntingState(List<AgentInfo> preyList, boolean inSwamp) {
            stamina -= inSwamp ? 4 : 2;
            
            if (stamina <= 0) {
                currentState = State.RESTING;
                return;
            }

            if (preyList.isEmpty()) {
                currentState = State.SCOUTING;
                return;
            }

            AgentInfo target = findClosest(preyList);
            double dist = position.distance(target.getPosition());

            if (dist <= CATCH_DISTANCE && eatingCooldown <= 0) {
                capture(target);
                currentState = State.SCOUTING;
            } else {
                double dx = target.getPosition().getX() - position.getX();
                double dy = target.getPosition().getY() - position.getY();
                wanderAngle = Math.atan2(dy, dx);
                
                double speed = mySpeed * 1.5;
                if (inSwamp) speed *= 0.5;

                moveTo(target.getPosition(), speed);
            }
        }

        private void handleScoutingState(List<AgentInfo> preyList, List<AgentInfo> nearby, boolean inSwamp) {
            if (stamina < MAX_STAMINA) stamina++;

            if (!preyList.isEmpty() && stamina > 30 && eatingCooldown <= 0) {
                currentState = State.HUNTING;
                return;
            }

            List<AgentInfo> huntingPartners = nearby.stream()
                    .filter(info -> info.isPredator() && !info.getAID().equals(getAID()))
                    .filter(pred -> {
                        for (AgentInfo prey : preyList) {
                            if (pred.getPosition().distance(prey.getPosition()) < 150) return true;
                        }
                        return false;
                    })
                    .collect(Collectors.toList());

            if (!huntingPartners.isEmpty() && stamina > 50) {
                AgentInfo partner = huntingPartners.get(0);
                double dx = partner.getPosition().getX() - position.getX();
                double dy = partner.getPosition().getY() - position.getY();
                wanderAngle = Math.atan2(dy, dx);
                
                double speed = mySpeed * 1.1; 
                if (inSwamp) speed *= 0.5;
                
                position = new Position(
                        position.getX() + Math.cos(wanderAngle) * speed,
                        position.getY() + Math.sin(wanderAngle) * speed
                );
                return;
            }

            List<AgentInfo> nearbyPredators = nearby.stream()
                    .filter(AgentInfo::isPredator).collect(Collectors.toList());
            
            if (nearbyPredators.size() > 3) {
                disperseFromCrowd(nearbyPredators, inSwamp);
            } else {
                wanderAngle += (Math.random() - 0.5) * 0.4;
                
                double dx = Math.cos(wanderAngle);
                double dy = Math.sin(wanderAngle);
                
                double speed = mySpeed * 0.6;
                if (inSwamp) speed *= 0.5;

                position = new Position(
                        position.getX() + dx * speed,
                        position.getY() + dy * speed
                );
                
                checkBoundsBounce();
            }
        }

        private void checkBoundsBounce() {
            if (position.getX() <= 30 || position.getX() >= environment.getWidth() - 30) {
                wanderAngle = Math.PI - wanderAngle;
            }
            if (position.getY() <= 30 || position.getY() >= environment.getHeight() - 30) {
                wanderAngle = -wanderAngle;
            }
        }

        private void handleCooldowns() {
            if (reproductionCooldown > 0) reproductionCooldown--;
            if (eatingCooldown > 0) eatingCooldown--;
            if (cycleCount % 4 == 0) energy -= ENERGY_LOSS;
        }

        private void updatePositionAndStats() {
            double x = Math.max(30, Math.min(environment.getWidth() - 30, position.getX()));
            double y = Math.max(30, Math.min(environment.getHeight() - 30, position.getY()));
            position.setX(x);
            position.setY(y);
            
            // update position and energy
            environment.updatePosition(getAID(), position, energy);
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
            
            List<AgentInfo> partners = nearby.stream().filter(AgentInfo::isPredator).collect(Collectors.toList());
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

        private void disperseFromCrowd(List<AgentInfo> nearbyPredators, boolean inSwamp) {
            double avgX = 0, avgY = 0;
            for (AgentInfo other : nearbyPredators) {
                avgX += other.getPosition().getX();
                avgY += other.getPosition().getY();
            }
            avgX /= nearbyPredators.size();
            avgY /= nearbyPredators.size();
            
            double dx = position.getX() - avgX;
            double dy = position.getY() - avgY;
            
            wanderAngle = Math.atan2(dy, dx);
            
            double speed = 0.1;
            if (inSwamp) speed *= 0.5;

            position = new Position(
                    position.getX() + dx * speed + (Math.random()-0.5)*10,
                    position.getY() + dy * speed + (Math.random()-0.5)*10
            );
        }
    }
}