package de.toengi.cili.model.entity;

import de.toengi.cili.model.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "acl_entries")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AclEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private AclSubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private AclResourceType resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AclPermission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_type", nullable = false)
    @Builder.Default
    private AclGrantType grantType = AclGrantType.ALLOW;

    @Column(name = "is_inheritable", nullable = false)
    @Builder.Default
    private boolean inheritable = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
