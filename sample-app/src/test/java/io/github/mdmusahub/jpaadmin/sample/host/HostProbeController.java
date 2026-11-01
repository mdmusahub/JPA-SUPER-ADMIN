package io.github.mdmusahub.jpaadmin.sample.host;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stands in for a host application's own routes in the tests. Not in the console package, so never prefixed. */
@RestController
class HostProbeController {

    @GetMapping("/hello")
    String hello() {
        return "hello from the host";
    }

    @GetMapping("/api/data")
    String data() {
        return "host data";
    }

    /** A host route that keeps state in the HTTP session, which the console shares with the host. */
    @GetMapping("/cart/add")
    String add(HttpServletRequest request) {
        request.getSession().setAttribute("cart", "3 items");
        return "added";
    }

    @GetMapping("/cart")
    String cart(HttpServletRequest request) {
        Object cart = request.getSession(false) == null ? null : request.getSession(false).getAttribute("cart");
        return "cart: " + cart;
    }

    /** A host route that starts a session, to compare its cookie with the console's. */
    @GetMapping("/session")
    String session(HttpServletRequest request) {
        request.getSession();
        return "session started";
    }
}
