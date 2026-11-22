package io.github.mdmusahub.jpaadmin.sample;

/** The demo application on its own H2: the baseline of the engine check (Phase 14a, D-128). */
class H2SampleEngineTest extends AbstractSampleEngineTest {

    @Override
    SampleEngineDatabase newDatabase() {
        return SampleEngineDatabase.h2();
    }
}
