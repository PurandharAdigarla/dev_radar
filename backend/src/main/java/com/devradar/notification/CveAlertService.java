package com.devradar.notification;

import com.devradar.domain.InterestTag;
import com.devradar.domain.Source;
import com.devradar.domain.User;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceRepository;
import com.devradar.repository.UserRepository;
import com.devradar.service.UserInterestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class CveAlertService {

    private static final Logger LOG = LoggerFactory.getLogger(CveAlertService.class);
    private static final String GHSA_SOURCE_CODE = "GHSA";

    private final UserRepository userRepo;
    private final UserInterestService interestService;
    private final SourceItemRepository sourceItemRepo;
    private final SourceRepository sourceRepo;
    private final EmailRenderer renderer;
    private final Optional<EmailSender> emailSender;

    public CveAlertService(UserRepository userRepo,
                           UserInterestService interestService,
                           SourceItemRepository sourceItemRepo,
                           SourceRepository sourceRepo,
                           EmailRenderer renderer,
                           Optional<EmailSender> emailSender) {
        this.userRepo = userRepo;
        this.interestService = interestService;
        this.sourceItemRepo = sourceItemRepo;
        this.sourceRepo = sourceRepo;
        this.renderer = renderer;
        this.emailSender = emailSender;
    }

    public void sendCveAlertsForAllUsers() {
        Source ghsa = sourceRepo.findByCode(GHSA_SOURCE_CODE).orElse(null);
        if (ghsa == null) {
            LOG.info("GHSA source not found; skipping CVE alerts");
            return;
        }

        List<User> users = userRepo.findAll();
        LOG.info("CVE alert job: checking {} users", users.size());

        int sent = 0;
        for (User user : users) {
            try {
                if (sendCveAlertForUser(user, ghsa.getId())) sent++;
            } catch (Exception e) {
                LOG.error("CVE alert failed for user={}: {}", user.getId(), e.getMessage(), e);
            }
        }
        LOG.info("CVE alert job complete: sent {} emails", sent);
    }

    private boolean sendCveAlertForUser(User user, Long ghsaSourceId) {
        List<InterestTag> tags = interestService.findInterestsForUser(user.getId());
        if (tags.isEmpty()) return false;

        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        long count = sourceItemRepo.countNewItemsBySourceForUserSince(user.getId(), ghsaSourceId, since);
        if (count == 0) return false;

        String html = renderer.renderCveAlert(user.getDisplayName(), (int) count);
        requireSender().send(user.getEmail(), "⚠️ " + count + " new CVEs affect your stack", html);
        LOG.info("CVE alert sent to user={} email={} count={}", user.getId(), user.getEmail(), count);
        return true;
    }

    private EmailSender requireSender() {
        return emailSender.orElseThrow(() -> new IllegalStateException("Email sending is not configured"));
    }
}
