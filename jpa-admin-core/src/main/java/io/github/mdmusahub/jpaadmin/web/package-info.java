/**
 * Console controllers, the exception resolver for the console's error pages, and the small helpers they share
 * ({@link io.github.mdmusahub.jpaadmin.web.BasePath}, the page chrome factory, the fixed error texts). Controllers here
 * declare mappings relative to the console path; the auto-configuration adds the configured base path in front of them
 * (and in front of nothing else). This module has no dependency on Spring Security or Thymeleaf: the CSRF token comes
 * in through the {@link io.github.mdmusahub.jpaadmin.web.CsrfTokens} interface, and the pages' views come in through
 * {@link io.github.mdmusahub.jpaadmin.web.ConsoleViewFactory}, so every page is answered with a ready-made view and no
 * view resolver of the host is ever asked about it (D-40).
 */
package io.github.mdmusahub.jpaadmin.web;
