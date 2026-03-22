package com.francmatyas.uhk_thesis_automatic_kyc_api.model;

import jakarta.persistence.*;
import jakarta.persistence.Column;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
public class Tag extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String key;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private TagKind kind;

    @Column
    private String description;

    @Column
    private String color;

    @Column
    private String icon;

    /*@OneToMany(mappedBy = "tag", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SimulationTag> simulationTags = new ArrayList<>();*/
}
