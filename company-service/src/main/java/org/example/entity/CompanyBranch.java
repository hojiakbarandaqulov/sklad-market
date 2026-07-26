package org.example.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;

@Entity
@Getter
@Setter
public class CompanyBranch extends BaseEntity {
    private String branchName;
    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;
    private String address;
    private String phone;
    private String lng;
    private String lat;
}
