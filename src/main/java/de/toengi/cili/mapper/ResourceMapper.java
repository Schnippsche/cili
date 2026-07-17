package de.toengi.cili.mapper;

import de.toengi.cili.dto.resource.MetadataDto;
import de.toengi.cili.dto.resource.ResourceDto;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.ResourceMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ResourceMapper {
    @Mapping(target = "metadata", source = "metadata")
    @Mapping(target = "id", source = "resource.id")
    @Mapping(target = "sortOrder", source = "resource.sortOrder")
    @Mapping(target = "updatedAt", source = "resource.updatedAt")
    @Mapping(target = "thumbnailStatus", source = "thumbnailStatus")
    @Mapping(target = "hasAnalyzableSubtitles", source = "hasAnalyzableSubtitles")
    ResourceDto toDto(Resource resource, ResourceMetadata metadata, String thumbnailStatus, boolean hasAnalyzableSubtitles);

    MetadataDto toMetadataDto(ResourceMetadata metadata);
}
