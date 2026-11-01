package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Hands the CSRF token of the current request to the console's pages (SEC-5).
 *
 * <p>This module has no dependency on Spring Security (the preview app and the view-model records use it without
 * one). The auto-configuration module implements this interface by reading the token that Spring Security's CSRF
 * filter put on the request.
 *
 * <p>Internal, not stable API. Public only because the auto-configuration module implements it.
 */
public interface CsrfTokens {

    /**
     * The token of {@code request}, ready for a hidden form field or a request header.
     *
     * <p>Never {@code null}. A request that carries no token (which cannot happen behind the console's security
     * chain) gets an empty token, so any form it renders fails the CSRF check when posted, which is the safe outcome.
     */
    PageChrome.Csrf forRequest(HttpServletRequest request);
}
