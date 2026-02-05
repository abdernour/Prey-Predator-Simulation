
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.wrapper.AgentController;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.text.DecimalFormat;

public class VisualizerAgent extends Agent {
    private SimulationPanel panel;
    private JFrame frame;
    private Environment environment;
    private PopulationChart chart;
    private ControlPanel controlPanel;
    private ParameterPanel parameterPanel;
    private InspectorPanel inspectorPanel;
    private StatsPanel statsPanel;
    private boolean isRunning = false;
    
    private AgentInfo selectedAgent = null;

    // Shared simulation parameters
    public static class SimParams {
        // Prey parameters
        public static int PREY_ENERGY_START = 60;
        public static int PREY_ENERGY_MAX = 120;
        public static int PREY_REPRO_THRESHOLD = 100;
        public static int PREY_REPRO_COST = 70;
        public static double PREY_SPEED = 2.5; 

        // Predator parameters
        public static int PRED_ENERGY_START = 250;
        public static int PRED_ENERGY_MAX = 400;
        public static int PRED_ENERGY_GAIN = 80; 
        public static int PRED_REPRO_THRESHOLD = 250; 
        public static int PRED_REPRO_COST = 100; 
        public static double PRED_SPEED = 2.9;

        // Food parameters
        public static int FOOD_ENERGY_VALUE = 40;
        public static int FOOD_SPAWN_RATE = 10;
        public static int FOOD_PER_SPAWN = 2;
    }

