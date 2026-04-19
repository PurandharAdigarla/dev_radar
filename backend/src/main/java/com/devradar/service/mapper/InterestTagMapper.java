package com.devradar.service.mapper;

import com.devradar.domain.InterestTag;
import com.devradar.web.rest.dto.InterestTagResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InterestTagMapper {
    InterestTagResponseDTO toDto(InterestTag tag);
}
