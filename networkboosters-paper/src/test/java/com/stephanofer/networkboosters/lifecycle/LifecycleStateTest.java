package com.stephanofer.networkboosters.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LifecycleStateTest {

    @Test
    void statesRemainStableForOperationalDiagnostics() {
        assertEquals(6, LifecycleState.values().length);
        assertEquals(LifecycleState.NEW, LifecycleState.valueOf("NEW"));
        assertEquals(LifecycleState.STARTING, LifecycleState.valueOf("STARTING"));
        assertEquals(LifecycleState.RUNNING, LifecycleState.valueOf("RUNNING"));
        assertEquals(LifecycleState.STOPPING, LifecycleState.valueOf("STOPPING"));
        assertEquals(LifecycleState.STOPPED, LifecycleState.valueOf("STOPPED"));
        assertEquals(LifecycleState.FAILED, LifecycleState.valueOf("FAILED"));
    }
}
