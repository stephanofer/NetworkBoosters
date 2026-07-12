package com.stephanofer.networkboosters.persistence.transaction;

import com.hera.craftkit.database.SqlRetryClassifier;
import com.hera.craftkit.database.TransactionIsolation;
import com.hera.craftkit.database.TransactionOptions;
import com.hera.craftkit.database.TransactionRetryEvent;
import com.hera.craftkit.database.TransactionRetryPolicy;
import java.util.Objects;
import java.util.function.Consumer;

public final class BoosterTransactionOptions {

    private BoosterTransactionOptions() {
    }

    public static TransactionOptions consistentRead() {
        return TransactionOptions.builder()
            .isolation(TransactionIsolation.REPEATABLE_READ)
            .readOnly(true)
            .build();
    }

    public static TransactionOptions retryingWrite(Consumer<TransactionRetryEvent> listener) {
        TransactionRetryPolicy retryPolicy = TransactionRetryPolicy.builder()
            .maxAttempts(3)
            .initialDelayMillis(25)
            .maxDelayMillis(250)
            .multiplier(2.0)
            .jitterFactor(0.25)
            .classifier(SqlRetryClassifier.mysqlTransient())
            .listener(Objects.requireNonNull(listener, "listener")::accept)
            .build();

        return TransactionOptions.builder()
            .isolation(TransactionIsolation.READ_COMMITTED)
            .retryPolicy(retryPolicy)
            .build();
    }
}
