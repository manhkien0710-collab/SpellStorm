package com.spellstorm.systems;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.entities.TankEnemy;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.TileMap;

import java.util.List;
import java.util.Random;

/**
 * Manages procedural enemy spawning in the arena over match progression.
 * Features:
 * - Deterministic pseudo-random placement.
 * - Dynamic difficulty scaling across match progression (0 to 120s or up to 15 min).
 * - Spawn interval tightening (2.2s -> 0.55s).
 * - Dynamic active enemy caps (20 -> 36).
 * - Camera-aware spawn ring placement outside player view with a safety margin (default 450-800px).
 * - Multi-phase traversability collision validation with TileMap (avoiding water and solid obstacles).
 */
public class EnemySpawner {

    public static final float DEFAULT_BASE_INTERVAL = 2.2f;
    public static final float DEFAULT_MIN_INTERVAL = 0.55f;
    public static final int DEFAULT_BASE_MAX_ENEMIES = 20;
    public static final int DEFAULT_MAX_ENEMIES_CAP = 36;
    public static final float DEFAULT_SCALING_DURATION = 120.0f; // 2 min (supports 900.0f / 15 min)

    private final TileMap tileMap;
    private final Random rng;
    private BalanceConfig balanceConfig;
    private GameCamera camera;
    private Rectangle bounds;

    private float elapsedTime = 0.0f;
    private float spawnTimer = 1.5f; // Initial grace period before first spawn
    private float bossSpawnTimer = 60.0f; // Boss milestone encounter hook
    private boolean initialWaveSpawned = false;
    private float scalingDuration = DEFAULT_SCALING_DURATION;

    private float baseInterval = DEFAULT_BASE_INTERVAL;
    private float minInterval = DEFAULT_MIN_INTERVAL;
    private float currentInterval = DEFAULT_BASE_INTERVAL;

    private int baseMaxEnemies = DEFAULT_BASE_MAX_ENEMIES;
    private int maxEnemiesCap = DEFAULT_MAX_ENEMIES_CAP;
    private int maxEnemies = DEFAULT_BASE_MAX_ENEMIES;

    // =========================================================================
    // Constructors
    // =========================================================================

    public EnemySpawner(TileMap tileMap, Random rng, BalanceConfig balanceConfig) {
        this(tileMap, rng);
        setBalanceConfig(balanceConfig);
    }

    public EnemySpawner(TileMap tileMap, Random rng) {
        this.tileMap = tileMap;
        this.rng = rng != null ? rng : new Random();
        this.bounds = tileMap != null ? tileMap.getWorldBounds() : new Rectangle(0, 0, 4096, 4096);
    }

    public EnemySpawner(TileMap tileMap) {
        this(tileMap, new Random());
    }

    public EnemySpawner() {
        this(null, new Random());
    }

    // =========================================================================
    // Configuration & Telemetry
    // =========================================================================

    public void setBalanceConfig(BalanceConfig config) {
        this.balanceConfig = config;
        if (config != null) {
            this.baseInterval = config.getBaseSpawnInterval();
            this.minInterval = config.getMinSpawnInterval();
            this.baseMaxEnemies = config.getBaseMaxEnemies();
            this.maxEnemiesCap = config.getMaxEnemiesCap();
            this.scalingDuration = config.getRampDurationSeconds();
            if (this.elapsedTime == 0.0f) {
                this.spawnTimer = config.getInitialSpawnDelay();
                this.currentInterval = config.computeSpawnInterval(0.0f);
                this.maxEnemies = config.computeMaxEnemies(0.0f);
                this.initialWaveSpawned = false;
            } else {
                this.currentInterval = config.computeSpawnInterval(this.elapsedTime);
                this.maxEnemies = config.computeMaxEnemies(this.elapsedTime);
            }
        } else {
            this.baseInterval = DEFAULT_BASE_INTERVAL;
            this.minInterval = DEFAULT_MIN_INTERVAL;
            this.baseMaxEnemies = DEFAULT_BASE_MAX_ENEMIES;
            this.maxEnemiesCap = DEFAULT_MAX_ENEMIES_CAP;
            this.scalingDuration = DEFAULT_SCALING_DURATION;
            if (this.elapsedTime == 0.0f) {
                this.spawnTimer = 1.5f;
                this.currentInterval = this.baseInterval;
                this.maxEnemies = this.baseMaxEnemies;
                this.initialWaveSpawned = false;
            }
        }
    }

    public BalanceConfig getBalanceConfig() {
        return balanceConfig;
    }

    public void setCamera(GameCamera camera) {
        this.camera = camera;
    }

    public GameCamera getCamera() {
        return camera;
    }

    public void setBounds(Rectangle bounds) {
        if (bounds != null) {
            this.bounds = new Rectangle(bounds);
        }
    }

