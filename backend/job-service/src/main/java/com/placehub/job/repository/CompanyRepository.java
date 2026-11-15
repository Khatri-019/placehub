package com.placehub.job.repository;

import com.placehub.job.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByRecruiterOwnerUserId(UUID recruiterOwnerUserId);

    boolean existsByRecruiterOwnerUserId(UUID recruiterOwnerUserId);
}
