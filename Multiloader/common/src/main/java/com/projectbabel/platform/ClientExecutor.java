package com.projectbabel.platform;

/** Schedules work back onto Minecraft's client thread without exposing loader APIs. */
public interface ClientExecutor {
    boolean isClientThread();

    void execute(Runnable task);
}
