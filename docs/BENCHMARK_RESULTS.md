# Milestone 4 Performance & Memory Benchmark Report

**Milestone**: Phase C Playable Prototype & Gate 2 Delivery  
**Timestamp**: 2026-11-14T23:50:00Z  
**Branch**: `feature/infinite-world`  
**Resolution**: 1280x720 (LibGDX FitViewport @ 60 FPS target)  
**Test Class**: `com.spellstorm.benchmark.InfiniteWorldPerformanceBenchmarkTest`  

## 1. Executive Summary

| Metric | Target Budget | Measured Value | Headroom / Margin | Gate 2 Status |
|---|---|---|---|---|
| **Average Frame Time** | < 16.67 ms (60 FPS) | **0.046 ms** (21737.8 FPS) | **+99.7%** | **PASS** |
| **95th Percentile (P95)** | < 14.00 ms | **0.073 ms** | **+99.5%** | **PASS** |
| **99th Percentile (P99)** | < 16.60 ms | **0.179 ms** | **+98.9%** | **PASS** |
| **Resident Heap Memory** | < 60.0 MB | **8.77 MB** | **+51.2 MB** | **PASS** |
| **Active Chunk Cache Size** | <= 64 chunks | **64 chunks** | Within capacity | **PASS** |
| **Exploration Distance** | >= 20 chunks | **20 chunks** | Multi-quadrant | **PASS** |
| **Floating Origin Rebases** | >= 5 shifts | **19 shifts** | Zero-glitch shifts | **PASS** |

## 2. Latency Distribution Percentiles (1,800 Frames / 30.0 Seconds)

| Percentile | Frame Duration (ms) | Equivalent FPS |
|---|---|---|
| Minimum (Best) | 0.012 ms | 86423.0 FPS |
| P50 (Median) | 0.024 ms | 42295.8 FPS |
| P90 | 0.066 ms | 15039.2 FPS |
| P95 (Upper SLA) | 0.073 ms | 13754.4 FPS |
| P99 (Tail Latency) | 0.179 ms | 5601.0 FPS |
| Maximum (Worst) | 2.273 ms | 439.9 FPS |

## 3. Workload Configuration

- **Resolution & Viewport**: 1280x720, GameCamera zoom 1.35x
- **Simulation Duration**: 1,800 continuous frames (30.0 seconds simulated time at dt = 16.667 ms)
- **Entities & Spawning**: 1 Player, 25 active combat enemies (Melee & Ranged) with AI navigation and crowd separation
- **Combat Load**: Continuous spell casting, projectile simulation and collision culling
- **Spatial Partitioning**: Two-tier chunk-local obstacle spatial grids, camera frustum queries, and Y-depth sorting
- **Floating-Origin Shifts**: Centralized 1024px chunk-span rebase shifts dispatched across all simulation components

## 4. System Environment

- **OS**: Linux (amd64)
- **JVM**: Java 21.0.13 (Eclipse Adoptium)
- **Framework**: LibGDX 1.13.1 (Headless Mock GL20 Context)

## 5. Verification Command

```bash
./gradlew test --tests "com.spellstorm.benchmark.InfiniteWorldPerformanceBenchmarkTest" --console=plain
```
