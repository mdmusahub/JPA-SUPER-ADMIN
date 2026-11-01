/**
 * View-model records: the contract between the controllers (backend track) and the Thymeleaf templates (UI track).
 *
 * <p>These records are public only because Thymeleaf and the preview app need them. They are internal, not stable
 * API. Records hold display-ready values only: no entities and no sensitive values (SEC-1). Changes follow the
 * contract rules in {@code docs/UI_PLAN.md}.
 */
package io.github.mdmusahub.jpaadmin.web.view;