    public Rectangle getBounds() {
        return bounds != null ? new Rectangle(bounds) : new Rectangle(0, 0, 4096, 4096);
    }

    public void setMaxEnemies(int maxEnemies) {
        this.baseMaxEnemies = maxEnemies;
        this.maxEnemiesCap = maxEnemies;
        this.maxEnemies = maxEnemies;
    }

    public void setDynamicEnemyCap(int baseCap, int maxCap) {
        this.baseMaxEnemies = baseCap;
        this.maxEnemiesCap = Math.max(baseCap, maxCap);
        this.maxEnemies = baseCap;
    }

    public void setMaxEnemiesCap(int cap) {
        this.maxEnemiesCap = cap;
        if (this.maxEnemies > this.maxEnemiesCap) {
            this.maxEnemies = this.maxEnemiesCap;
        }
    }

    public int getMaxEnemies() {
        return maxEnemies;
    }

    public int maxEnemies() {
        return maxEnemies;
    }

    public int getBaseMaxEnemies() {
        return baseMaxEnemies;
    }

    public int baseMaxEnemies() {
        return baseMaxEnemies;
    }

    public int getMaxEnemiesCap() {
        return maxEnemiesCap;
    }

    public int maxEnemiesCap() {
        return maxEnemiesCap;
    }

    public void setBaseInterval(float interval) {
        this.baseInterval = Math.max(0.1f, interval);
    }

    public float getBaseInterval() {
        return baseInterval;
    }

    public float baseInterval() {
        return baseInterval;
    }

    public float getMinInterval() {
        return minInterval;
    }

    public float minInterval() {
        return minInterval;
    }

    public float getCurrentInterval() {
        return currentInterval;
    }

    public float getSpawnInterval() {
        return currentInterval;
    }

    public float currentInterval() {
        return currentInterval;
    }

    public float getElapsedTime() {
        return elapsedTime;
    }

    public float elapsedTime() {
        return elapsedTime;
    }

    public float getSpawnTimer() {
        return spawnTimer;
    }

    public float spawnTimer() {
        return spawnTimer;
    }

    public float getBossSpawnTimer() {
        return bossSpawnTimer;
    }

    public float bossSpawnTimer() {
        return bossSpawnTimer;
    }

    public void setBossSpawnTimer(float timer) {
        this.bossSpawnTimer = timer;
    }

    public float getScalingDuration() {
        return scalingDuration;
    }

    public void setScalingDuration(float duration) {
        this.scalingDuration = Math.max(1.0f, duration);
    }

    // =========================================================================
    // Spawn Ring Positioning with Camera Safety Margin
    // =========================================================================

    /**
     * Calculates the minimum spawn distance outside the camera frustum with a safety margin (50px).
     */
    public float computeMinSpawnDistance(float fallbackMinDistance) {
        if (camera != null) {
            Rectangle vis = camera.getVisibleBounds();
            float halfW = vis.width * 0.5f;
            float halfH = vis.height * 0.5f;
            float halfDiag = (float) Math.hypot(halfW, halfH);
            return Math.max(fallbackMinDistance, halfDiag + 50.0f);
        }
        return Math.max(fallbackMinDistance, 450.0f);
    }

