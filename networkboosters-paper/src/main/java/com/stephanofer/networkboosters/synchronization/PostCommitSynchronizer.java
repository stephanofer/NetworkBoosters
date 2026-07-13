package com.stephanofer.networkboosters.synchronization;

public interface PostCommitSynchronizer {
    void publish(PostCommitMutation<?> mutation);

    static PostCommitSynchronizer noop() {
        return mutation -> {
        };
    }
}
