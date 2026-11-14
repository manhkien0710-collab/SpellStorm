package com.spellstorm.combat;

/**
 * Defines projectile parameters for a spell relative to the caster's baseline attributes.
 */
public class ProjectileProfile {

    public float damageMultiplier = 1.0f;
    public float speedMultiplier = 1.0f;
    public float radius = 7.0f;
    public float lifetime = 1.6f;
    public float cooldownMultiplier = 1.0f;
    public float spreadDegrees = 12.0f;
    public int pierce = 0;
    public int extraProjectiles = 0;

    public ProjectileProfile() {
    }

    public ProjectileProfile(float damageMultiplier, float speedMultiplier, float radius,
                             float lifetime, float cooldownMultiplier, float spreadDegrees,
                             int pierce, int extraProjectiles) {
        this.damageMultiplier = damageMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.radius = radius;
        this.lifetime = lifetime;
        this.cooldownMultiplier = cooldownMultiplier;
        this.spreadDegrees = spreadDegrees;
        this.pierce = pierce;
        this.extraProjectiles = extraProjectiles;
    }

    public float getDamageMultiplier() { return damageMultiplier; }
    public float getSpeedMultiplier() { return speedMultiplier; }
    public float getRadius() { return radius; }
    public float getLifetime() { return lifetime; }
    public float getCooldownMultiplier() { return cooldownMultiplier; }
    public float getSpreadDegrees() { return spreadDegrees; }
    public int getPierce() { return pierce; }
    public int getExtraProjectiles() { return extraProjectiles; }
}
