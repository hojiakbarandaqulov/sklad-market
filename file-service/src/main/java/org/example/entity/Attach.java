package org.example.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;

@Getter
@Setter
@Entity
public class Attach extends BaseEntity {
    private String originalName;
    private Long size;
    private String extension;
    private String path;
}
