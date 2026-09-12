package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.tag.TagRequestDTO;
import com.municipalidadsanjose.archivo.dto.tag.TagResponseDTO;
import com.municipalidadsanjose.archivo.entity.Tag;
import org.springframework.stereotype.Component;

@Component
public class TagMapper {

    public Tag toEntity(TagRequestDTO dto) {
        Tag tag = new Tag();
        tag.setNombre(dto.nombre());
        return tag;
    }

    public TagResponseDTO toResponseDTO(Tag tag) {
        return new TagResponseDTO(tag.getId(), tag.getNombre());
    }
}
