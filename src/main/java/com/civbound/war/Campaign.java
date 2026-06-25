package com.civbound.war;

import java.util.ArrayList;
import java.util.List;

public final class Campaign {

    private final String id;
    private final String name;
    private final String attackerLand;
    private final String defenderLand;

    
    private final List<String> attackerAllies = new ArrayList<>();
    private final List<String> defenderAllies = new ArrayList<>();

    private int attackerSiegePoints;
    private int defenderSiegePoints;
    private int battlesPlayed;

    
    private final boolean squatter;

    
    private Battle currentBattle;

    private boolean finished;
    private Side winner;

    public Campaign(String id, String name, String attackerLand, String defenderLand, boolean squatter) {
        this.id = id;
        this.name = name;
        this.attackerLand = attackerLand;
        this.defenderLand = defenderLand;
        this.squatter = squatter;
    }

    public String getId() {
        return id;
    }

    
    public String getName() {
        return name;
    }

    public String getAttackerLand() {
        return attackerLand;
    }

    public String getDefenderLand() {
        return defenderLand;
    }

    public List<String> allies(Side side) {
        return side == Side.ATTACKER ? attackerAllies : defenderAllies;
    }

    public String warringLand(Side side) {
        return side == Side.ATTACKER ? attackerLand : defenderLand;
    }

    public int siegePoints(Side side) {
        return side == Side.ATTACKER ? attackerSiegePoints : defenderSiegePoints;
    }

    public void addSiegePoint(Side side) {
        if (side == Side.ATTACKER) {
            attackerSiegePoints++;
        } else {
            defenderSiegePoints++;
        }
    }

    public int getBattlesPlayed() {
        return battlesPlayed;
    }

    public void incrementBattlesPlayed() {
        battlesPlayed++;
    }

    public boolean isSquatter() {
        return squatter;
    }

    public int siegePointsToWin(WarConfig config) {
        return squatter ? config.getSquatterSiegePointsToWin() : config.getSiegePointsToWin();
    }

    public Battle getCurrentBattle() {
        return currentBattle;
    }

    public void setCurrentBattle(Battle battle) {
        this.currentBattle = battle;
    }

    public boolean isBattleActive() {
        return currentBattle != null && currentBattle.isActive();
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public Side getWinner() {
        return winner;
    }

    public void setWinner(Side winner) {
        this.winner = winner;
    }

    
    public Side seriesWinner(WarConfig config) {
        int need = siegePointsToWin(config);
        if (attackerSiegePoints >= need) {
            return Side.ATTACKER;
        }
        if (defenderSiegePoints >= need) {
            return Side.DEFENDER;
        }
        return null;
    }
}
