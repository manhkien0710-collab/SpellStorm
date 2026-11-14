package com.spellstorm.core;

/**
 * High-level state lifecycle of a match in SpellStorm.
 */
public enum GameState {
    /** Initial state before match begins. */
    START,
    /** Normal active gameplay simulation: player, enemies, combat, and timers ticking. */
    PLAYING,
    /** Manual pause suspended by player (Escape/P): simulation and timers frozen. */
    PAUSED,
    /** Level-up perk selection modal open: simulation and power-ups frozen, player immune. */
    LEVEL_UP,
    /** Player health depleted: simulation frozen, match summary displayed, restart/quit active. */
    GAME_OVER
}
