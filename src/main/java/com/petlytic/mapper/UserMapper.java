package com.petlytic.mapper;

import com.petlytic.dtos.requests.RegisterUserDTO;
import com.petlytic.dtos.responses.UserResponseDTO;
import com.petlytic.models.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {
    @Mapping(target = "password", ignore = true)
    User toUser(RegisterUserDTO request);

    UserResponseDTO toUserResponse(User user);
}
