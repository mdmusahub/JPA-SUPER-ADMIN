/**
 * The console's services: the code that reads and writes the host's data.
 *
 * <p>Services are the only place that touches an {@code EntityManager} (ARCH-2). Reads run in a read-only
 * transaction (ARCH-5) and are built with the Criteria API, never by putting request values into a query string
 * (SEC-3).
 *
 * <p>Internal, not stable API.
 */
package io.github.mdmusahub.jpaadmin.service;
