
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

public class VisualizerAgent extends Agent {
    private SimulationPanel panel;
    private JFrame frame;
    private Environment environment;
    private PopulationChart chart;
    private ControlPanel controlPanel;
    private ParameterPanel parameterPanel;
    private boolean isRunning = false;

    // Shared simulation parameters
    public static class SimParams {
        // Prey parameters
        public static int PREY_ENERGY_START = 85;
        public static int PREY_ENERGY_MAX = 120;
        public static int PREY_REPRO_THRESHOLD = 80;
        public static int PREY_REPRO_COST = 40;
        public static double PREY_SPEED = 2.4; 

        // Predator parameters
        public static int PRED_ENERGY_START = 200;
        public static int PRED_ENERGY_MAX = 300;
        public static int PRED_ENERGY_GAIN = 40; 
        public static int PRED_REPRO_THRESHOLD = 220; 
        public static int PRED_REPRO_COST = 110; 
        public static double PRED_SPEED = 2.65;

        // Food parameters
        public static int FOOD_ENERGY_VALUE = 35;
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
            frame.getContentPane().setBackground(new Color(240, 242, 245)); // Soft gray bg

            // Top control panel - sleek and minimal
            controlPanel = new ControlPanel();

            JPanel topContainer = new JPanel(new BorderLayout());
            topContainer.setBackground(Color.WHITE);
            topContainer.setBorder(new MatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));
            topContainer.add(controlPanel, BorderLayout.CENTER);
            frame.add(topContainer, BorderLayout.NORTH);

            // Center simulation on left, parameters on right
            JPanel centerContainer = new JPanel(new BorderLayout(20, 0)); // More spacing
            centerContainer.setBackground(new Color(240, 242, 245));
            centerContainer.setBorder(new EmptyBorder(20, 20, 20, 20)); // Outer padding

