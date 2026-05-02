package org.example.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;

@Getter
@Setter
@Entity
public class CompanyDocument extends BaseEntity {
    private Long companyId;
    private String documentType;
    private String attachId;
    private String fileUrl;
    private String status = "UPLOADED";
}
