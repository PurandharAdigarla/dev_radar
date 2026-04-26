package com.devradar.notification;

import com.devradar.domain.RadarTheme;
import com.devradar.domain.SourceItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EmailRenderer {

    @Value("${frontend.base-url}")
    private String frontendBaseUrl;

    public String renderRadarDigest(String displayName, List<RadarTheme> themes,
                                     Map<Long, List<SourceItem>> citedItemsByTheme, Long radarId) {
        var sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"margin:0;padding:0;background:#faf9f7;font-family:Inter,-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;\">");
        sb.append("<div style=\"max-width:600px;margin:0 auto;padding:40px 24px;\">");

        sb.append("<div style=\"margin-bottom:32px;\">");
        sb.append("<h1 style=\"margin:0 0 8px;font-size:20px;font-weight:500;color:#2d2a26;letter-spacing:-0.01em;\">Dev Radar — Your Weekly Brief</h1>");
        sb.append("<div style=\"height:1px;background:#e8e4df;\"></div>");
        sb.append("</div>");

        sb.append("<p style=\"font-size:15px;line-height:24px;color:#2d2a26;margin:0 0 24px;\">Hi ").append(escape(displayName)).append(",</p>");
        sb.append("<p style=\"font-size:15px;line-height:24px;color:#6b655e;margin:0 0 32px;\">Here's what matters in your tech world this week.</p>");

        for (var theme : themes) {
            sb.append("<div style=\"margin-bottom:28px;\">");
            sb.append("<h2 style=\"margin:0 0 8px;font-size:17px;font-weight:500;color:#2d2a26;\">").append(escape(theme.getTitle())).append("</h2>");
            sb.append("<p style=\"font-size:14px;line-height:22px;color:#6b655e;margin:0 0 12px;\">").append(escape(theme.getSummary())).append("</p>");

            List<SourceItem> items = citedItemsByTheme.getOrDefault(theme.getId(), List.of());
            if (!items.isEmpty()) {
                sb.append("<ul style=\"margin:0;padding:0 0 0 20px;\">");
                for (var item : items) {
                    sb.append("<li style=\"font-size:13px;line-height:20px;color:#6b655e;margin-bottom:4px;\">");
                    sb.append("<a href=\"").append(escape(item.getUrl())).append("\" style=\"color:#2d2a26;text-decoration:underline;text-underline-offset:3px;\">")
                      .append(escape(item.getTitle())).append("</a>");
                    sb.append("</li>");
                }
                sb.append("</ul>");
            }
            sb.append("</div>");
        }

        String radarUrl = frontendBaseUrl + "/app/radars/" + radarId;
        sb.append("<div style=\"margin-top:32px;padding-top:24px;border-top:1px solid #e8e4df;\">");
        sb.append("<a href=\"").append(escape(radarUrl)).append("\" style=\"display:inline-block;padding:12px 20px;background:#2d2a26;color:#ffffff;text-decoration:none;border-radius:999px;font-size:14px;font-weight:500;\">View full radar</a>");
        sb.append("</div>");

        sb.append("<div style=\"margin-top:32px;padding-top:16px;border-top:1px solid #e8e4df;\">");
        sb.append("<p style=\"font-size:12px;line-height:18px;color:#9e9891;margin:0;\">You're receiving this because you enabled email digests in Dev Radar. ");
        sb.append("To stop, go to Settings &gt; Notifications and turn off email delivery.</p>");
        sb.append("</div>");

        sb.append("</div></body></html>");
        return sb.toString();
    }

    public String renderTestEmail(String displayName) {
        var sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"margin:0;padding:0;background:#faf9f7;font-family:Inter,-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;\">");
        sb.append("<div style=\"max-width:600px;margin:0 auto;padding:40px 24px;\">");
        sb.append("<h1 style=\"margin:0 0 8px;font-size:20px;font-weight:500;color:#2d2a26;\">Dev Radar — Test Email</h1>");
        sb.append("<div style=\"height:1px;background:#e8e4df;margin-bottom:24px;\"></div>");
        sb.append("<p style=\"font-size:15px;line-height:24px;color:#2d2a26;margin:0 0 16px;\">Hi ").append(escape(displayName)).append(",</p>");
        sb.append("<p style=\"font-size:15px;line-height:24px;color:#6b655e;margin:0;\">This is a test email from Dev Radar. If you're reading this, email delivery is working correctly.</p>");
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
