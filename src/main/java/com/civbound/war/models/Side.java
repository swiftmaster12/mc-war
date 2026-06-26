package com.civbound.war.models;

public enum Side {
    ATTACKER,
    DEFENDER;

    public Side other() {
        return this == ATTACKER ? DEFENDER : ATTACKER;
    }

    public String display() {
        return this == ATTACKER ? "Attackers" : "Defenders";
    }
}
