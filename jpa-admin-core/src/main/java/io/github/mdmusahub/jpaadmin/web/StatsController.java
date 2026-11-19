package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.service.StatsService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * {@code GET {base path}/api/stats?range=7|30|90}: the data of the dashboard's chart as JSON (Phase 10, D-101).
 *
 * <p>Thin on purpose (ARCH-2): it asks {@link StatsService} for the series and writes it. The console's security chain
 * decides who may ask (a session is required, like for every console route); only GET is mapped, so any other method is
 * refused by the framework. The body is the frozen {@code ChartSeries} record; it holds no entity, no collection and no
 * sensitive value, and an entity that is hidden or excluded is not in the registry, so it is not in the answer. A
 * {@code range} that is not 7, 30 or 90 is replaced by 30 and the answer says so in its {@code notes}.
 *
 * <p>The mapping is relative; the auto-configuration puts the base path in front of it (D-30). Internal, not stable API.
 */
@Controller
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    /**
     * @param range the requested range as it came, {@code null} when absent; untrusted
     */
    @GetMapping("/api/stats")
    public void stats(@RequestParam(name = "range", required = false) String range, HttpServletResponse response)
            throws IOException {
        String body = ChartJson.json(stats.series(range));
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}
