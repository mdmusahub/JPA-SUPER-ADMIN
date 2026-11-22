package io.github.mdmusahub.jpaadmin.engines;

/**
 * The shared engine scenarios on a real MySQL in a container. Without Docker this class is skipped with the reason on a laptop
 * and fails when {@code JPA_ADMIN_REQUIRE_ENGINES} is true ({@link EngineGate}).
 */
class MySqlEngineTest extends AbstractEngineScenarios {

    @Override
    protected EngineDatabase newDatabase() {
        return EngineDatabase.mysql();
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
        return true;
    }
}
