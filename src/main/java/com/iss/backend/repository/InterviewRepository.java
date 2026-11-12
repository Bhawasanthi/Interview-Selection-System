package com.iss.backend.repository;

import com.iss.backend.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Integer> {

    List<Interview> findByApplicationApplicationId(Integer applicationId);

    List<Interview> findByApplicationStreamStreamId(Integer streamId);

    long countByStatus(String status);

    boolean existsByUserUserId(Integer userId);
}
