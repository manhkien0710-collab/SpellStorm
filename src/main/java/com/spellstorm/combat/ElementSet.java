package com.spellstorm.combat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Compact, order-independent bitmask set of elements.
 * Used as keys for spell combination recipes, so {Fire, Wind} equals {Wind, Fire}.
 */
public class ElementSet {

    private int mask = 0;

    public ElementSet() {
        this.mask = 0;
    }

    public ElementSet(Element... elements) {
        if (elements != null) {
            for (Element e : elements) {
                add(e);
            }
        }
    }

    public ElementSet(Collection<Element> elements) {
        if (elements != null) {
            for (Element e : elements) {
                add(e);
            }
        }
    }

    public void add(Element element) {
        if (element != null) {
            mask |= (1 << element.getIndex());
        }
    }

    public void remove(Element element) {
        if (element != null) {
            mask &= ~(1 << element.getIndex());
        }
    }

    public boolean contains(Element element) {
        if (element == null) {
            return false;
        }
        return (mask & (1 << element.getIndex())) != 0;
    }

    public int count() {
        return Integer.bitCount(mask);
    }

    public int mask() {
        return mask;
    }

    public int getMask() {
        return mask;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElementSet that = (ElementSet) o;
        return mask == that.mask;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mask);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ElementSet{");
        boolean first = true;
        for (Element e : Element.values()) {
            if (contains(e)) {
                if (!first) sb.append(", ");
                sb.append(e.getDisplayName());
                first = false;
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
