package com.tongkey.domain.entity;

import com.tongkey.domain.EntityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 用户（规格文档 4.1）。
 */
@Entity
@Table(name = "tk_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
        indexes = {
                @Index(name = "idx_user_external", columnList = "source_id, external_key"),
                @Index(name = "idx_user_updated", columnList = "updated_at")
        })
public class UserEntity extends TraceableEntity {

    @Column(nullable = false, length = 128)
    private String username;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntityStatus status = EntityStatus.ENABLED;

    @Column(length = 255)
    private String password;

    @Column(length = 32)
    private String gender;

    @Column(length = 255)
    private String department;

    @Column(length = 255)
    private String position;

    @Column(length = 32)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    // ---- getter / setter ----

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public EntityStatus getStatus() {
        return status;
    }

    public void setStatus(EntityStatus status) {
        this.status = status;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
