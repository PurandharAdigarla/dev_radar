package com.devradar.service.mapper;

import com.devradar.domain.User;
import com.devradar.web.rest.dto.UserResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponseDTO toDto(User user);
}