    /**
     * Generates a valid arena position at least minDistance away from playerPos,
     * verifying traversability (free of water and solid obstacles).
     */
    public Vector2D generateSpawnPosition(Vector2D playerPos, float minDistance, float entityRadius) {
        Vector2D pPos = (playerPos != null) ? playerPos : new Vector2D(2048.0f, 2048.0f);

        // Phase 1: Engagement ring placement
        float minSpawnDist;
        float maxSpawnDist;
        int ringAttempts;
        if (balanceConfig != null) {
            minSpawnDist = balanceConfig.getMinSpawnDistance();
            maxSpawnDist = balanceConfig.getMaxSpawnDistance();
            ringAttempts = 64;
        } else {
            minSpawnDist = computeMinSpawnDistance(minDistance);
            maxSpawnDist = minSpawnDist + 350.0f;
            ringAttempts = 32;
        }

        for (int attempt = 0; attempt < ringAttempts; attempt++) {
            float angle = rng.nextFloat() * MathUtils.TWO_PI;
            float dist = minSpawnDist + rng.nextFloat() * (maxSpawnDist - minSpawnDist);
            float cx = pPos.x + (float) Math.cos(angle) * dist;
            float cy = pPos.y + (float) Math.sin(angle) * dist;

            if (tileMap != null) {
                if (tileMap.isTraversable(cx, cy, entityRadius)) {
                    return new Vector2D(cx, cy);
                }
            } else if (bounds != null && bounds.contains(cx, cy)) {
                return new Vector2D(cx, cy);
            }
        }

        // Phase 2: Global bounds random sampling (24 attempts)
        Rectangle b = (bounds != null) ? bounds : (tileMap != null ? tileMap.getWorldBounds() : new Rectangle(0, 0, 4096, 4096));
        float margin = Math.max(45.0f, entityRadius + 10.0f);
        float minX = b.x + margin;
        float maxX = b.x + b.width - margin;
        float minY = b.y + margin;
        float maxY = b.y + b.height - margin;
        float effectiveMinDist = (balanceConfig != null) ? balanceConfig.getMinSpawnDistance() : minDistance;
        float minSq = effectiveMinDist * effectiveMinDist;

        for (int attempt = 0; attempt < 24; attempt++) {
            float x = minX + rng.nextFloat() * Math.max(0.0f, maxX - minX);
            float y = minY + rng.nextFloat() * Math.max(0.0f, maxY - minY);
            float dx = x - pPos.x;
            float dy = y - pPos.y;

            if ((dx * dx + dy * dy) >= minSq) {
                if (tileMap == null || tileMap.isTraversable(x, y, entityRadius)) {
                    return new Vector2D(x, y);
                }
            }
        }

        // Phase 3: Deterministic opposite-quadrant fallback
        Vector2D center = new Vector2D(b.x + b.width * 0.5f, b.y + b.height * 0.5f);
        Vector2D offset = Vector2D.sub(center, pPos);
        if (offset.len2() < 1e-4f) {
            offset = new Vector2D(1.0f, 0.0f);
        }
        Vector2D dir = offset.nor();
        Vector2D fallbackPos = center.cpy().mulAdd(dir, Math.min(b.width, b.height) * 0.35f);
        fallbackPos.x = MathUtils.clamp(fallbackPos.x, minX, maxX);
        fallbackPos.y = MathUtils.clamp(fallbackPos.y, minY, maxY);

        if (tileMap != null && !tileMap.isTraversable(fallbackPos.x, fallbackPos.y, entityRadius)) {
            // Search radially outward (8-spoke radial spiral)
            for (float r = 32.0f; r <= 320.0f; r += 32.0f) {
                for (int a = 0; a < 8; a++) {
                    float ang = a * (MathUtils.TWO_PI / 8.0f);
                    float px = MathUtils.clamp(fallbackPos.x + (float) Math.cos(ang) * r, minX, maxX);
                    float py = MathUtils.clamp(fallbackPos.y + (float) Math.sin(ang) * r, minY, maxY);
                    if (tileMap.isTraversable(px, py, entityRadius)) {
                        return new Vector2D(px, py);
                    }
                }
            }
        }

        return fallbackPos;
    }

    public Vector2D generateSpawnPosition(Vector2D playerPos) {
        return generateSpawnPosition(playerPos, 220.0f, 16.0f);
    }

    // =========================================================================
    // Wave Generation & Scaling
    // =========================================================================

    public Enemy spawnEnemy(Vector2D playerPos, EnemyType typeOverride) {
        EnemyType chosenType = EnemyType.MELEE;

        if (typeOverride != null) {
            chosenType = typeOverride;
        } else {
            float roll = rng.nextFloat();
            if (elapsedTime < 25.0f) {
                // Early phase: 75% Melee, 25% Ranged
                chosenType = (roll < 0.75f) ? EnemyType.MELEE : EnemyType.RANGED;
            } else if (elapsedTime < 70.0f) {
                // Mid phase: 45% Melee, 40% Ranged, 15% Tank
                if (roll < 0.45f) {
                    chosenType = EnemyType.MELEE;
                } else if (roll < 0.85f) {
                    chosenType = EnemyType.RANGED;
                } else {
                    chosenType = EnemyType.TANK;
                }
            } else {
                // Late phase: 35% Melee, 45% Ranged, 20% Tank
                if (roll < 0.35f) {
                    chosenType = EnemyType.MELEE;
                } else if (roll < 0.80f) {
                    chosenType = EnemyType.RANGED;
                } else {
                    chosenType = EnemyType.TANK;
                }
            }
        }

        float entityRadius = chosenType.getDefaultRadius();
        Vector2D spawnPos = generateSpawnPosition(playerPos, 220.0f, entityRadius);

        Enemy enemy;
        switch (chosenType) {
            case RANGED -> enemy = new RangedEnemy(spawnPos);
            case TANK -> enemy = new TankEnemy(spawnPos);
            case BOSS -> enemy = new BossEnemy(spawnPos);
            default -> enemy = new MeleeEnemy(spawnPos);
        }

        // Controlled, gradual health scaling for late-game challenge (max +25% after 50s, exempt for boss)
        if (elapsedTime > 50.0f && !enemy.isBoss()) {
            float hpScale = 1.0f + MathUtils.clamp((elapsedTime - 50.0f) / 600.0f, 0.0f, 0.25f);
            enemy.scaleMaxHealth(hpScale);
        }

        if (balanceConfig != null) {
            balanceConfig.applyToEnemy(enemy);
        }

        return enemy;
    }

