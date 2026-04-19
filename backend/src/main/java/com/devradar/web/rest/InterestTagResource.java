package com.devradar.web.rest;

import com.devradar.domain.InterestCategory;
import com.devradar.service.InterestTagService;
import com.devradar.service.mapper.InterestTagMapper;
import com.devradar.web.rest.dto.InterestTagResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interest-tags")
public class InterestTagResource {

    private final InterestTagService service;
    private final InterestTagMapper mapper;

    public InterestTagResource(InterestTagService service, InterestTagMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<InterestTagResponseDTO> search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) InterestCategory category,
        Pageable pageable
    ) {
        return service.search(q, category, pageable).map(mapper::toDto);
    }
}