    protected void setup() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}

        environment = Environment.getInstance();

        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Simulation Proie-Prédateur");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout(0, 0));
            frame.getContentPane().setBackground(new Color(240, 242, 245));

            // Top control panel
            controlPanel = new ControlPanel();
            JPanel topContainer = new JPanel(new BorderLayout());
            topContainer.setBackground(Color.WHITE);
            topContainer.setBorder(new MatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));
            topContainer.add(controlPanel, BorderLayout.CENTER);
            frame.add(topContainer, BorderLayout.NORTH);

            // Center simulation
            JPanel centerContainer = new JPanel(new BorderLayout(20, 0));
            centerContainer.setBackground(new Color(240, 242, 245));
            centerContainer.setBorder(new EmptyBorder(20, 20, 20, 20));

            // Simulation panel
            panel = new SimulationPanel();
            
            // MOUSE LISTENER FOR SELECTION
            panel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectAgentAt(e.getX(), e.getY());
                }
            });

            JPanel simWrapper = new JPanel(new BorderLayout());
            simWrapper.setBackground(new Color(240, 242, 245));
            simWrapper.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(220, 220, 220), 1, true),
                    new EmptyBorder(0, 0, 0, 0)
            ));
            simWrapper.add(panel, BorderLayout.CENTER);
            centerContainer.add(simWrapper, BorderLayout.CENTER);

            // RIGHT SIDEBAR (Inspector + Stats + Parameters)
            JPanel rightSidebar = new JPanel();
            rightSidebar.setLayout(new BoxLayout(rightSidebar, BoxLayout.Y_AXIS));
            rightSidebar.setBackground(new Color(240, 242, 245));
            
            // Inspector Panel
            inspectorPanel = new InspectorPanel();
            rightSidebar.add(inspectorPanel);
            rightSidebar.add(Box.createVerticalStrut(15));

            // Stats Panel
            statsPanel = new StatsPanel();
            rightSidebar.add(statsPanel);
            rightSidebar.add(Box.createVerticalStrut(15));

            // Parameters Panel
            parameterPanel = new ParameterPanel();
            JScrollPane paramScroll = new JScrollPane(parameterPanel);
            paramScroll.setPreferredSize(new Dimension(320, 300));
            paramScroll.setBorder(null);
            paramScroll.getViewport().setBackground(new Color(240, 242, 245));
            paramScroll.getVerticalScrollBar().setUnitIncrement(16);
            
            rightSidebar.add(paramScroll);
            
            centerContainer.add(rightSidebar, BorderLayout.EAST);

            frame.add(centerContainer, BorderLayout.CENTER);

            // Chart
            JPanel chartWrapper = new JPanel(new BorderLayout());
            chartWrapper.setBackground(new Color(240, 242, 245));
            chartWrapper.setBorder(new EmptyBorder(0, 20, 20, 20));

            chart = new PopulationChart();
            JPanel chartCard = new JPanel(new BorderLayout());
            chartCard.add(chart);
            chartCard.setBorder(new LineBorder(new Color(220, 220, 220), 1, true));

            chartWrapper.add(chartCard, BorderLayout.CENTER);
            frame.add(chartWrapper, BorderLayout.SOUTH);

            frame.setSize(1400, 950);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        addBehaviour(new TickerBehaviour(this, 33) {
            private int tickCount = 0;
            protected void onTick() {
                if (!isRunning) return;

                tickCount++;
                environment.updateSeason();

                // Seasonal Food
                int spawnRate = SimParams.FOOD_SPAWN_RATE;
                int amount = SimParams.FOOD_PER_SPAWN;
                switch (environment.getCurrentSeason()) {
                    case SPRING: spawnRate = Math.max(1, spawnRate / 2); amount += 1; break;
                    case SUMMER: break;
                    case AUTUMN: spawnRate = spawnRate * 2; break;
                    case WINTER: spawnRate = spawnRate * 3; break;
                }

                if (tickCount % spawnRate == 0) {
                    for (int i = 0; i < amount; i++) {
                        double x = 50 + Math.random() * (environment.getWidth() - 100);
                        double y = 50 + Math.random() * (environment.getHeight() - 100);
                        environment.spawnFood(new Position(x, y));
                    }
                }

                // immigration system (safety net)
                if (tickCount % 100 == 0) { // Check every ~3 seconds
                    if (environment.getPreyCount() < 6) {
                        controlPanel.spawnSingleAgent("PreyAgent", "Prey");
                        controlPanel.spawnSingleAgent("PreyAgent", "Prey");
                        System.out.println("🚑 Emergency Prey Immigration!");
                    }
                    if (environment.getPredatorCount() < 2) {
                        controlPanel.spawnSingleAgent("PredatorAgent", "Predator");
                        System.out.println("🚑 Emergency Predator Immigration!");
                    }
                }

                if (panel != null) panel.repaint();
                if (chart != null && tickCount % 3 == 0) chart.updateData(environment.getPreyCount(), environment.getPredatorCount());
                
                // Update UI
                SwingUtilities.invokeLater(() -> {
                    if (parameterPanel != null && tickCount % 10 == 0) {
                        parameterPanel.updateLiveStats(environment.getPreyCount(), environment.getPredatorCount(), environment.getFoodCount());
                    }
                    if (statsPanel != null && tickCount % 10 == 0) {
                        statsPanel.updateStats(environment.getStats());
                    }
                    if (inspectorPanel != null && selectedAgent != null) {
                        AgentInfo freshInfo = environment.getAllAgents().get(selectedAgent.getAID());
                        if (freshInfo != null) {
                            selectedAgent = freshInfo;
                            inspectorPanel.updateInfo(selectedAgent);
                        } else {
                            selectedAgent = null;
                            inspectorPanel.clearInfo();
                        }
                    }
                });
            }
        });
    }

    private void selectAgentAt(int x, int y) {
        Position clickPos = new Position(x, y);
        double bestDist = 30.0; // Click radius
        AgentInfo bestMatch = null;

        for (AgentInfo info : environment.getAllAgents().values()) {
            double dist = info.getPosition().distance(clickPos);
            if (dist < bestDist) {
                bestDist = dist;
                bestMatch = info;
            }
        }
        
        selectedAgent = bestMatch;
        if (selectedAgent != null) {
            inspectorPanel.updateInfo(selectedAgent);
        } else {
            inspectorPanel.clearInfo();
        }
        panel.repaint();
    }

    private void startSimulation() { isRunning = true; }
    private void stopSimulation() { isRunning = false; }

    // stats panel
    class StatsPanel extends JPanel {
        private JLabel huntedLabel, starvedPreyLabel, oldAgeLabel, starvedPredLabel;

        public StatsPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(230, 230, 230), 1, true),
                    new EmptyBorder(15, 15, 15, 15)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

            JLabel title = new JLabel("Statistiques de Décès");
            title.setFont(new Font("Segoe UI", Font.BOLD, 16));
            title.setForeground(Color.GRAY);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(title);
            add(Box.createVerticalStrut(10));

            huntedLabel = createStatRow("🐰 Chassés:", new Color(220, 53, 69));
            starvedPreyLabel = createStatRow("🐰 Affamés:", new Color(255, 193, 7));
            oldAgeLabel = createStatRow("🐰 Vieillesse:", Color.GRAY);
            add(Box.createVerticalStrut(5));
            starvedPredLabel = createStatRow("🦁 Affamés:", new Color(220, 53, 69));
        }

        private JLabel createStatRow(String title, Color color) {
            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(Color.WHITE);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            JLabel titleLbl = new JLabel(title);
            titleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            row.add(titleLbl, BorderLayout.WEST);
            
            JLabel valueLbl = new JLabel("0");
            valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            valueLbl.setForeground(color);
            row.add(valueLbl, BorderLayout.EAST);
            
            add(row);
            add(Box.createVerticalStrut(5));
            return valueLbl;
        }

        public void updateStats(Environment.DeathStats stats) {
            huntedLabel.setText(String.valueOf(stats.preyHunted));
            starvedPreyLabel.setText(String.valueOf(stats.preyStarved));
            oldAgeLabel.setText(String.valueOf(stats.preyOldAge));
            starvedPredLabel.setText(String.valueOf(stats.predStarved));
        }
    }

    // ==========================================
    // INSPECTOR PANEL
    // ==========================================
    class InspectorPanel extends JPanel {
        private JLabel nameLabel, typeLabel, energyLabel, speedLabel, visionLabel;
        private JProgressBar energyBar;
        private JPanel contentPanel;
        private JLabel emptyLabel;

        public InspectorPanel() {
            setLayout(new BorderLayout());
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(230, 230, 230), 1, true),
                    new EmptyBorder(15, 15, 15, 15)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

            // Header
            JLabel title = new JLabel("Inspecteur");
            title.setFont(new Font("Segoe UI", Font.BOLD, 16));
            title.setForeground(Color.GRAY);
            add(title, BorderLayout.NORTH);

            // Empty State
            emptyLabel = new JLabel("Cliquez sur un agent", SwingConstants.CENTER);
            emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            emptyLabel.setForeground(Color.LIGHT_GRAY);
            emptyLabel.setPreferredSize(new Dimension(200, 120));
            add(emptyLabel, BorderLayout.CENTER);

            // Content State (Hidden initially)
            contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            contentPanel.setBackground(Color.WHITE);
            
            nameLabel = new JLabel("Agent #001");
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            typeLabel = new JLabel("PREDATOR");
            typeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            typeLabel.setForeground(new Color(220, 53, 69));
            typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            energyBar = new JProgressBar(0, 100);
            energyBar.setForeground(new Color(40, 167, 69));
            energyBar.setStringPainted(true);
            energyBar.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            speedLabel = new JLabel("Vitesse: 2.5");
            visionLabel = new JLabel("Vision: 100px");
            
            contentPanel.add(Box.createVerticalStrut(10));
            contentPanel.add(nameLabel);
            contentPanel.add(typeLabel);
            contentPanel.add(Box.createVerticalStrut(10));
            contentPanel.add(new JLabel("Énergie:"));
            contentPanel.add(energyBar);
            contentPanel.add(Box.createVerticalStrut(10));
            contentPanel.add(new JLabel("Génétique:"));
            contentPanel.add(speedLabel);
            contentPanel.add(visionLabel);
        }

        public void updateInfo(AgentInfo info) {
            remove(emptyLabel);
            add(contentPanel, BorderLayout.CENTER);
            
            nameLabel.setText(info.getAID().getLocalName());
            
            if (info.isPrey()) {
                typeLabel.setText("PROIE");
                typeLabel.setForeground(new Color(40, 167, 69));
                energyBar.setMaximum(SimParams.PREY_ENERGY_MAX);
            } else {
                typeLabel.setText("PRÉDATEUR");
                typeLabel.setForeground(new Color(220, 53, 69));
                energyBar.setMaximum(SimParams.PRED_ENERGY_MAX);
            }
            
            energyBar.setValue(info.getEnergy());
            energyBar.setString(info.getEnergy() + " / " + energyBar.getMaximum());
            
            DecimalFormat df = new DecimalFormat("#.##");
            speedLabel.setText("⚡ Vitesse: " + df.format(info.getSpeed()));
            visionLabel.setText("👁 Vision: " + (int)info.getVisionRange() + "px");
            
            revalidate();
            repaint();
        }

        public void clearInfo() {
            remove(contentPanel);
            add(emptyLabel, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }

    // ==========================================
    // PARAMETER PANEL
    // ==========================================
    class ParameterPanel extends JPanel {
        private Map<String, JSpinner> preySpinners = new HashMap<>();
        private Map<String, JSpinner> predSpinners = new HashMap<>();
        private Map<String, JSpinner> foodSpinners = new HashMap<>();
        private JLabel livePreyLabel, livePredatorLabel, liveFoodLabel;

        public ParameterPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(new Color(240, 242, 245));
            setBorder(new EmptyBorder(0, 0, 0, 5));

            JLabel title = new JLabel("Paramètres");
            title.setFont(new Font("Segoe UI", Font.BOLD, 20));
            title.setForeground(new Color(40, 40, 40));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            title.setBorder(new EmptyBorder(0, 5, 15, 0));
            add(title);

            add(createLiveStatsCard());
            add(Box.createVerticalStrut(15));
            add(createSectionCard("Proies", new Color(34, 139, 34), "prey"));
            add(Box.createVerticalStrut(15));
            add(createSectionCard("Prédateurs", new Color(220, 20, 60), "pred"));
            add(Box.createVerticalStrut(15));
            add(createSectionCard("Nourriture", new Color(255, 165, 0), "food"));
            add(Box.createVerticalStrut(20));

            JButton applyBtn = new JButton("Appliquer");
            applyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            applyBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            applyBtn.setBackground(new Color(0, 123, 255));
            applyBtn.setForeground(Color.WHITE);
            applyBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            applyBtn.setFocusPainted(false);
            applyBtn.addActionListener(e -> applyParameters());
            add(applyBtn);
            add(Box.createVerticalGlue());
        }

        public void updateLiveStats(int prey, int pred, int food) {
            livePreyLabel.setText(String.valueOf(prey));
            livePredatorLabel.setText(String.valueOf(pred));
            liveFoodLabel.setText(String.valueOf(food));
        }

        private JPanel createLiveStatsCard() {
            JPanel card = new JPanel(new GridLayout(1, 3, 10, 0));
            card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(230, 230, 230), 1, true),
                    new EmptyBorder(15, 10, 15, 10)
            ));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

            livePreyLabel = createStatItem(card, "Proies", new Color(34, 139, 34));
            livePredatorLabel = createStatItem(card, "Préd.", new Color(220, 20, 60));
            liveFoodLabel = createStatItem(card, "Nourr.", new Color(255, 165, 0));
            return card;
        }

        private JLabel createStatItem(JPanel parent, String title, Color color) {
            JPanel item = new JPanel(new BorderLayout());
            item.setBackground(Color.WHITE);
            JLabel titleLbl = new JLabel(title, SwingConstants.CENTER);
            titleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            titleLbl.setForeground(Color.GRAY);
            item.add(titleLbl, BorderLayout.NORTH);
            JLabel valueLbl = new JLabel("0", SwingConstants.CENTER);
            valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
            valueLbl.setForeground(color);
            item.add(valueLbl, BorderLayout.CENTER);
            parent.add(item);
            return valueLbl;
        }

        private JPanel createSectionCard(String title, Color accentColor, String type) {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(230, 230, 230), 1, true),
                    new EmptyBorder(15, 15, 15, 15)
            ));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, type.equals("food") ? 180 : 250));

            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            header.setBackground(Color.WHITE);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel bar = new JPanel();
            bar.setPreferredSize(new Dimension(4, 16));
            bar.setBackground(accentColor);
            header.add(bar);
            JLabel label = new JLabel("  " + title);
            label.setFont(new Font("Segoe UI", Font.BOLD, 14));
            header.add(label);
            card.add(header);
            card.add(Box.createVerticalStrut(15));

            if (type.equals("prey")) {
                addPreyParameter(card, "Énergie initiale", SimParams.PREY_ENERGY_START, 10, 200, 5);
                addPreyParameter(card, "Énergie max", SimParams.PREY_ENERGY_MAX, 50, 300, 10);
                addPreyParameter(card, "Seuil reprod.", SimParams.PREY_REPRO_THRESHOLD, 30, 150, 5);
                addPreyParameter(card, "Coût reprod.", SimParams.PREY_REPRO_COST, 10, 100, 5);
            } else if (type.equals("pred")) {
                addPredParameter(card, "Énergie initiale", SimParams.PRED_ENERGY_START, 50, 400, 10);
                addPredParameter(card, "Énergie max", SimParams.PRED_ENERGY_MAX, 100, 500, 10);
                addPredParameter(card, "Gain capture", SimParams.PRED_ENERGY_GAIN, 20, 150, 5);
                addPredParameter(card, "Seuil reprod.", SimParams.PRED_REPRO_THRESHOLD, 50, 300, 10);
                addPredParameter(card, "Coût reprod.", SimParams.PRED_REPRO_COST, 20, 150, 5);
            } else if (type.equals("food")) {
                addFoodParameter(card, "Valeur énerg.", SimParams.FOOD_ENERGY_VALUE, 10, 100, 5);
                addFoodParameter(card, "Taux spawn", SimParams.FOOD_SPAWN_RATE, 1, 50, 1);
                addFoodParameter(card, "Qté par spawn", SimParams.FOOD_PER_SPAWN, 1, 10, 1);
            }
            return card;
        }

        private void addPreyParameter(JPanel panel, String label, int value, int min, int max, int step) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
            preySpinners.put(label, spinner);
            panel.add(createParameterRow(label, spinner));
            panel.add(Box.createVerticalStrut(8));
        }
        private void addPredParameter(JPanel panel, String label, int value, int min, int max, int step) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
            predSpinners.put(label, spinner);
            panel.add(createParameterRow(label, spinner));
            panel.add(Box.createVerticalStrut(8));
        }
        private void addFoodParameter(JPanel panel, String label, int value, int min, int max, int step) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
            foodSpinners.put(label, spinner);
            panel.add(createParameterRow(label, spinner));
            panel.add(Box.createVerticalStrut(8));
        }
        private JPanel createParameterRow(String label, JSpinner spinner) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setBackground(Color.WHITE);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel lbl = new JLabel(label);
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            row.add(lbl, BorderLayout.CENTER);
            spinner.setPreferredSize(new Dimension(70, 28));
            row.add(spinner, BorderLayout.EAST);
            return row;
        }
        private void applyParameters() {
            try {
                SimParams.PREY_ENERGY_START = (Integer) preySpinners.get("Énergie initiale").getValue();
                SimParams.PREY_ENERGY_MAX = (Integer) preySpinners.get("Énergie max").getValue();
                SimParams.PREY_REPRO_THRESHOLD = (Integer) preySpinners.get("Seuil reprod.").getValue();
                SimParams.PREY_REPRO_COST = (Integer) preySpinners.get("Coût reprod.").getValue();
                SimParams.PRED_ENERGY_START = (Integer) predSpinners.get("Énergie initiale").getValue();
                SimParams.PRED_ENERGY_MAX = (Integer) predSpinners.get("Énergie max").getValue();
                SimParams.PRED_ENERGY_GAIN = (Integer) predSpinners.get("Gain capture").getValue();
                SimParams.PRED_REPRO_THRESHOLD = (Integer) predSpinners.get("Seuil reprod.").getValue();
                SimParams.PRED_REPRO_COST = (Integer) predSpinners.get("Coût reprod.").getValue();
                SimParams.FOOD_ENERGY_VALUE = (Integer) foodSpinners.get("Valeur énerg.").getValue();
                SimParams.FOOD_SPAWN_RATE = (Integer) foodSpinners.get("Taux spawn").getValue();
                SimParams.FOOD_PER_SPAWN = (Integer) foodSpinners.get("Qté par spawn").getValue();
                JOptionPane.showMessageDialog(this, "Paramètres appliqués!", "Succès", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {}
        }
    }

    // ==========================================
    // CONTROL PANEL
    // ==========================================
    class ControlPanel extends JPanel {
        private JLabel statusLabel;
        private JSpinner preySpinner, predatorSpinner;
        private JButton startBtn, pauseBtn;

        public ControlPanel() {
            setLayout(new BorderLayout());
            setBackground(Color.WHITE);
            setBorder(new EmptyBorder(10, 20, 10, 20));

            JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
            leftPanel.setBackground(Color.WHITE);
            statusLabel = new JLabel("PRÊT");
            statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            statusLabel.setForeground(Color.GRAY);
            leftPanel.add(statusLabel);
            leftPanel.add(new JSeparator(SwingConstants.VERTICAL));
            leftPanel.add(new JLabel("Proies:"));
            preySpinner = new JSpinner(new SpinnerNumberModel(15, 0, 100, 1));
            leftPanel.add(preySpinner);
            leftPanel.add(new JLabel("Préd:"));
            predatorSpinner = new JSpinner(new SpinnerNumberModel(8, 0, 50, 1));
            leftPanel.add(predatorSpinner);
            add(leftPanel, BorderLayout.WEST);

            JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            centerPanel.setBackground(Color.WHITE);
            
            // ICON BUTTONS
            startBtn = createButton("▶", new Color(40, 167, 69));
            pauseBtn = createButton("⏸", new Color(220, 53, 69));
            JButton restartBtn = createButton("↺", new Color(0, 123, 255));
            pauseBtn.setEnabled(false);

            startBtn.addActionListener(e -> {
                if (environment.getPreyCount() == 0 && environment.getPredatorCount() == 0) spawnInitialPopulation();
                startSimulation();
                startBtn.setEnabled(false);
                pauseBtn.setEnabled(true);
                statusLabel.setText("EN COURS");
                statusLabel.setForeground(new Color(40, 167, 69));
            });
            pauseBtn.addActionListener(e -> {
                stopSimulation();
                startBtn.setEnabled(true);
                pauseBtn.setEnabled(false);
                statusLabel.setText("PAUSE");
                statusLabel.setForeground(new Color(220, 53, 69));
            });
            restartBtn.addActionListener(e -> {
                stopSimulation();
                for (jade.core.AID aid : new java.util.HashSet<>(environment.getAllAgents().keySet())) environment.unregisterAgent(aid);
                environment.getAllFoods().clear();
                environment.resetStats(); // reset stats
                startBtn.setEnabled(true);
                pauseBtn.setEnabled(false);
                statusLabel.setText("PRÊT");
                statusLabel.setForeground(Color.GRAY);
                panel.repaint();
                chart.updateData(0, 0);
                parameterPanel.updateLiveStats(0, 0, 0);
                statsPanel.updateStats(environment.getStats()); // reset stats ui
                selectedAgent = null;
                inspectorPanel.clearInfo();
            });

            centerPanel.add(startBtn);
            centerPanel.add(pauseBtn);
            centerPanel.add(restartBtn);
            add(centerPanel, BorderLayout.CENTER);

            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
            rightPanel.setBackground(Color.WHITE);
            JButton addPrey = createButton("+ 🐰", new Color(34, 139, 34));
            addPrey.addActionListener(e -> spawnSingleAgent("PreyAgent", "Prey"));
            JButton addPred = createButton("+ 🦁", new Color(220, 20, 60));
            addPred.addActionListener(e -> spawnSingleAgent("PredatorAgent", "Predator"));
            rightPanel.add(addPrey);
            rightPanel.add(addPred);
            add(rightPanel, BorderLayout.EAST);
        }

        private JButton createButton(String text, Color bg) {
            JButton btn = new JButton(text) {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (getModel().isPressed()) g2.setColor(bg.darker());
                    else g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btn.setBackground(bg);
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 16));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setPreferredSize(new Dimension(50, 34));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return btn;
        }
        
        public void spawnInitialPopulation() {
            int preyCount = (Integer) preySpinner.getValue();
            int predatorCount = (Integer) predatorSpinner.getValue();
            for (int i = 0; i < preyCount; i++) spawnSingleAgent("PreyAgent", "Prey");
            for (int i = 0; i < predatorCount; i++) spawnSingleAgent("PredatorAgent", "Predator");
        }

        public void spawnSingleAgent(String className, String prefix) {
            try {
                // randomize genetics for initial population
                double baseSpeed = prefix.equals("Prey") ? SimParams.PREY_SPEED : SimParams.PRED_SPEED;
                double baseVision = prefix.equals("Prey") ? 70.0 : 110.0;
                
                // +/- 15% variation
                double speed = baseSpeed * (0.85 + Math.random() * 0.30);
                double vision = baseVision * (0.85 + Math.random() * 0.30);
                
                Object[] args = new Object[]{
                        Math.random() * environment.getWidth(),
                        Math.random() * environment.getHeight(),
                        speed,
                        vision
                };
                String name = prefix + System.nanoTime();
                getContainerController().createNewAgent(name, className, args).start();
            } catch (Exception ex) {
                System.err.println("Error spawning agent: " + ex.getMessage());
            }
        }
    }

    // ==========================================
    // SIMULATION PANEL
    // ==========================================
    class SimulationPanel extends JPanel {
        private List<Shape> forestTrees = new ArrayList<>();
        private List<Color> treeColors = new ArrayList<>();
        private List<Shape> swampPuddles = new ArrayList<>();
        private List<Color> puddleColors = new ArrayList<>();
        private boolean terrainInitialized = false;

        public SimulationPanel() {
            setPreferredSize(new Dimension(900, 650));
            setBackground(new Color(245, 240, 235));
            setBorder(null);
        }

        private void initTerrainDecorations() {
            if (terrainInitialized) return;
            Random rand = new Random(12345);
            for (Shape tree : environment.getTrees()) {
                Rectangle2D r = tree.getBounds2D();
                int numTrees = (int)(r.getWidth() * r.getHeight() / 800);
                for (int i = 0; i < numTrees; i++) {
                    int size = 15 + rand.nextInt(20);
                    double x = r.getX() + rand.nextDouble() * (r.getWidth() - size);
                    double y = r.getY() + rand.nextDouble() * (r.getHeight() - size);
                    forestTrees.add(new Ellipse2D.Double(x, y, size, size));
                    int g = 130 + rand.nextInt(80);
                    treeColors.add(new Color(34, g, 34, 180));
                }
            }
            for (Shape swamp : environment.getSwamps()) {
                Rectangle2D r = swamp.getBounds2D();
                int numPuddles = (int)(r.getWidth() * r.getHeight() / 1000);
                for (int i = 0; i < numPuddles; i++) {
                    int w = 20 + rand.nextInt(30);
                    int h = 10 + rand.nextInt(15);
                    double x = r.getX() + rand.nextDouble() * (r.getWidth() - w);
                    double y = r.getY() + rand.nextDouble() * (r.getHeight() - h);
                    swampPuddles.add(new Ellipse2D.Double(x, y, w, h));
                    int darkness = rand.nextInt(40);
                    puddleColors.add(new Color(100 - darkness, 90 - darkness, 80 - darkness, 150));
                }
            }
            terrainInitialized = true;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!terrainInitialized && environment != null) initTerrainDecorations();
            
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bgColor = new Color(245, 240, 235);
            switch (environment.getCurrentSeason()) {
                case AUTUMN: bgColor = new Color(250, 235, 220); break;
                case WINTER: bgColor = new Color(240, 245, 250); break;
            }
            setBackground(bgColor);

            // Draw Terrain
            g2d.setColor(new Color(101, 67, 33, 100));
            for (Shape swamp : environment.getSwamps()) g2d.fill(swamp);
            
            g2d.setColor(new Color(128, 128, 128));
            for (Shape rock : environment.getRocks()) {
                g2d.setColor(new Color(0,0,0,40));
                g2d.translate(3,3); g2d.fill(rock); g2d.translate(-3,-3);
                g2d.setColor(new Color(128,128,128)); g2d.fill(rock);
                g2d.setColor(new Color(160,160,160)); g2d.draw(rock);
            }

            Color treeColor = new Color(34, 139, 34, 200);
            switch (environment.getCurrentSeason()) {
                case SUMMER: treeColor = new Color(20, 100, 20, 200); break;
                case AUTUMN: treeColor = new Color(200, 100, 20, 200); break;
                case WINTER: treeColor = new Color(200, 220, 220, 200); break;
            }
            for (Shape tree : environment.getTrees()) {
                g2d.setColor(treeColor); g2d.fill(tree);
            }

            // Draw Agents
            Map<jade.core.AID, AgentInfo> agents = environment.getAllAgents();
            for (Food food : environment.getAllFoods()) {
                Position pos = food.getPosition();
                g2d.setColor(new Color(255, 220, 0, 50));
                g2d.fill(new Ellipse2D.Double(pos.getX()-8, pos.getY()-8, 16, 16));
                g2d.setColor(new Color(255, 193, 7));
                g2d.fill(new Ellipse2D.Double(pos.getX()-5, pos.getY()-5, 10, 10));
            }

            for (AgentInfo info : agents.values()) {
                Position pos = info.getPosition();
                if (info.isPrey()) {
                    g2d.setColor(new Color(0,0,0,30)); g2d.fill(new Ellipse2D.Double(pos.getX()-5, pos.getY()-4, 10, 10));
                    g2d.setColor(new Color(40, 167, 69)); g2d.fill(new Ellipse2D.Double(pos.getX()-6, pos.getY()-6, 12, 12));
                } else {
                    g2d.setColor(new Color(0,0,0,40)); g2d.fill(new Ellipse2D.Double(pos.getX()-7, pos.getY()-6, 14, 14));
                    g2d.setColor(new Color(220, 53, 69)); g2d.fill(new Ellipse2D.Double(pos.getX()-8, pos.getY()-8, 16, 16));
                }
                
                // Draw Selection Ring
                if (selectedAgent != null && info.getAID().equals(selectedAgent.getAID())) {
                    g2d.setColor(new Color(0, 123, 255));
                    g2d.setStroke(new BasicStroke(2f));
                    g2d.draw(new Ellipse2D.Double(pos.getX()-12, pos.getY()-12, 24, 24));
                }
            }
            
            // Season Text
            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.setFont(new Font("Segoe UI", Font.BOLD, 16));
            String seasonText = "";
            switch (environment.getCurrentSeason()) {
                case SPRING: seasonText = "🌸 PRINTEMPS"; break;
                case SUMMER: seasonText = "☀️ ÉTÉ"; break;
                case AUTUMN: seasonText = "🍂 AUTOMNE"; break;
                case WINTER: seasonText = "❄️ HIVER"; break;
            }
            g2d.drawString(seasonText, 20, 30);
        }
    }

    // ==========================================
    // POPULATION CHART
    // ==========================================
    class PopulationChart extends JPanel {
        private List<Integer> preyHistory = new ArrayList<>();
        private List<Integer> predatorHistory = new ArrayList<>();
        private static final int MAX_POINTS = 200;

        public PopulationChart() {
            setPreferredSize(new Dimension(900, 200));
            setBackground(Color.WHITE);
        }

        public void updateData(int preyCount, int predatorCount) {
            preyHistory.add(preyCount);
            predatorHistory.add(predatorCount);
            if (preyHistory.size() > MAX_POINTS) {
                preyHistory.remove(0);
                predatorHistory.remove(0);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (preyHistory.isEmpty()) return;

            int width = getWidth();
            int height = getHeight();
            int padding = 40;
            int maxPop = Math.max(10, Math.max(
                    preyHistory.stream().max(Integer::compareTo).orElse(1),
                    predatorHistory.stream().max(Integer::compareTo).orElse(1)
            ));

            g2d.setColor(new Color(245, 245, 245));
            for (int i = 0; i <= 5; i++) {
                int y = padding + i * (height - 2 * padding) / 5;
                g2d.drawLine(padding, y, width - padding, y);
            }

            g2d.setColor(new Color(200, 200, 200));
            g2d.drawLine(padding, padding, padding, height - padding);
            g2d.drawLine(padding, height - padding, width - padding, height - padding);

            double xScale = (double) (width - 2 * padding) / MAX_POINTS;
            double yScale = (double) (height - 2 * padding) / maxPop;

            g2d.setColor(new Color(40, 167, 69));
            g2d.setStroke(new BasicStroke(2f));
            drawCurve(g2d, preyHistory, xScale, yScale, padding, height);

            g2d.setColor(new Color(220, 53, 69));
            drawCurve(g2d, predatorHistory, xScale, yScale, padding, height);
        }

        private void drawCurve(Graphics2D g2d, List<Integer> history, double xScale, double yScale, int padding, int height) {
            for (int i = 1; i < history.size(); i++) {
                int x1 = padding + (int) ((i - 1) * xScale);
                int y1 = height - padding - (int) (history.get(i - 1) * yScale);
                int x2 = padding + (int) (i * xScale);
                int y2 = height - padding - (int) (history.get(i) * yScale);
                g2d.drawLine(x1, y1, x2, y2);
            }
        }
    }
}
