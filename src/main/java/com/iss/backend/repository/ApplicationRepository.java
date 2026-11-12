package com.iss.backend.repository;

import com.iss.backend.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Integer> {

    List<Application> findByStreamStreamId(Integer streamId);

    List<Application> findByStudentStudentId(Integer studentId);

    List<Application> findByStatus(String status);

    long countByStatus(String status);

    boolean existsBySchoolSchoolId(Integer schoolId);

    boolean existsByStreamStreamId(Integer streamId);

    @Query("""
    SELECT a
    FROM Application a
    JOIN FETCH a.student
    JOIN FETCH a.school
    JOIN FETCH a.stream
    WHERE a.applicationId = :id
""")
    Optional<Application> findApplicationWithDetails(@Param("id") Integer id);
}
