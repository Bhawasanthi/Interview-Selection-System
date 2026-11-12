package com.iss.backend.repository;

import com.iss.backend.entity.SelectionCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SelectionConditionRepository extends JpaRepository<SelectionCondition, Integer> {

    List<SelectionCondition> findByStreamStreamIdAndYear(Integer streamId, Integer year);

    List<SelectionCondition> findByYear(Integer year);

    boolean existsByStreamStreamId(Integer streamId);

    // Derived deletes need a transaction; without it saving conditions fails
    @Transactional
    @Modifying
    void deleteByStreamStreamIdAndYear(Integer streamId, Integer year);
}
