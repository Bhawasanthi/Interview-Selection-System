package com.iss.backend.repository;

import com.iss.backend.entity.CutoffRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CutoffRecordRepository extends JpaRepository<CutoffRecord, Integer> {

    CutoffRecord findByStreamStreamIdAndYear(Integer streamId, Integer year);

    List<CutoffRecord> findByYear(Integer year);

    boolean existsBySchoolSchoolId(Integer schoolId);

    boolean existsByStreamStreamId(Integer streamId);
}
