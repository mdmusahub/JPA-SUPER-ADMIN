package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import java.util.List;
import java.util.Map;

/** The filter-safety contract (SEC-3) against the real {@link ListQueryService}. */
class ListQueryServiceFilterSafetyTest extends FilterSafetyContract {

    private static final AdminEntityRegistry REGISTRY = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
            DiscoverySettings.defaults());
    private static final AdminEntity PARCEL = REGISTRY.find("parcel").orElseThrow();

    @Override
    protected long matches(String text) {
        try (var entityManager = ListTestPersistence.entityManager()) {
            ListQueryService service = ListQueryService.over(entityManager, REGISTRY);
            FilterParse parsed = service.parseFilters(PARCEL, Map.of("filter.notes", List.of(text)));
            return service.list(PARCEL, ListQuery.defaults().withParsedFilters(parsed.filters())).total();
        }
    }
}
