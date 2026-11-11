package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;

/** The search-safety contract (SEC-3) against the real {@link ListQueryService}. */
class ListQueryServiceSearchSafetyTest extends SearchSafetyContract {

    private static final AdminEntityRegistry REGISTRY = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
            DiscoverySettings.defaults());
    private static final AdminEntity PARCEL = REGISTRY.find("parcel").orElseThrow();

    @Override
    protected long matches(String text) {
        try (var entityManager = ListTestPersistence.entityManager()) {
            return ListQueryService.over(entityManager, REGISTRY)
                    .list(PARCEL, ListQuery.parse(PARCEL, text, null, null, null, null)).total();
        }
    }
}
