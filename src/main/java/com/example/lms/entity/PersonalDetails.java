package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Personal details of any user (admin, instructor, learner...). One row per user
 * (user_id is unique + FK to users.id). Row is created the first time the user saves
 * the profile page or uploads a picture.
 *
 * First name, last name, email, country code and phone number stay in the users table
 * (they are the signup/login data).
 */
@Entity
@Table(name = "personal_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_personal_details_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "personal_address", length = 500)
    private String personalAddress;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    /** Full https Cloudinary URL. */
    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;

    /** Full https Cloudinary URL. */
    @Column(name = "background_picture_url", length = 500)
    private String backgroundPictureUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}