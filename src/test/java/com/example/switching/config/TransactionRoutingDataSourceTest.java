package com.example.switching.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionRoutingDataSourceTest {
    private final TransactionRoutingDataSource routing = new TransactionRoutingDataSource();

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void defaultsToPrimary() {
        assertThat(routing.currentRoute()).isEqualTo(DataSourceRoute.PRIMARY);
    }

    @Test
    void routesReadOnlyTransactionToReplica() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThat(routing.currentRoute()).isEqualTo(DataSourceRoute.REPLICA);
    }

    @Test
    void explicitPrimaryOverrideWinsInsideReadOnlyTransaction() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        DataSourceRoute route = ReadReplicaRoutingContext.forcePrimary(routing::currentRoute);
        assertThat(route).isEqualTo(DataSourceRoute.PRIMARY);
        assertThat(routing.currentRoute()).isEqualTo(DataSourceRoute.REPLICA);
    }
}
