package com.placehub.user.repository;

import com.placehub.user.entity.StudentDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentDetailsRepository extends JpaRepository<StudentDetails, UUID> {
}
