package com.spellstorm.navigation;

import java.util.Objects;

/**
 * Node used in A* priority queue search and path reconstruction.
 */
public final class PathNode implements Comparable<PathNode> {

    public int x;
    public int y;
    public float g;
    public float f;

    public PathNode() {
        this(0, 0, 0.0f, 0.0f);
    }

    public PathNode(int x, int y, float g, float f) {
        this.x = x;
        this.y = y;
        this.g = g;
        this.f = f;
    }

    public void set(int x, int y, float g, float f) {
        this.x = x;
        this.y = y;
        this.g = g;
        this.f = f;
    }

    @Override
    public int compareTo(PathNode other) {
        if (other == null) return -1;
        return Float.compare(this.f, other.f);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathNode node = (PathNode) o;
        return x == node.x && y == node.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "PathNode{" +
                "x=" + x +
                ", y=" + y +
                ", g=" + g +
                ", f=" + f +
                '}';
    }
}
