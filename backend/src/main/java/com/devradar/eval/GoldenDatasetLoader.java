package com.devradar.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Component
public class GoldenDatasetLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<GoldenRadarCase> loadAll() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            var resources = resolver.getResources("classpath:evals/golden_radars/*.json");
            Arrays.sort(resources, Comparator.comparing(r -> r.getFilename() != null ? r.getFilename() : ""));

            List<GoldenRadarCase> cases = new ArrayList<>();
            for (var resource : resources) {
                cases.add(objectMapper.readValue(resource.getInputStream(), GoldenRadarCase.class));
            }
            return cases;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load golden datasets", e);
        }
    }
}
