package de.toengi.cili.mapper;

import de.toengi.cili.dto.group.GroupDto;
import de.toengi.cili.model.entity.RightsGroup;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupMapper {

    @Mapping(target = "memberCount", expression = "java(group.getMemberships().size())")
    @Mapping(target = "system", source = "system")
    GroupDto toDto(RightsGroup group);
}
