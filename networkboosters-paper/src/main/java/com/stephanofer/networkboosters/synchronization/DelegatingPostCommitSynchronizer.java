package com.stephanofer.networkboosters.synchronization;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class DelegatingPostCommitSynchronizer implements PostCommitSynchronizer {

    private final AtomicReference<PostCommitSynchronizer> delegate = new AtomicReference<>(PostCommitSynchronizer.noop());

    public void setDelegate(PostCommitSynchronizer delegate) {
        this.delegate.set(Objects.requireNonNull(delegate, "delegate"));
    }

    @Override
    public void publish(PostCommitMutation<?> mutation) {
        this.delegate.get().publish(mutation);
    }
}
