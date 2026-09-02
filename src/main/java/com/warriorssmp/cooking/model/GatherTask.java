package com.warriorssmp.cooking.model;

import org.bukkit.Material;

public final class GatherTask {

    private final Material material;
    private final int tier;
    private final int required;
    private int progress;

    public GatherTask(Material material, int tier, int required, int progress) {
        this.material = material;
        this.tier = tier;
        this.required = required;
        this.progress = progress;
    }

    public Material material() {
        return material;
    }

    public int tier() {
        return tier;
    }

    public int required() {
        return required;
    }

    public int progress() {
        return progress;
    }

    public boolean isComplete() {
        return progress >= required;
    }

    public void addProgress(int amount) {
        progress = Math.min(required, progress + amount);
    }

    public String displayName() {
        String raw = material.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