            // Simulation panel with nice border
            panel = new SimulationPanel();
            JPanel simWrapper = new JPanel(new BorderLayout());
            simWrapper.setBackground(new Color(240, 242, 245));
            // Card effect for simulation panel
            simWrapper.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(220, 220, 220), 1, true),
                    new EmptyBorder(0, 0, 0, 0)
            ));
            simWrapper.add(panel, BorderLayout.CENTER);
            centerContainer.add(simWrapper, BorderLayout.CENTER);

            // Parameters panel
            parameterPanel = new ParameterPanel();
            JScrollPane paramScroll = new JScrollPane(parameterPanel);
            paramScroll.setPreferredSize(new Dimension(320, 600)); // Slightly wider
            paramScroll.setBorder(null); // Remove default scroll border
            paramScroll.getViewport().setBackground(new Color(240, 242, 245)); // Match bg
            paramScroll.getVerticalScrollBar().setUnitIncrement(16);
            centerContainer.add(paramScroll, BorderLayout.EAST);

            frame.add(centerContainer, BorderLayout.CENTER);

            // Chart at bottom with padding
            JPanel chartWrapper = new JPanel(new BorderLayout());
            chartWrapper.setBackground(new Color(240, 242, 245));
            chartWrapper.setBorder(new EmptyBorder(0, 20, 20, 20)); // Match side padding

            chart = new PopulationChart();
            // Add shadow/border to chart
            JPanel chartCard = new JPanel(new BorderLayout());
            chartCard.add(chart);
            chartCard.setBorder(new LineBorder(new Color(220, 220, 220), 1, true));

            chartWrapper.add(chartCard, BorderLayout.CENTER);
            frame.add(chartWrapper, BorderLayout.SOUTH);

            frame.setSize(1350, 950);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        addBehaviour(new TickerBehaviour(this, 33) { // Increased refresh rate to ~30 FPS
            private int tickCount = 0;
            protected void onTick() {
                if (!isRunning) return;

                tickCount++;
                environment.updateSeason(); // update season

                // seasonal food spawn rate
                int spawnRate = SimParams.FOOD_SPAWN_RATE;
                int amount = SimParams.FOOD_PER_SPAWN;
                
                switch (environment.getCurrentSeason()) {
                    case SPRING: 
                        spawnRate = Math.max(1, spawnRate / 2); // 2x faster
                        amount += 1;
                        break;
                    case SUMMER:
                        // Normal
                        break;
                    case AUTUMN:
                        spawnRate = spawnRate * 2; // 0.5x slower
                        break;
                    case WINTER:
                        spawnRate = spawnRate * 5; // 0.2x slower (Very scarce)
                        break;
                }

                if (tickCount % spawnRate == 0) {
                    for (int i = 0; i < amount; i++) {
                        double x = 50 + Math.random() * (environment.getWidth() - 100);
                        double y = 50 + Math.random() * (environment.getHeight() - 100);
                        environment.spawnFood(new Position(x, y));
                    }
                }

                if (panel != null) panel.repaint();
                if (chart != null && tickCount % 3 == 0) chart.updateData(environment.getPreyCount(), environment.getPredatorCount());
                if (parameterPanel != null && tickCount % 10 == 0) {
                    SwingUtilities.invokeLater(() ->
                            parameterPanel.updateLiveStats(environment.getPreyCount(), environment.getPredatorCount(), environment.getFoodCount())
                    );
                }
            }
        });

        System.out.println("Visualizer Agent started");
    }

    private void startSimulation() { isRunning = true; System.out.println("Simulation started"); }
    private void stopSimulation() { isRunning = false; System.out.println("Simulation paused"); }

    private void spawnAgent(String className, String prefix) {
        try {
            Object[] args = new Object[]{
                    Math.random() * environment.getWidth(),
                    Math.random() * environment.getHeight()
            };
            String name = prefix + System.nanoTime();
            getContainerController().createNewAgent(name, className, args).start();
        } catch (Exception ex) {
            System.err.println("Error spawning agent: " + ex.getMessage());
        }
    }

    // card layout for parameters
    class ParameterPanel extends JPanel {
        private Map<String, JSpinner> preySpinners = new HashMap<>();
        private Map<String, JSpinner> predSpinners = new HashMap<>();
        private Map<String, JSpinner> foodSpinners = new HashMap<>();

        // Live stat labels
        private JLabel livePreyLabel, livePredatorLabel, liveFoodLabel;

        public ParameterPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(new Color(240, 242, 245)); // Match main bg
            setBorder(new EmptyBorder(0, 0, 0, 5)); // Right padding

            // Title
            JLabel title = new JLabel("Paramètres");
            title.setFont(new Font("Segoe UI", Font.BOLD, 20));
            title.setForeground(new Color(40, 40, 40));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            title.setBorder(new EmptyBorder(0, 5, 15, 0));
            add(title);

            // live stats card
            add(createLiveStatsCard());
            add(Box.createVerticalStrut(15));

            // Prey Section Card
            add(createSectionCard("Proies", new Color(34, 139, 34), "prey"));
            add(Box.createVerticalStrut(15));

            // Predator Section Card
            add(createSectionCard("Prédateurs", new Color(220, 20, 60), "pred"));
            add(Box.createVerticalStrut(15));

            // Food Section Card
            add(createSectionCard("Nourriture", new Color(255, 165, 0), "food"));
            add(Box.createVerticalStrut(20));

            // Apply button
            JButton applyBtn = new JButton("Appliquer les changements");
            applyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            applyBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
            applyBtn.setBackground(new Color(0, 123, 255));
            applyBtn.setForeground(Color.WHITE);
            applyBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            applyBtn.setFocusPainted(false);
            applyBtn.setBorderPainted(false);
            applyBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
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
            JPanel card = new JPanel();
            card.setLayout(new GridLayout(1, 3, 10, 0)); // Grid for 3 stats
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
            valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 22)); // Large font
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

            // Header
            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            header.setBackground(Color.WHITE);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Accent bar
            JPanel bar = new JPanel();
            bar.setPreferredSize(new Dimension(4, 16));
            bar.setBackground(accentColor);
            header.add(bar);

            JLabel label = new JLabel("  " + title);
            label.setFont(new Font("Segoe UI", Font.BOLD, 14));
            label.setForeground(new Color(50, 50, 50));
            header.add(label);

            card.add(header);
            card.add(Box.createVerticalStrut(15));

            // Content
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
            JSpinner spinner = createSpinner(value, min, max, step);
            preySpinners.put(label, spinner);
            panel.add(createParameterRow(label, spinner));
            panel.add(Box.createVerticalStrut(8));
        }

        private void addPredParameter(JPanel panel, String label, int value, int min, int max, int step) {
            JSpinner spinner = createSpinner(value, min, max, step);
            predSpinners.put(label, spinner);
            panel.add(createParameterRow(label, spinner));
            panel.add(Box.createVerticalStrut(8));
        }

        private void addFoodParameter(JPanel panel, String label, int value, int min, int max, int step) {
            JSpinner spinner = createSpinner(value, min, max, step);
            foodSpinners.put(label, spinner);
            panel.add(createParameterRow(label, spinner));
            panel.add(Box.createVerticalStrut(8));
        }

        private JSpinner createSpinner(int value, int min, int max, int step) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
            spinner.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setHorizontalAlignment(JTextField.CENTER);
            return spinner;
        }

        private JPanel createParameterRow(String label, JSpinner spinner) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setBackground(Color.WHITE);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel lbl = new JLabel(label);
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lbl.setForeground(new Color(80, 80, 80));
            row.add(lbl, BorderLayout.CENTER);

            spinner.setPreferredSize(new Dimension(70, 28));
            row.add(spinner, BorderLayout.EAST);
            return row;
        }

        private void applyParameters() {
            try {
                // Prey parameters
                SimParams.PREY_ENERGY_START = (Integer) preySpinners.get("Énergie initiale").getValue();
                SimParams.PREY_ENERGY_MAX = (Integer) preySpinners.get("Énergie max").getValue();
                SimParams.PREY_REPRO_THRESHOLD = (Integer) preySpinners.get("Seuil reprod.").getValue();
                SimParams.PREY_REPRO_COST = (Integer) preySpinners.get("Coût reprod.").getValue();

                // Predator parameters
                SimParams.PRED_ENERGY_START = (Integer) predSpinners.get("Énergie initiale").getValue();
                SimParams.PRED_ENERGY_MAX = (Integer) predSpinners.get("Énergie max").getValue();
                SimParams.PRED_ENERGY_GAIN = (Integer) predSpinners.get("Gain capture").getValue();
                SimParams.PRED_REPRO_THRESHOLD = (Integer) predSpinners.get("Seuil reprod.").getValue();
                SimParams.PRED_REPRO_COST = (Integer) predSpinners.get("Coût reprod.").getValue();

                // Food parameters
                SimParams.FOOD_ENERGY_VALUE = (Integer) foodSpinners.get("Valeur énerg.").getValue();
                SimParams.FOOD_SPAWN_RATE = (Integer) foodSpinners.get("Taux spawn").getValue();
                SimParams.FOOD_PER_SPAWN = (Integer) foodSpinners.get("Qté par spawn").getValue();

                JOptionPane.showMessageDialog(this, "Paramètres appliqués avec succès!", "Succès", JOptionPane.INFORMATION_MESSAGE);
                System.out.println("Parameters updated successfully");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ==========================================
    // MODERN CONTROL PANEL
    // ==========================================
    class ControlPanel extends JPanel {
        private JLabel statusLabel;
        private JSpinner preySpinner, predatorSpinner;
        private JButton startBtn, pauseBtn;

        public ControlPanel() {
            setLayout(new BorderLayout());
            setBackground(Color.WHITE);
            setBorder(new EmptyBorder(10, 20, 10, 20));

            // Left - Status & Config
            JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
            leftPanel.setBackground(Color.WHITE);

            statusLabel = new JLabel("PRÊT");
            statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            statusLabel.setForeground(new Color(108, 117, 125));
            leftPanel.add(statusLabel);
            leftPanel.add(createSeparator());

            leftPanel.add(createLabel("Proies (init):"));
            preySpinner = createSpinner(15, 0, 100);
            leftPanel.add(preySpinner);

            leftPanel.add(createLabel("Prédateurs (init):"));
            predatorSpinner = createSpinner(8, 0, 50);
            leftPanel.add(predatorSpinner);

            add(leftPanel, BorderLayout.WEST);

            // Center - Main Controls
            JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            centerPanel.setBackground(Color.WHITE);

            startBtn = createModernButton("Démarrer", new Color(40, 167, 69));
            pauseBtn = createModernButton("Pause", new Color(220, 53, 69));
            JButton restartBtn = createModernButton("Redémarrer", new Color(0, 123, 255));

            pauseBtn.setEnabled(false);

            startBtn.addActionListener(e -> {
                if (environment.getPreyCount() == 0 && environment.getPredatorCount() == 0) {
                    spawnInitialPopulation();
                }
                startSimulation();
                startBtn.setEnabled(false);
                pauseBtn.setEnabled(true);
                preySpinner.setEnabled(false);
                predatorSpinner.setEnabled(false);
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
                for (jade.core.AID aid : new java.util.HashSet<>(environment.getAllAgents().keySet())) {
                    environment.unregisterAgent(aid);
                }
                environment.getAllFoods().clear();
                startBtn.setEnabled(true);
                pauseBtn.setEnabled(false);
                preySpinner.setEnabled(true);
                predatorSpinner.setEnabled(true);
                statusLabel.setText("PRÊT");
                statusLabel.setForeground(new Color(108, 117, 125));
                if (panel != null) panel.repaint();
                if (chart != null) chart.updateData(0, 0);
                // Reset live stats to 0 via update
                if (parameterPanel != null) parameterPanel.updateLiveStats(0, 0, 0);
            });

            centerPanel.add(startBtn);
            centerPanel.add(pauseBtn);
            centerPanel.add(restartBtn);
            add(centerPanel, BorderLayout.CENTER);

            // Right - Quick Actions (Cleaned up)
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
            rightPanel.setBackground(Color.WHITE);

            JButton addPreyBtn = createQuickButton("+ Proie", new Color(34, 139, 34));
            addPreyBtn.addActionListener(e -> spawnSingleAgent("PreyAgent", "Prey"));
            rightPanel.add(addPreyBtn);

            JButton addPredatorBtn = createQuickButton("+ Prédateur", new Color(220, 20, 60));
            addPredatorBtn.addActionListener(e -> spawnSingleAgent("PredatorAgent", "Predator"));
            rightPanel.add(addPredatorBtn);

            JButton spawnFoodBtn = createQuickButton("+ Nourriture", new Color(255, 193, 7));
            spawnFoodBtn.setForeground(Color.BLACK);
            spawnFoodBtn.addActionListener(e -> {
                for(int i=0; i<5; i++) {
                    double x = 50 + Math.random() * (environment.getWidth() - 100);
                    double y = 50 + Math.random() * (environment.getHeight() - 100);
                    environment.spawnFood(new Position(x, y));
                }
            });
            rightPanel.add(spawnFoodBtn);

            add(rightPanel, BorderLayout.EAST);
        }

        private JLabel createLabel(String text) {
            JLabel l = new JLabel(text);
            l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            return l;
        }

        private JSpinner createSpinner(int val, int min, int max) {
            JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, 1));
            s.setPreferredSize(new Dimension(60, 28));
            s.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            return s;
        }

        private JSeparator createSeparator() {
            JSeparator s = new JSeparator(SwingConstants.VERTICAL);
            s.setPreferredSize(new Dimension(1, 24));
            s.setForeground(new Color(220, 220, 220));
            return s;
        }

        private JButton createModernButton(String text, Color bg) {
            JButton btn = new JButton(text) {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (getModel().isPressed()) {
                        g2.setColor(bg.darker());
                    } else {
                        g2.setColor(bg);
                    }
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btn.setBackground(bg);
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setOpaque(false);
            btn.setPreferredSize(new Dimension(110, 34));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return btn;
        }

        private JButton createQuickButton(String text, Color bg) {
            JButton btn = new JButton(text) {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (getModel().isPressed()) {
                        g2.setColor(bg.darker());
                    } else {
                        g2.setColor(bg);
                    }
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btn.setBackground(bg);
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setOpaque(false);
            btn.setPreferredSize(new Dimension(95, 30));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return btn;
        }

        private void spawnInitialPopulation() {
            int preyCount = (Integer) preySpinner.getValue();
            int predatorCount = (Integer) predatorSpinner.getValue();
            for (int i = 0; i < preyCount; i++) spawnSingleAgent("PreyAgent", "Prey");
            for (int i = 0; i < predatorCount; i++) spawnSingleAgent("PredatorAgent", "Predator");
        }

        private void spawnSingleAgent(String className, String prefix) {
            VisualizerAgent.this.spawnAgent(className, prefix);
        }
    }

    // ==========================================
    // SIMULATION PANEL
    // ==========================================
    class SimulationPanel extends JPanel {
        public SimulationPanel() {
            setPreferredSize(new Dimension(900, 650));
            setBackground(new Color(245, 240, 235)); // Warmer "Paper" map color
            setBorder(null);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // seasonal background
            Color bgColor = new Color(245, 240, 235); // Default Spring/Summer
            switch (environment.getCurrentSeason()) {
                case AUTUMN: bgColor = new Color(250, 235, 220); break; // Warmer/Orange tint
                case WINTER: bgColor = new Color(240, 245, 250); break; // Cooler/Blue tint
            }
            setBackground(bgColor);

            // draw terrain
            // 1. swamps (organic blobs)
            g2d.setColor(new Color(101, 67, 33, 100)); // Semi-transparent brown
            g2d.setStroke(new BasicStroke(2f));
            for (Shape swamp : environment.getSwamps()) {
                g2d.fill(swamp);
                g2d.setColor(new Color(80, 50, 20, 150)); // Darker outline
                g2d.draw(swamp);
                g2d.setColor(new Color(101, 67, 33, 100)); // Reset fill color
            }

            // 2. rocks (natural polygons with 3d effect)
            for (Shape rock : environment.getRocks()) {
                // Drop shadow
                g2d.setColor(new Color(0, 0, 0, 40));
                g2d.translate(3, 3);
                g2d.fill(rock);
                g2d.translate(-3, -3);

                // Main rock body
                g2d.setColor(new Color(128, 128, 128));
                g2d.fill(rock);
                
                // Highlight edge
                g2d.setColor(new Color(160, 160, 160));
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.draw(rock);
            }

            // 3. forest clusters (organic trees)
            // change tree color based on season
            Color treeColor = new Color(34, 139, 34, 200); // Spring Green
            switch (environment.getCurrentSeason()) {
                case SUMMER: treeColor = new Color(20, 100, 20, 200); break; // Deep Green
                case AUTUMN: treeColor = new Color(200, 100, 20, 200); break; // Orange/Red
                case WINTER: treeColor = new Color(200, 220, 220, 200); break; // Snowy White
            }

            for (Shape tree : environment.getTrees()) {
                // Tree shadow
                g2d.setColor(new Color(20, 60, 20, 40));
                g2d.translate(2, 2);
                g2d.fill(tree);
                g2d.translate(-2, -2);

                // Tree canopy
                g2d.setColor(treeColor);
                g2d.fill(tree);
                
                // Subtle outline
                g2d.setColor(new Color(20, 80, 20, 100));
                g2d.setStroke(new BasicStroke(1f));
                g2d.draw(tree);
            }

            Map<jade.core.AID, AgentInfo> agents = environment.getAllAgents();
            List<Food> foods = environment.getAllFoods();

            // Draw food with glow effect
            for (Food food : foods) {
                Position pos = food.getPosition();
                // Outer glow
                g2d.setColor(new Color(255, 220, 0, 50));
                g2d.fill(new Ellipse2D.Double(pos.getX() - 8, pos.getY() - 8, 16, 16));
                // Inner circle
                g2d.setColor(new Color(255, 193, 7));
                g2d.fill(new Ellipse2D.Double(pos.getX() - 5, pos.getY() - 5, 10, 10));
            }

            // Draw agents with shadows
            for (AgentInfo info : agents.values()) {
                Position pos = info.getPosition();
                if (info.isPrey()) {
                    // Shadow
                    g2d.setColor(new Color(0, 0, 0, 30));
                    g2d.fill(new Ellipse2D.Double(pos.getX() - 5, pos.getY() - 4, 10, 10));
                    // Agent
                    g2d.setColor(new Color(40, 167, 69));
                    g2d.fill(new Ellipse2D.Double(pos.getX() - 6, pos.getY() - 6, 12, 12));
                    // Highlight
                    g2d.setColor(new Color(255, 255, 255, 100));
                    g2d.fill(new Ellipse2D.Double(pos.getX() - 4, pos.getY() - 5, 4, 4));
                } else {
                    // Shadow
                    g2d.setColor(new Color(0, 0, 0, 40));
                    g2d.fill(new Ellipse2D.Double(pos.getX() - 7, pos.getY() - 6, 14, 14));
                    // Agent
                    g2d.setColor(new Color(220, 53, 69));
                    g2d.fill(new Ellipse2D.Double(pos.getX() - 8, pos.getY() - 8, 16, 16));
                    // Highlight
                    g2d.setColor(new Color(255, 255, 255, 120));
                    g2d.fill(new Ellipse2D.Double(pos.getX() - 5, pos.getY() - 6, 5, 5));
                }
            }
            
                // draw season indicator
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
            // Border handled by wrapper
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

            if (preyHistory.isEmpty()) {
                g2d.setColor(new Color(150, 150, 150));
                g2d.setFont(new Font("Segoe UI", Font.ITALIC, 14));
                g2d.drawString("En attente de données...", getWidth() / 2 - 80, getHeight() / 2);
                return;
            }

            int width = getWidth();
            int height = getHeight();
            int padding = 40; // Reduced padding

            int maxPop = Math.max(
                    preyHistory.stream().max(Integer::compareTo).orElse(1),
                    predatorHistory.stream().max(Integer::compareTo).orElse(1)
            );
            maxPop = Math.max(maxPop, 10);

            // Clean Grid
            g2d.setColor(new Color(245, 245, 245));
            for (int i = 0; i <= 5; i++) {
                int y = padding + i * (height - 2 * padding) / 5;
                g2d.drawLine(padding, y, width - padding, y);
            }

            // Axes
            g2d.setColor(new Color(200, 200, 200));
            g2d.setStroke(new BasicStroke(1));
            g2d.drawLine(padding, padding, padding, height - padding);
            g2d.drawLine(padding, height - padding, width - padding, height - padding);

            // Labels
            g2d.setColor(new Color(120, 120, 120));
            g2d.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2d.drawString("0", padding - 15, height - padding + 5);
            g2d.drawString(String.valueOf(maxPop), padding - 25, padding + 5);

            double xScale = (double) (width - 2 * padding) / MAX_POINTS;
            double yScale = (double) (height - 2 * padding) / maxPop;

            // Draw prey line (Green)
            g2d.setColor(new Color(40, 167, 69));
            g2d.setStroke(new BasicStroke(2f)); // Thinner, sharper line
            drawCurve(g2d, preyHistory, xScale, yScale, padding, height);

            // Draw predator line (Red)
            g2d.setColor(new Color(220, 53, 69));
            drawCurve(g2d, predatorHistory, xScale, yScale, padding, height);

            // Legend
            int legendX = width - 140;
            int legendY = 20;

            drawLegendItem(g2d, legendX, legendY, new Color(40, 167, 69), "Proies");
            drawLegendItem(g2d, legendX, legendY + 20, new Color(220, 53, 69), "Prédateurs");
        }

        private void drawLegendItem(Graphics2D g2d, int x, int y, Color c, String text) {
            g2d.setColor(c);
            g2d.fillOval(x, y, 10, 10);
            g2d.setColor(Color.DARK_GRAY);
            g2d.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2d.drawString(text, x + 15, y + 9);
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
