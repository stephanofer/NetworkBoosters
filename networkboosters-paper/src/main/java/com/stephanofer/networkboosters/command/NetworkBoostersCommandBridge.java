package com.stephanofer.networkboosters.command;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class NetworkBoostersCommandBridge {

    private final AtomicReference<NetworkBoostersCommandRuntime> runtime = new AtomicReference<>();

    public Optional<NetworkBoostersCommandRuntime> runtime() {
        return Optional.ofNullable(this.runtime.get()).filter(NetworkBoostersCommandRuntime::isRunning);
    }

    public void bind(NetworkBoostersCommandRuntime runtime) {
        this.runtime.set(Objects.requireNonNull(runtime, "runtime"));
    }

    public void clear(NetworkBoostersCommandRuntime runtime) {
        this.runtime.compareAndSet(runtime, null);
    }
}
