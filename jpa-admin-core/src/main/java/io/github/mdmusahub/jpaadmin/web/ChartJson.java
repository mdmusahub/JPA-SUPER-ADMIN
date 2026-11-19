package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.ChartSeries;

/**
 * Writes a {@link ChartSeries} as JSON by hand, so the answer does not depend on the host's JSON configuration (naming
 * strategies, modules, escaping, a host without a JSON library). The shape is the record's:
 *
 * <pre>{"range":30,"entities":[{"name":"x","label":"X","available":true,"total":3,"capped":false}],
 * "days":[{"date":"2026-11-01","counts":[1]}],"notes":[]}</pre>
 *
 * <p>Strings go through the same escaping as the upload answer, which also escapes every character that could matter if the
 * text were ever put into a page. Package-private: a rule, not API.
 */
final class ChartJson {

    private ChartJson() {
    }

    static String json(ChartSeries series) {
        StringBuilder out = new StringBuilder(256 + series.days().size() * 24);
        out.append("{\"range\":").append(series.range()).append(",\"entities\":[");
        for (int i = 0; i < series.entities().size(); i++) {
            ChartSeries.Entity entity = series.entities().get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"name\":").append(UploadAnswers.string(entity.name()));
            out.append(",\"label\":").append(UploadAnswers.string(entity.label()));
            out.append(",\"available\":").append(entity.available());
            out.append(",\"total\":").append(entity.total());
            out.append(",\"capped\":").append(entity.capped()).append('}');
        }
        out.append("],\"days\":[");
        for (int i = 0; i < series.days().size(); i++) {
            ChartSeries.Day day = series.days().get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"date\":").append(UploadAnswers.string(day.date())).append(",\"counts\":[");
            for (int c = 0; c < day.counts().size(); c++) {
                if (c > 0) {
                    out.append(',');
                }
                out.append(day.counts().get(c));
            }
            out.append("]}");
        }
        out.append("],\"notes\":[");
        for (int i = 0; i < series.notes().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(UploadAnswers.string(series.notes().get(i)));
        }
        return out.append("]}").toString();
    }
}