    public Enemy spawnEnemy(Vector2D playerPos) {
        return spawnEnemy(playerPos, null);
    }

    /**
     * Spawns a BossEnemy at an off-screen traversable location at least 350px away from the player.
     *
     * @param playerPos current player position
     * @return newly instantiated BossEnemy
     */
    public Enemy spawnBoss(Vector2D playerPos) {
        float minDist = (balanceConfig != null) ? balanceConfig.getMinSpawnDistance() : 350.0f;
        Vector2D spawnPos = generateSpawnPosition(playerPos, minDist, BossEnemy.BOSS_RADIUS);
        BossEnemy boss = new BossEnemy(spawnPos);
        if (balanceConfig != null) {
            balanceConfig.applyToEnemy(boss);
        }
        return boss;
    }

    // =========================================================================
    // Simulation Lifecycle
    // =========================================================================

    public void update(float dt, Vector2D playerPos, List<Enemy> activeEnemies) {
        elapsedTime += dt;

        if (balanceConfig != null) {
            currentInterval = balanceConfig.computeSpawnInterval(elapsedTime);
            maxEnemies = balanceConfig.computeMaxEnemies(elapsedTime);
        } else {
            // Difficulty scaling: spawn interval gradually tightens from base to min
            float progress = MathUtils.clamp(elapsedTime / scalingDuration, 0.0f, 1.0f);
            currentInterval = baseInterval - progress * (baseInterval - minInterval);

            // Active enemy cap scales progressively from baseMaxEnemies up to maxEnemiesCap
            if (maxEnemiesCap > baseMaxEnemies) {
                maxEnemies = (int) (baseMaxEnemies + progress * (maxEnemiesCap - baseMaxEnemies));
            } else {
                maxEnemies = baseMaxEnemies;
            }
        }

        spawnTimer -= dt;

        if (spawnTimer <= 0.0f) {
            spawnTimer = currentInterval;

            int spawnCount;
            if (balanceConfig != null && !initialWaveSpawned) {
                initialWaveSpawned = true;
                int minWave = balanceConfig.getInitialWaveMin();
                int maxWave = Math.max(minWave, balanceConfig.getInitialWaveMax());
                spawnCount = minWave + rng.nextInt(maxWave - minWave + 1);
            } else {
                // In late game, occasionally spawn a pair of enemies
                spawnCount = (elapsedTime > 60.0f && rng.nextFloat() < 0.35f) ? 2 : 1;
            }

            if (activeEnemies != null) {
                for (int i = 0; i < spawnCount; i++) {
                    if (activeEnemies.size() < maxEnemies) {
                        Enemy enemy = spawnEnemy(playerPos, null);
                        if (enemy != null) {
                            activeEnemies.add(enemy);
                        }
                    }
                }
            }
        }

        // Periodic Boss encounter: spawns at 60s milestone if no boss is active
        bossSpawnTimer -= dt;
        if (bossSpawnTimer <= 0.0f && activeEnemies != null) {
            boolean bossPresent = false;
            for (Enemy e : activeEnemies) {
                if (e != null && e.isAlive() && e.isBoss()) {
                    bossPresent = true;
                    break;
                }
            }
            if (!bossPresent) {
                activeEnemies.add(spawnBoss(playerPos));
                bossSpawnTimer = 90.0f; // Next boss wave in 90 seconds
            } else {
                bossSpawnTimer = 10.0f; // Retry check in 10s if previous boss is still fighting
            }
        }
    }

    public void reset() {
        elapsedTime = 0.0f;
        bossSpawnTimer = 60.0f;
        initialWaveSpawned = false;
        if (balanceConfig != null) {
            spawnTimer = balanceConfig.getInitialSpawnDelay();
            baseInterval = balanceConfig.getBaseSpawnInterval();
            minInterval = balanceConfig.getMinSpawnInterval();
            baseMaxEnemies = balanceConfig.getBaseMaxEnemies();
            maxEnemiesCap = balanceConfig.getMaxEnemiesCap();
            scalingDuration = balanceConfig.getRampDurationSeconds();
            currentInterval = balanceConfig.computeSpawnInterval(0.0f);
            maxEnemies = balanceConfig.computeMaxEnemies(0.0f);
        } else {
            spawnTimer = 1.5f;
            currentInterval = baseInterval;
            maxEnemies = baseMaxEnemies;
        }
    }
}
