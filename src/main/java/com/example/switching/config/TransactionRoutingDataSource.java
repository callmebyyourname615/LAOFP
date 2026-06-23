package com.example.switching.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Routes read-only transactions to the replica and all other work to primary. */
public class TransactionRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return currentRoute();
    }

    DataSourceRoute currentRoute() {
        DataSourceRoute override = ReadReplicaRoutingContext.currentOverride();
        if (override != null) {
            return override;
        }
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? DataSourceRoute.REPLICA
                : DataSourceRoute.PRIMARY;
    }
}
