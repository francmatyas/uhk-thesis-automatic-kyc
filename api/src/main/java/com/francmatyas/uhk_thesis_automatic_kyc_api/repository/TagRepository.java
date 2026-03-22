package com.francmatyas.uhk_thesis_automatic_kyc_api.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
    Optional<Tag> findByKey(String key);
}

