package com.civbound.war;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Battle {

    private final Campaign campaign;
    private final int number;
    private final long startMillis;
    private final long durationMillis;
    private final long prepEndMillis;

    
    private final Map<UUID, Tier> attackerRoster = new HashMap<>();
    private final Map<UUID, Tier> defenderRoster = new HashMap<>();

    private final List<CapState> caps = new ArrayList<>();

    
    private final Set<ChunkPos> warzoneChunks = new HashSet<>();

    private double attackerScore;
    private double defenderScore;

    private boolean active = true;
    private Side winner;
    private String recordingName;

    public Battle(Campaign campaign, int number, long startMillis, long durationMillis, long prepEndMillis) {
        this.campaign = campaign;
        this.number = number;
        this.startMillis = startMillis;
        this.durationMillis = durationMillis;
        this.prepEndMillis = prepEndMillis;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public int getNumber() {
        return number;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return startMillis + durationMillis;
    }

    public long millisRemaining(long now) {
        return Math.max(0L, getEndMillis() - now);
    }

    public boolean isTimedOut(long now) {
        return now >= getEndMillis();
    }

    public boolean isInPrep(long now) {
        return now < prepEndMillis;
    }

    public Map<UUID, Tier> roster(Side side) {
        return side == Side.ATTACKER ? attackerRoster : defenderRoster;
    }

    
    public Side sideOf(UUID id) {
        if (attackerRoster.containsKey(id)) {
            return Side.ATTACKER;
        }
        if (defenderRoster.containsKey(id)) {
            return Side.DEFENDER;
        }
        return null;
    }

    public Tier tierOf(UUID id) {
        Tier t = attackerRoster.get(id);
        if (t != null) {
            return t;
        }
        return defenderRoster.get(id);
    }

    public boolean isParticipant(UUID id) {
        return attackerRoster.containsKey(id) || defenderRoster.containsKey(id);
    }

    public List<CapState> getCaps() {
        return caps;
    }

    public Set<ChunkPos> getWarzoneChunks() {
        return warzoneChunks;
    }

    public boolean isInWarzone(String world, int chunkX, int chunkZ) {
        return warzoneChunks.contains(new ChunkPos(world, chunkX, chunkZ));
    }

    public double score(Side side) {
        return side == Side.ATTACKER ? attackerScore : defenderScore;
    }

    public void addScore(Side side, double amount) {
        if (side == Side.ATTACKER) {
            attackerScore += amount;
        } else {
            defenderScore += amount;
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Side getWinner() {
        return winner;
    }

    public void setWinner(Side winner) {
        this.winner = winner;
    }

    public String getRecordingName() {
        return recordingName;
    }

    public void setRecordingName(String recordingName) {
        this.recordingName = recordingName;
    }
}
