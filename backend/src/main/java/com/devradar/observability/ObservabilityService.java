package com.devradar.observability;

import com.devradar.domain.MetricsDailyRollup;
import com.devradar.repository.MetricsDailyRollupRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ObservabilityService {

    private final MetricsDailyRollupRepository repository;

    public ObservabilityService(MetricsDailyRollupRepository repository) {
        this.repository = repository;
    }

    public Optional<MetricsDailyRollup> getForDate(LocalDate date) {
        return repository.findById(date);
    }

    public List<MetricsDailyRollup> getTimeseries(int days) {
        var to = LocalDate.now();
        var from = to.minusDays(days);
        return repository.findByDateBetweenOrderByDateDesc(from, to);
    }
}
