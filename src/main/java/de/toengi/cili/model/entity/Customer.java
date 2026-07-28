package de.toengi.cili.model.entity;

import de.toengi.cili.model.enums.Gender;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sponsor_user_id", "email"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "mobile_phone", length = 50)
    private String mobilePhone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "member_id")
    private Integer memberId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(name = "informal_address")
    private Boolean informalAddress;

    @Column(name = "sponsor_user_id", nullable = false)
    private Long sponsorUserId;

    @Column(name = "consent_granted", nullable = false)
    @Builder.Default
    private boolean consentGranted = true;

    @Column(name = "consent_granted_at", nullable = false)
    private LocalDateTime consentGrantedAt;

    @Column(name = "consent_revoked_at")
    private LocalDateTime consentRevokedAt;

    @Column(name = "unsubscribe_token", nullable = false, unique = true, length = 36)
    private String unsubscribeToken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
