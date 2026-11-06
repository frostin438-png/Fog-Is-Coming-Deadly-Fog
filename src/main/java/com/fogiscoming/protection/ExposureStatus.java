package com.fogiscoming.protection;

/** Exposure state of a player, shared by server logic and the client HUD. */
public enum ExposureStatus {
    /** Not in the deadly stage, wrong dimension, creative/spectator, dead. */
    NONE,
    SAFE_LIGHT,
    SAFE_SHELTER,
    /** Outside of safety, grace countdown running. */
    GRACE,
    /** Outside of safety, taking damage. */
    EXPOSED;

    public static ExposureStatus byId(int id) {
        ExposureStatus[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }

    public boolean isSafe() {
        return this == SAFE_LIGHT || this == SAFE_SHELTER;
    }

    public boolean isUnsafe() {
        return this == GRACE || this == EXPOSED;
    }
}
