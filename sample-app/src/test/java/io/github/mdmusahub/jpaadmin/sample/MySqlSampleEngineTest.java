package io.github.mdmusahub.jpaadmin.sample;

/**
 * The demo application on a real MySQL in a container (Phase 14a, D-128). Without Docker each test is skipped with the reason on
 * a laptop, and the class fails when {@code JPA_ADMIN_REQUIRE_ENGINES} is true.
 */
class MySqlSampleEngineTest extends AbstractSampleEngineTest {

    @Override
    SampleEngineDatabase newDatabase() {
        return SampleEngineDatabase.mysql();
    }
}
