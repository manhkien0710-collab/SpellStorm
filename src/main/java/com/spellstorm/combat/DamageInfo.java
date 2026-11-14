package com.spellstorm.combat;

/**
 * Data holder for damage calculation and transmission.
 * Supports direct physical attacks (subject to armor mitigation)
 * and true damage / damage-over-time effects that ignore armor.
 */
public class DamageInfo {

    public float amount;
    public boolean ignoreArmor;

    public DamageInfo() {
        this(0.0f, false);
    }

    public DamageInfo(float amount) {
        this(amount, false);
    }

    public DamageInfo(float amount, boolean ignoreArmor) {
        this.amount = amount;
        this.ignoreArmor = ignoreArmor;
    }

    public float getAmount() {
        return amount;
    }

    public float amount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }

    public boolean isIgnoreArmor() {
        return ignoreArmor;
    }

    public boolean ignoreArmor() {
        return ignoreArmor;
    }

    public void setIgnoreArmor(boolean ignoreArmor) {
        this.ignoreArmor = ignoreArmor;
    }

    @Override
    public String toString() {
        return "DamageInfo{amount=" + amount + ", ignoreArmor=" + ignoreArmor + "}";
    }
}
