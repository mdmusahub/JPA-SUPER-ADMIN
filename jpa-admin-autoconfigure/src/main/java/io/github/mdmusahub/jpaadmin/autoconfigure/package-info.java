/**
 * Properties, startup checks, the sign-in security chain, the console's own private Thymeleaf and the
 * auto-configuration for the console. The security and view classes are written so that the host application's own
 * security and view technology cannot feel them (SEC-4): see {@code JpaAdminSecurityConfiguration} and
 * {@code ConsoleThymeleafViews}.
 */
package io.github.mdmusahub.jpaadmin.autoconfigure;
