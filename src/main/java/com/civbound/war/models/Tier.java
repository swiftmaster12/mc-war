package com.civbound.war.models;

public enum Tier {
    CITIZEN,
    NATION,
    ALLY;

    
    public double multiplier(WarConfig config) {
        return switch (this) {
            case CITIZEN -> config.getTierCitizenMultiplier();
            case NATION -> config.getTierNationMultiplier();
            case ALLY -> config.getTierAllyMultiplier();
        };
    }
}
