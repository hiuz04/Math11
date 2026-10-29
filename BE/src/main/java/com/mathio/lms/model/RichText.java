package com.mathio.lms.model;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public final class RichText {
    private static final Safelist ALLOWED =
            Safelist.basic()
                    .addTags("h1", "h2", "h3", "s", "span", "div")
                    .addAttributes("span", "data-type", "data-latex")
                    .addAttributes("div", "data-type", "data-latex");

    public static String clean(String html) {
        return Jsoup.clean(html, ALLOWED);
    }

    public static boolean hasContent(String html) {
        var document = Jsoup.parse(html);
        return !document.text().isBlank() || document.select("[data-latex]").stream()
                .anyMatch(element -> !element.attr("data-latex").isBlank());
    }

    private RichText() {}
}
