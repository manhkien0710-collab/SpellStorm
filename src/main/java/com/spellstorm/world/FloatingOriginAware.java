package com.spellstorm.world;

/**
 * Technical contract for simulation subsystems and entities that maintain
 * local world-space coordinates and must respond to centralized floating-origin shifts.
 */
public interface FloatingOriginAware {

    /**
     * Translates all internal local world-space coordinates by (-deltaX, -deltaY)
     * during a centralized atomic floating-origin shift.
     *
     * @param deltaX pixel displacement along the X axis (origin shifted by +deltaX)
     * @param deltaY pixel displacement along the Y axis (origin shifted by +deltaY)
     */
    void onOriginShift(long deltaX, long deltaY);
}
