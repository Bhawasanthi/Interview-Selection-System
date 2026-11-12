package com.iss.backend.service;

import com.iss.backend.entity.*;
import com.iss.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InterviewMarkingService {

    public static final String INTERVIEW_ASSESSMENT = "INTERVIEW_ASSESSMENT";
    public static final String DOCUMENTS_ELIGIBILITY = "DOCUMENTS_ELIGIBILITY";

    /** Six core subjects plus one from each of the three baskets. */
    public static final int OL_SUBJECT_COUNT = 9;

    /** Option label used for criteria the panel scores directly (up to a maximum). */
    public static final String MAX = "MAX";

    /** Directly scored criteria and their default maximum marks. */
    public static final Map<String, BigDecimal> SCORED_CRITERIA = new LinkedHashMap<>();
    static {
        SCORED_CRITERIA.put(INTERVIEW_ASSESSMENT, BigDecimal.valueOf(10));
        SCORED_CRITERIA.put(DOCUMENTS_ELIGIBILITY, BigDecimal.valueOf(5));
    }

    @Autowired private MarkingConfigRepository markingConfigRepository;
    @Autowired private InterviewMarkRepository interviewMarkRepository;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private ApplicationRepository applicationRepository;

    /** Adds the directly scored criteria to every marking scale saved before they existed. */
    @EventListener(ApplicationReadyEvent.class)
    public void addMissingScoredCriteria() {
        markingConfigRepository.findAll().stream()
                .map(MarkingConfig::getYear).distinct()
                .forEach(this::ensureScoredCriteria);
    }

    public void ensureScoredCriteria(Integer year) {
        SCORED_CRITERIA.forEach((type, defaultMax) -> {
            if (markingConfigRepository.findByYearAndCriterionTypeAndOptionLabel(year, type, MAX) == null) {
                MarkingConfig c = new MarkingConfig();
                c.setYear(year);
                c.setCriterionType(type);
                c.setOptionLabel(MAX);
                c.setMarks(defaultMax);
                markingConfigRepository.save(c);
            }
        });
    }

    /**
     * The marking scale year to use for an interview held in the given year:
     * that year's scale, otherwise the latest earlier scale, otherwise the latest scale.
     * Returns null when no marking scale has been set up at all.
     */
    public Integer scaleYear(int interviewYear) {
        List<Integer> years = markingConfigRepository.findAll().stream()
                .map(MarkingConfig::getYear).distinct().sorted(Comparator.reverseOrder()).toList();
        if (years.isEmpty()) return null;
        return years.stream().filter(y -> y <= interviewYear).findFirst().orElse(years.get(0));
    }

    /** Maximum marks for a directly scored criterion in a scale year. */
    public BigDecimal maxMarks(Integer year, String type) {
        if (year == null) return SCORED_CRITERIA.get(type);
        MarkingConfig c = markingConfigRepository.findByYearAndCriterionTypeAndOptionLabel(year, type, MAX);
        return c != null ? c.getMarks() : SCORED_CRITERIA.get(type);
    }

    private BigDecimal configMarks(Integer year, String type, String option) {
        MarkingConfig config = markingConfigRepository.findByYearAndCriterionTypeAndOptionLabel(year, type, option);
        return config != null ? config.getMarks() : BigDecimal.ZERO;
    }

    /**
     * Saves the panel's marks for one interview (replacing any earlier marks).
     *
     * @return the marking scale year that was used
     * @throws IllegalArgumentException when a mark is out of range or no marking scale exists
     */
    @Transactional
    public int saveInterviewMarks(Integer interviewId,
                                  int countA, int countB, int countC, int countS, int countW,
                                  String gnApproval, String alAvailable,
                                  String oneSubjectAvailable, String twoSubjectAvailable,
                                  String extracurricular,
                                  BigDecimal interviewAssessment, BigDecimal documentsEligibility) {

        Interview interview = interviewRepository.findById(interviewId).orElseThrow();

        String appStatus = interview.getApplication().getStatus();
        if ("SELECTED".equals(appStatus) || "NOT_SELECTED".equals(appStatus)) {
            throw new IllegalArgumentException("The final selection has already been made for this student, so the marks are locked.");
        }

        Integer year = scaleYear(interview.getInterviewDate().getYear());
        if (year == null) {
            throw new IllegalArgumentException("No marking scale has been set up. Ask the administrator to set it in System Settings.");
        }
        ensureScoredCriteria(year);

        if (countA < 0 || countB < 0 || countC < 0 || countS < 0 || countW < 0) {
            throw new IllegalArgumentException("O/L grade counts cannot be negative.");
        }
        if (countA + countB + countC + countS + countW > OL_SUBJECT_COUNT) {
            throw new IllegalArgumentException("The O/L grade counts add up to more than " + OL_SUBJECT_COUNT + " subjects.");
        }
        checkRange("Interview assessment", interviewAssessment, maxMarks(year, INTERVIEW_ASSESSMENT));
        checkRange("Supporting documents & eligibility", documentsEligibility, maxMarks(year, DOCUMENTS_ELIGIBILITY));

        // If A/L is not offered at the O/L school, none of the applied subjects can be either
        if ("NO".equals(alAvailable)) {
            oneSubjectAvailable = "NO";
            twoSubjectAvailable = "NO";
        }

        // clear any existing marks for this interview (allows re-marking)
        interviewMarkRepository.deleteAll(interviewMarkRepository.findByInterviewInterviewId(interviewId));

        BigDecimal olTotal = configMarks(year, "OL_RESULT", "A").multiply(BigDecimal.valueOf(countA))
                .add(configMarks(year, "OL_RESULT", "B").multiply(BigDecimal.valueOf(countB)))
                .add(configMarks(year, "OL_RESULT", "C").multiply(BigDecimal.valueOf(countC)))
                .add(configMarks(year, "OL_RESULT", "S").multiply(BigDecimal.valueOf(countS)))
                .add(configMarks(year, "OL_RESULT", "W").multiply(BigDecimal.valueOf(countW)));

        save(interview, "OL_RESULT", olTotal);
        save(interview, "GN_APPROVAL", configMarks(year, "GN_APPROVAL", gnApproval));
        save(interview, "AL_AVAILABLE", configMarks(year, "AL_AVAILABLE", alAvailable));
        save(interview, "ONE_SUBJECT_AVAILABLE", configMarks(year, "ONE_SUBJECT_AVAILABLE", oneSubjectAvailable));
        save(interview, "TWO_SUBJECT_AVAILABLE", configMarks(year, "TWO_SUBJECT_AVAILABLE", twoSubjectAvailable));
        save(interview, "EXTRACURRICULAR", configMarks(year, "EXTRACURRICULAR", extracurricular));
        save(interview, INTERVIEW_ASSESSMENT, interviewAssessment);
        save(interview, DOCUMENTS_ELIGIBILITY, documentsEligibility);

        interview.setStatus("COMPLETED");
        interviewRepository.save(interview);

        Application app = interview.getApplication();
        app.setStatus("INTERVIEWED");
        applicationRepository.save(app);
        return year;
    }

    private static void checkRange(String label, BigDecimal value, BigDecimal max) {
        if (value == null || value.signum() < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(label + " must be between 0 and " + max.stripTrailingZeros().toPlainString() + ".");
        }
    }

    private void save(Interview interview, String criteria, BigDecimal score) {
        InterviewMark mark = new InterviewMark();
        mark.setInterview(interview);
        mark.setCriteria(criteria);
        mark.setScore(score);
        interviewMarkRepository.save(mark);
    }

    public BigDecimal getTotalScore(Integer interviewId) {
        return interviewMarkRepository.findByInterviewInterviewId(interviewId).stream()
                .map(InterviewMark::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
