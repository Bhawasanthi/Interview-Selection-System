package com.iss.backend.repository;

import com.iss.backend.entity.MarkingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MarkingConfigRepository extends JpaRepository<MarkingConfig, Integer> {
    List<MarkingConfig> findByYear(Integer year);
    MarkingConfig findByYearAndCriterionTypeAndOptionLabel(Integer year, String criterionType, String optionLabel);
}