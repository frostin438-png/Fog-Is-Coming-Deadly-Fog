package com.fogiscoming.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent fog progress, stored in {@code <world>/data/fogiscoming.dat} (overworld data storage).
 * Survives restarts and re-joins; one instance per world/server.
 */
public final class FogWorldData extends SavedData {
    private static final String NAME = "fogiscoming";

    /** In-game ticks that have passed since the countdown started (monotonic, never decreases on its own). */
    private long elapsedTicks;
    /** Overworld day time seen on the previous tick, used to measure progress (-1 = unknown). */
    private long lastDayTime = -1L;
    /** -1 = automatic progression, 0..3 = stage forced by an operator. */
    private int forcedStage = -1;
    /** Highest stage ever reached automatically — the deadly fog never recedes. */
    private int highestStage;

    public static FogWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FogWorldData::load, FogWorldData::new, NAME);
    }

    public static FogWorldData load(CompoundTag tag) {
        FogWorldData data = new FogWorldData();
        data.elapsedTicks = Math.max(0L, tag.getLong("ElapsedTicks"));
        data.lastDayTime = tag.contains("LastDayTime") ? tag.getLong("LastDayTime") : -1L;
        data.forcedStage = tag.contains("ForcedStage") ? tag.getInt("ForcedStage") : -1;
        data.highestStage = tag.getInt("HighestStage");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("ElapsedTicks", elapsedTicks);
        tag.putLong("LastDayTime", lastDayTime);
        tag.putInt("ForcedStage", forcedStage);
        tag.putInt("HighestStage", highestStage);
        return tag;
    }

    public long getElapsedTicks() {
        return elapsedTicks;
    }

    public void setElapsedTicks(long elapsedTicks) {
        this.elapsedTicks = Math.max(0L, elapsedTicks);
        setDirty();
    }

    public long getLastDayTime() {
        return lastDayTime;
    }

    public void setLastDayTime(long lastDayTime) {
        if (this.lastDayTime != lastDayTime) {
            this.lastDayTime = lastDayTime;
            setDirty();
        }
    }

    public int getForcedStage() {
        return forcedStage;
    }

    public void setForcedStage(int forcedStage) {
        this.forcedStage = forcedStage;
        setDirty();
    }

    public int getHighestStage() {
        return highestStage;
    }

    public void setHighestStage(int highestStage) {
        this.highestStage = highestStage;
        setDirty();
    }
}
