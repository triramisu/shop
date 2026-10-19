package com.shop.identity.internal.entity;

import com.shop.identity.internal.constant.IdentityTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;

@Entity
@Table(name = IdentityTableNames.PERMISSIONS)
public class Permission {

    @Id
    @Column(nullable = false, length = 100)
    private String code;

    @Column(length = 255)
    private String description;

    protected Permission() {}

    @Builder
    public Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
