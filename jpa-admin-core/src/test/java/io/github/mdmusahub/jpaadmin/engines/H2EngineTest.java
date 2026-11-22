package io.github.mdmusahub.jpaadmin.engines;

/** The shared engine scenarios on H2 (in memory), the database every other test of the project already uses. */
class H2EngineTest extends AbstractEngineScenarios {

    @Override
    protected EngineDatabase newDatabase() {
        return EngineDatabase.h2();
    }

    @Override
    protected boolean textOrderIsJavaOrder() {
        return true;
    }

    @Override
    protected boolean momentsWithAnOffsetComeBackInUtc() {
        return false;
    }

    @Override
    protected boolean aLargeObjectColumnSortsByItsText() {
        return true;
    }
}
