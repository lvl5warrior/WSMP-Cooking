package com.warriorssmp.cooking.model;

import java.util.ArrayList;
import java.util.List;

public final class GatherTier {

    private final int number;
    private final String name;
    private final String display;
    private final String difficulty;
    private final int minLevel;
    private final int baseCoins;
    private final boolean premium;
    private final List<ResourceDef> resources = new ArrayList<>();

    public GatherTier(int number, String name, String display, String difficulty,
                       int minLevel, int baseCoins, boolean premium) {
        this.number = number;
        this.name = name;
        this.display = display;
        this.difficulty = difficulty;
        this.minLevel = minLevel;
        this.baseCoins = baseCoins;
        this.premium = premium;
    }

    public void addResource(ResourceDef def) {
        resources.add(def);
    }

    public int number() {
        return number;
    }

    public String rawName() {
        return name;
    }

    public String display() {
        return display;
    }

    public String difficulty() {
        return difficulty;
    }

    public int minLevel() {
        return minLevel;
    }

    public int baseCoins() {
        return baseCoins;
    }

    public boolean premium() {
        return premium;
    }

    public List<ResourceDef> resources() {
        return resources;
    }
}
