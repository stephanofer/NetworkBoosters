package com.stephanofer.networkboosters.synchronization;

import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PostCommitMutation<T>(T result, List<PostCommitChange> changes) {
    public PostCommitMutation {
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }

    public static <T> PostCommitMutation<T> unchanged(T result) {
        return new PostCommitMutation<>(result, List.of());
    }

    public List<PlayerBoostSnapshot> snapshots() {
        ArrayList<PlayerBoostSnapshot> snapshots = new ArrayList<>();
        for (PostCommitChange change : this.changes) {
            snapshots.addAll(change.snapshots());
        }
        return List.copyOf(snapshots);
    }
}
