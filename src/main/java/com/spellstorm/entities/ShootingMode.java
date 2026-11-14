package com.spellstorm.entities;

/**
 * Defines the player's weapon aiming and firing control mode.
 */
public enum ShootingMode {
    /**
     * Manual Mode: Spells are cast only when the player clicks or holds
     * the Left Mouse Button (LMB). Aim direction tracks the world-space mouse cursor.
     */
    MANUAL("Manual", "MANUAL SHOOTING ACTIVATED [V]", 0.39f, 0.78f, 1.0f),

    /**
     * Auto Mode: Spells are cast automatically as soon as the attack cooldown expires.
     * Automatically acquires and aims at the nearest living enemy, or tracks the mouse
     * cursor if no enemies are present.
     */
    AUTO("Auto", "AUTO-SHOT ACTIVATED [V]", 0.24f, 1.0f, 0.51f);

    private final String displayName;
    private final String notificationText;
    private final float notificationColorR;
    private final float notificationColorG;
    private final float notificationColorB;

    ShootingMode(String displayName, String notificationText,
                 float r, float g, float b) {
        this.displayName = displayName;
        this.notificationText = notificationText;
        this.notificationColorR = r;
        this.notificationColorG = g;
        this.notificationColorB = b;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNotificationText() {
        return notificationText;
    }

    public float getNotificationColorR() {
        return notificationColorR;
    }

    public float getNotificationColorG() {
        return notificationColorG;
    }

    public float getNotificationColorB() {
        return notificationColorB;
    }

    /**
     * Returns the opposite shooting mode.
     */
    public ShootingMode toggle() {
        return this == MANUAL ? AUTO : MANUAL;
    }
}
