# Predator–Prey Multi-Agent Simulation

> JADE-based ecological simulation with prey, predators, terrain, and real-time visualization

[![Java](https://img.shields.io/badge/Java-17+-orange)](https://openjdk.org/)
[![JADE](https://img.shields.io/badge/JADE-Multi--Agent-blue)](http://jade.tilab.com/)

## Project Overview

This project is a **predator–prey multi-agent simulation** built with the **JADE** (Java Agent DEvelopment Framework) platform. Autonomous prey and predator agents live in a 2D environment with terrain (forests, swamps, rocks), seasonal cycles, and food. The simulation includes a Swing GUI for real-time visualization, population charts, configurable parameters, and an optional **Lotka–Volterra** theoretical comparison window.

### About

- **Multi-agent systems**: Each prey and predator is an independent JADE agent with its own behaviour cycle.
- **Emergent behaviour**: Flocking (prey), hunting states (predators), reproduction with genetic variation (speed, vision), and death statistics emerge from local rules.
- **Realistic ecology**: Terrain affects movement (swamps slow agents, forests hide prey), seasons change over time, and food spawns for prey.
- **Comparison with theory**: The built-in Lotka–Volterra comparator shows the classic ODE model alongside the agent-based dynamics.

---

## Project Context

The simulation is structured around a shared environment and three main agent types:

| Component        | Role |
|-----------------|------|
| **Environment** | Singleton world (800×600): spatial grid, terrain, food, seasons, death stats |
| **PreyAgent**   | Eat food, flee predators, flock, reproduce; die from starvation, hunting, or old age |
| **PredatorAgent** | Scouting / Hunting / Resting; stamina; hunt prey, reproduce; die from starvation |
| **VisualizerAgent** | GUI: simulation view, controls, population chart, parameters, stats, agent inspector |
| **LotkaVolterraComparator** | Separate window: theoretical prey/predator ODE curves |

**Main entry point:** `SimulationLauncher` — starts the JADE main container and only the Visualizer; you set initial populations and press *Démarrer* (Start) to spawn agents.

---

## Screenshots



---

## Key Features

### Prey Behaviour
- **Perception**: Vision range; detect nearby predators and prey.
- **Flee**: Move away from predator centroid; use extra speed at cost of stamina.
- **Flocking**: Separation and cohesion with nearby prey when no predator and no food target.
- **Foraging**: Seek and consume food; higher speed when low energy.
- **Reproduction**: When energy above threshold and cooldown allows; offspring inherit speed/vision with random variation.
- **Death**: Starvation (energy ≤ 0), hunted (ACL message from predator), or old age (max age).

### Predator Behaviour
- **State machine**: Scouting (wander, optional cooperation), Hunting (chase nearest prey), Resting (recover stamina, e.g. in swamp).
- **Stamina**: Drains while hunting; must rest to hunt again; swamp increases drain.
- **Hunting**: Vision-based target selection; forests reduce effective visibility; catch distance for “eating” prey.
- **Reproduction**: When energy above threshold; offspring get genetic speed/vision variation.
- **Crowding**: Disperse when too many nearby predators.

### Environment
- **Terrain**: Forests (prey hiding), swamps (slower movement, stamina effects), rocks (obstacles).
- **Seasons**: Spring / Summer / Autumn / Winter with configurable duration.
- **Food**: Spawned at positions; prey consume within range; energy value per food.
- **Spatial grid**: Efficient nearby-agent and collision queries.
- **Death statistics**: Prey (hunted, starved, old age); predators (starved).

### GUI (VisualizerAgent)
- **Simulation panel**: 2D view of agents, terrain, and food.
- **Control panel**: Start / Stop, initial prey/predator counts, spawn settings.
- **Population chart**: Prey vs predator counts over time.
- **Parameter panel**: Energy, reproduction, speed, food spawn, etc.
- **Stats panel**: Current counts and death breakdown.
- **Agent inspector**: Click an agent to see type, position, energy, speed, vision.
- **Lotka–Volterra**: Button to open the theoretical ODE comparison window.

---

## Technical Stack

### Core
```
Java 21+
├── JADE (Agent platform)
├── Swing (GUI)
└── AWT / 2D (rendering, shapes)
```

### Project layout
```
src/
├── SimulationLauncher.java   ← main(String[]) — start here
├── VisualizerAgent.java      ← GUI + SimParams
├── PredatorAgent.java
├── PreyAgent.java
├── Environment.java          ← shared world singleton
├── AgentInfo.java
├── Position.java
├── Food.java
└── LotkaVolterraComparator.java  ← optional ODE comparison
```

### Architecture highlights
- **JADE behaviours**: Prey and predators use `CyclicBehaviour`; visualizer uses `TickerBehaviour` for repaints and updates.
- **Shared state**: `Environment.getInstance()` holds all agents, food, terrain, and stats; thread-safe updates (e.g. `synchronized` / concurrent collections).
- **Communication**: Predators send ACL `REQUEST` with content `"DIE"` to prey on capture; no other message protocols required for core loop.
- **Spatial partitioning**: Grid cells for `getNearbyAgents` and collision checks.

---



## Lotka–Volterra Comparator

`LotkaVolterraComparator` is a standalone Swing window that numerically integrates the classic Lotka–Volterra equations:

- **Prey (X)**: growth rate α, mortality from predation β·X·Y  
- **Predator (Y)**: growth from predation δ·X·Y, death rate γ  

Initial conditions (X₀, Y₀) and parameters (α, β, γ, δ) are fixed in the class. The chart plots prey and predator populations over time so you can compare the smooth ODE behaviour with the stochastic, spatial agent-based simulation.

---

*Built with JADE and Java to explore emergent ecology and multi-agent systems.*
