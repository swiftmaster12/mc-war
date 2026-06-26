package com.civbound.war.state;

public final class CapState {

    private final Cap cap;

    
    private Side holder;

    
    private Side pushing;

    
    private double captureProgress;

    
    private boolean contested;

    public CapState(Cap cap) {
        this.cap = cap;
    }

    public Cap getCap() {
        return cap;
    }

    public Side getHolder() {
        return holder;
    }

    public void setHolder(Side holder) {
        this.holder = holder;
    }

    public Side getPushing() {
        return pushing;
    }

    public void setPushing(Side pushing) {
        this.pushing = pushing;
    }

    public double getCaptureProgress() {
        return captureProgress;
    }

    public void setCaptureProgress(double captureProgress) {
        this.captureProgress = Math.max(0.0, captureProgress);
    }

    public boolean isContested() {
        return contested;
    }

    public void setContested(boolean contested) {
        this.contested = contested;
    }

    
    public double progressFraction(WarConfig config) {
        double t = config.getCapCaptureSeconds();
        if (t <= 0) {
            return holder == null ? 0.0 : 1.0;
        }
        return Math.min(1.0, captureProgress / t);
    }
}
