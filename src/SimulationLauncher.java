import jade.core.Runtime;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class SimulationLauncher {

    public static void main(String[] args) {
        try {
            Runtime runtime = Runtime.instance();
            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.MAIN_HOST, "localhost");
            profile.setParameter(Profile.GUI, "true");

            AgentContainer mainContainer = runtime.createMainContainer(profile);

            System.out.println("=== Lancement de la Simulation Proie-Prédateur ===");
            System.out.println("Configuration initiale via l'interface graphique");

            // Only start the visualizer - NO initial agents
            // User will configure populations and press Start
            AgentController visualizer = mainContainer.createNewAgent(
                    "Visualizer",
                    "VisualizerAgent",
                    null
            );
            visualizer.start();

            System.out.println("✓ Interface prête !");
            System.out.println("✓ Configurez les populations et appuyez sur Démarrer");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}