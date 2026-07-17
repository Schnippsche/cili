package de.toengi.cili.mapper;

import de.toengi.cili.dto.acl.AclEntryDto;
import de.toengi.cili.model.entity.AclEntry;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AclMapper {
    AclEntryDto toDto(AclEntry entry);
}
