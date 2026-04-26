package com.devradar.notification;

import com.devradar.domain.NotificationPreference;
import com.devradar.repository.NotificationPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "devradar.scheduling.enabled", havingValue = "true")
public class WeeklyDigestJob {

    private static final Logger LOG = LoggerFactory.getLogger(WeeklyDigestJob.class);

    private final NotificationPreferenceRepository prefRepo;
    private final DigestService digestService;

    public WeeklyDigestJob(NotificationPreferenceRepository prefRepo, DigestService digestService) {
        this.prefRepo = prefRepo;
        this.digestService = digestService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        int dayOfWeek = isoDayOfWeek(now.getDayOfWeek());
        int hour = now.getHour();

        List<NotificationPreference> prefs = prefRepo.findByEmailEnabledTrueAndDigestDayOfWeekAndDigestHourUtc(dayOfWeek, hour);
        if (prefs.isEmpty()) return;

        LOG.info("Weekly digest job: found {} users for day={} hour={}", prefs.size(), dayOfWeek, hour);

        for (var pref : prefs) {
            try {
                digestService.sendDigestForUser(pref.getUserId());
            } catch (Exception e) {
                LOG.error("Failed to send digest for user={}: {}", pref.getUserId(), e.getMessage(), e);
            }
        }
    }

    private static int isoDayOfWeek(DayOfWeek dow) {
        return dow.getValue();
    }
}
