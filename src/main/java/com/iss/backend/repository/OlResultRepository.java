package com.iss.backend.repository;
import java.util.List;
import com.iss.backend.entity.OlResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OlResultRepository extends JpaRepository<OlResult, Integer> {

    List<OlResult> findByApplicationApplicationId(Integer applicationId);
}
