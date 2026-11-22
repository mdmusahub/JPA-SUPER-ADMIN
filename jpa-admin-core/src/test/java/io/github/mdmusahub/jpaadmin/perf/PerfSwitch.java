package io.github.mdmusahub.jpaadmin.perf;

/** The one switch of the performance measurements (Phase 14a, D-131): off unless the Maven profile {@code perf} turns it on. */
final class PerfSwitch {

    /** The system property the profile {@code perf} sets to {@code true}. */
    static final String PROPERTY = "jpa.admin.perf";

    /** Why a measurement class was skipped, said in the report of the skip. */
    static final String OFF = "SKIPPED, not passed: the performance measurements are not part of a normal build; run them with "
            + "mvn -B -pl jpa-admin-core -Pperf test";

    private PerfSwitch() {
    }
}
