package io.quarkus.bot.buildreporter.githubactions.urlshortener;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NoopUrlShortener implements UrlShortener {

    @Override
    public String shorten(String url) {
        return url;
    }
}
