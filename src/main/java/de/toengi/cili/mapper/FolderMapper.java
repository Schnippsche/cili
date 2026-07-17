package de.toengi.cili.mapper;

import de.toengi.cili.dto.folder.BreadcrumbItemDto;
import de.toengi.cili.dto.folder.FolderDto;
import de.toengi.cili.model.entity.Folder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FolderMapper {
    FolderDto toDto(Folder folder);
    BreadcrumbItemDto toBreadcrumbItem(Folder folder);
}
