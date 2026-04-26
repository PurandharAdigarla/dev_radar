package com.devradar.notification;

import com.devradar.domain.RadarTheme;
import com.devradar.domain.SourceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailRendererTest {

    private EmailRenderer renderer;

    @BeforeEach
    void setUp() throws Exception {
        renderer = new EmailRenderer();
        Field f = EmailRenderer.class.getDeclaredField("frontendBaseUrl");
        f.setAccessible(true);
        f.set(renderer, "https://devradar.example.com");
    }

    @Test
    void renderRadarDigest_containsThemeTitles() {
        RadarTheme theme = makeTheme(1L, "Rust Adoption Surge", "Rust is growing fast.");
        String html = renderer.renderRadarDigest("Alice", List.of(theme), Map.of(1L, List.of()), 42L);
        assertTrue(html.contains("Rust Adoption Surge"));
        assertTrue(html.contains("Hi Alice,"));
    }

    @Test
    void renderRadarDigest_containsCitationLinks() {
        RadarTheme theme = makeTheme(1L, "Theme", "Summary");
        SourceItem item = makeSourceItem("https://example.com/article", "Cool Article");
        String html = renderer.renderRadarDigest("Bob", List.of(theme), Map.of(1L, List.of(item)), 10L);
        assertTrue(html.contains("href=\"https://example.com/article\""));
        assertTrue(html.contains("Cool Article"));
    }

    @Test
    void renderRadarDigest_containsRadarLink() {
        RadarTheme theme = makeTheme(1L, "Theme", "Summary");
        String html = renderer.renderRadarDigest("Charlie", List.of(theme), Map.of(1L, List.of()), 99L);
        assertTrue(html.contains("https://devradar.example.com/app/radars/99"));
        assertTrue(html.contains("View full radar"));
    }

    private static RadarTheme makeTheme(Long id, String title, String summary) {
        RadarTheme t = new RadarTheme();
        try {
            Field idField = RadarTheme.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(t, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        t.setTitle(title);
        t.setSummary(summary);
        t.setDisplayOrder(0);
        return t;
    }

    private static SourceItem makeSourceItem(String url, String title) {
        SourceItem si = new SourceItem();
        si.setUrl(url);
        si.setTitle(title);
        return si;
    }
}
