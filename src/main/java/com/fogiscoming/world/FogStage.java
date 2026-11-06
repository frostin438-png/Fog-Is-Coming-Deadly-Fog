package com.fogiscoming.world;

public enum FogStage {
    CALM(0, "THE CALM"),
    ARRIVAL(1, "STAGE I — THE ARRIVAL"),
    THICKENING(2, "STAGE II — THE THICKENING"),
    DEADLY(3, "STAGE III — THE DEADLY FOG");

    public final int id;
    public final String title;

    FogStage(int id, String title) {
        this.id = id;
        this.title = title;
    }

    public static FogStage byId(int id) {
        FogStage[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, id))];
    }

    public boolean isDeadly() {
        return this == DEADLY;
    }
}
