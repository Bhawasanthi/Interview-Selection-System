package com.iss.backend.service;

import com.iss.backend.entity.*;
import com.iss.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SelectionService {

    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private OlResultRepository olResultRepository;
    @Autowired private SelectionConditionRepository conditionRepository;

    // A is the best grade, W is the weakest
    private static final Map<String, Integer> GRADE_RANK = Map.of(
            "A", 5, "B", 4, "C", 3, "S", 2, "W", 1
    );

    /**
     * Runs the shortlisting logic for one stream + year.
     * A student passes only if EVERY condition set for that
     * stream/year is met (their grade is equal to or better
     * than the minimum required).
     */
    public int runSelection(Integer streamId, Integer year) {
        List<SelectionCondition> conditions = conditionRepository.findByStreamStreamIdAndYear(streamId, year);
        List<Application> applications = applicationRepository.findByStreamStreamId(streamId);

        int shortlistedCount = 0;
        for (Application app : applications) {
            if (!"APPLIED".equals(app.getStatus())
                    && !"SHORTLISTED".equals(app.getStatus())
                    && !"NOT_QUALIFIED".equals(app.getStatus())) {
                continue;
            }
            List<OlResult> results = olResultRepository.findByApplicationApplicationId(app.getApplicationId());
            boolean passesAll = true;
            for (SelectionCondition condition : conditions) {
                String actualGrade = results.stream()
                        .filter(r -> r.getSubjectName().equalsIgnoreCase(condition.getSubjectName()))
                        .map(OlResult::getGrade)
                        .findFirst()
                        .orElse("W");
                int actualRank = GRADE_RANK.getOrDefault(actualGrade, 0);
                int requiredRank = GRADE_RANK.getOrDefault(condition.getMinGrade(), 0);
                if (actualRank < requiredRank) {
                    passesAll = false;
                    break;
                }
            }

            app.setStatus(passesAll ? "SHORTLISTED" : "NOT_QUALIFIED");
            applicationRepository.save(app);
            if (passesAll) shortlistedCount++;
        }

        return shortlistedCount;
    }
}