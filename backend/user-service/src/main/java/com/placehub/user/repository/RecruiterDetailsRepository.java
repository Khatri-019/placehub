package com.placehub.user.repository;

import com.placehub.user.entity.RecruiterDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecruiterDetailsRepository extends JpaRepository<RecruiterDetails, UUID> {
}
