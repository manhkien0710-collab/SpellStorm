package com.spellstorm.combat;

import com.spellstorm.entities.Enemy;
import com.spellstorm.math.Vector2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightning on-hit effect: jumps up to N times to nearby living enemies within range,
 * applying child effects to each jumped target.
 */
public class ChainEffect implements HitEffect {

    private final int jumps;
    private final float range;
    private final List<HitEffect> onEachTarget = new ArrayList<>();

    public ChainEffect(int jumps, float range) {
        this.jumps = Math.max(0, jumps);
        this.range = Math.max(0.0f, range);
    }

    public ChainEffect then(HitEffect effect) {
        if (effect != null) {
            onEachTarget.add(effect);
        }
        return this;
    }

    public int getJumps() {
        return jumps;
    }

    public float getRange() {
        return range;
    }

    public List<HitEffect> getOnEachTarget() {
        return onEachTarget;
    }

    @Override
    public void apply(HitContext context) {
        if (context == null || context.target == null || context.services == null) {
            return;
        }

        List<Integer> visited = new ArrayList<>();
        visited.add(context.target.getId());
        Vector2D from = context.target.getPosition().cpy();

        for (int jump = 0; jump < jumps; jump++) {
            Enemy next = context.services.findNearestEnemy(from, range, visited);
            if (next == null || !next.isAlive()) {
                break;
            }

            visited.add(next.getId());
            Vector2D to = next.getPosition().cpy();
            Vector2D dir = Vector2D.sub(to, from);
            if (dir.len2() > 1e-4f) {
                dir.nor();
            } else {
                dir.set(1.0f, 0.0f);
            }
            from.set(to);

            HitContext jumpContext = new HitContext(next, to, dir, context.baseDamage, context.color, context.services);
            for (HitEffect effect : onEachTarget) {
                if (!next.isAlive()) {
                    break;
                }
                effect.apply(jumpContext);
            }
        }
    }
}
