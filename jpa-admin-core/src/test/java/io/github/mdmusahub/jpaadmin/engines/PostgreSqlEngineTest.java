package io.github.mdmusahub.jpaadmin.engines;

/**
 * The shared engine scenarios on a real PostgreSQL in a container. Without Docker this class is skipped with the reason on a
 * laptop and fails when {@code JPA_ADMIN_REQUIRE_ENGINES} is true ({@link EngineGate}).
 */
class PostgreSqlEngineTest extends AbstractEngineScenarios {

    @Override
    protected EngineDatabase newDatabase() {
        return EngineDatabase.postgres();
    }

    @Override
    protected boolean textOrderIsJavaOrder() {
        return false;
    }

    @Override
    protected boolean momentsWithAnOffsetComeBackInUtc() {
        return true;
    }

    @Override
    protected boolean aLargeObjectColumnSortsByItsText() {
        return false;
    }
}
