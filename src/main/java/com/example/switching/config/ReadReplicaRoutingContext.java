package com.example.switching.config;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Explicit routing override for correctness-sensitive reads.
 *
 * <p>Normal routing is driven by {@code @Transactional(readOnly = true)}. Financial
 * read-after-write paths can temporarily force the primary without changing the
 * surrounding transaction declaration.</p>
 */
public final class ReadReplicaRoutingContext {
    private static final ThreadLocal<Deque<DataSourceRoute>> ROUTES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private ReadReplicaRoutingContext() {
    }

    public static DataSourceRoute currentOverride() {
        return ROUTES.get().peek();
    }

    public static <T> T forcePrimary(Supplier<T> action) {
        return withRoute(DataSourceRoute.PRIMARY, action);
    }

    public static void forcePrimary(Runnable action) {
        withRoute(DataSourceRoute.PRIMARY, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T forceReplica(Supplier<T> action) {
        return withRoute(DataSourceRoute.REPLICA, action);
    }

    private static <T> T withRoute(DataSourceRoute route, Supplier<T> action) {
        Deque<DataSourceRoute> routes = ROUTES.get();
        routes.push(route);
        try {
            return action.get();
        } finally {
            routes.pop();
            if (routes.isEmpty()) {
                ROUTES.remove();
            }
        }
    }
}
