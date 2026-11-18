/**
 * File uploads: the storage SPI ({@link io.github.mdmusahub.jpaadmin.storage.StorageProvider}), the local provider, the
 * signed upload tickets, the file checks and the startup check (Phase 9a).
 *
 * <p>Public API: {@code StorageProvider}, {@code StoredFile} and {@code StoredContent}, so a host can replace the provider
 * (UPL-10). Everything else here is internal, not stable API.
 *
 * <p>No class here depends on Spring Boot, Spring Security or Thymeleaf. Nothing here reads a client's file name as a
 * path, and nothing logs one (UPL-3).
 */
package io.github.mdmusahub.jpaadmin.storage;
