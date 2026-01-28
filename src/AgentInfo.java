import jade.core.AID;
import java.io.Serializable;

public class AgentInfo implements Serializable {
    private AID aid;
    private String type;
    private Position position;
    private int energy;
    
    // Genetics
    private double speed;
    private double visionRange;

    public AgentInfo(AID aid, String type, Position position, int energy, double speed, double visionRange) {
        this.aid = aid;
        this.type = type;
        this.position = position;
        this.energy = energy;
        this.speed = speed;
        this.visionRange = visionRange;
    }

    public AID getAID() { return aid; }
    public String getType() { return type; }
    public Position getPosition() { return position; }
    public int getEnergy() { return energy; }
    public double getSpeed() { return speed; }
    public double getVisionRange() { return visionRange; }

    public void setPosition(Position position) { this.position = position; }
    public void setEnergy(int energy) { this.energy = energy; }

    public boolean isPrey() { return "PREY".equals(type); }
    public boolean isPredator() { return "PREDATOR".equals(type); }

    @Override
    public String toString() {
        return String.format("%s[%s] at %s, E=%d, S=%.2f, V=%.0f",
                type, aid.getLocalName(), position, energy, speed, visionRange);
    }
}