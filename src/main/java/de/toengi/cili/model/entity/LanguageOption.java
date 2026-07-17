package de.toengi.cili.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "language_options")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LanguageOption {

    @Id
    @Column(length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "translation_supported", nullable = false)
    private boolean translationSupported;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;
}
