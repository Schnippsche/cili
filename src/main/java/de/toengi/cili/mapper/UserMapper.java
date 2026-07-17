package de.toengi.cili.mapper;

import de.toengi.cili.dto.user.UserDto;
import de.toengi.cili.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "role", expression = "java(user.getRole().name())")
    UserDto toDto(User user);
}
