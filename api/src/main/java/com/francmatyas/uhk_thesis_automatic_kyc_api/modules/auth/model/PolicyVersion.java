package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "policy_version")
@Getter
@Setter
public class PolicyVersion {
    @Id
    @Column(name = "singleton_id")
    private Integer id = 1; // single-row table

    @Column(nullable = false)
    private int version = 1;

    @Version
    private long optimisticLock;
}
