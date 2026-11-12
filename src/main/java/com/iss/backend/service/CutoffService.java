package com.iss.backend.service;

import com.iss.backend.entity.*;
import com.iss.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CutoffService {

    @Autowired private CutoffRecordRepository cutoffRecordRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private InterviewMarkingService markingService;

    public void setCapacity(SubjectStream stream, School school, Integer year,
                            Integer totalCapacity, Integer internalStudentsCount) {

        CutoffRecord record = cutoffRecordRepository.findByStreamStreamIdAndYear(stream.getStreamId(), year);
        if (record == null) {
            record = new CutoffRecord();
            record.setStream(stream);
            record.setSchool(school);
            record.setYear(year);
        }
        record.setTotalCapacity(totalCapacity);
        record.setInternalStudentsCount(internalStudentsCount);
        record.setSeatsAvailable(totalCapacity - internalStudentsCount);
        cutoffRecordRepository.save(record);
    }

    /**
     * Ranks all interviewed applications for this stream by their
     * total interview score, selects the top N (N = seats available),
     * and records the cutoff mark as the score of the last selected
     * student.
     */
    public String runCutoffSelection(Integer streamId, Integer year) {

        CutoffRecord record = cutoffRecordRepository.findByStreamStreamIdAndYear(streamId, year);
        if (record == null || record.getSeatsAvailable() == null) {
            return "Please set the seat capacity for this stream and year first.";
        }
        int seats = record.getSeatsAvailable();
        List<Application> interviewed = applicationRepository.findByStreamStreamId(streamId).stream()
                .filter(a -> "INTERVIEWED".equals(a.getStatus()))
                .toList();
        // pair each application with its total interview score
        Map<Application, BigDecimal> scored = new LinkedHashMap<>();
        for (Application app : interviewed) {
            List<Interview> interviews = interviewRepository.findByApplicationApplicationId(app.getApplicationId());
            BigDecimal total = interviews.isEmpty() ? BigDecimal.ZERO
                    : markingService.getTotalScore(interviews.get(0).getInterviewId());
            scored.put(app, total);
        }
        List<Application> ranked = scored.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        BigDecimal cutoffMark = BigDecimal.ZERO;
        for (int i = 0; i < ranked.size(); i++) {
            Application app = ranked.get(i);
            if (i < seats) {
                app.setStatus("SELECTED");
                cutoffMark = scored.get(app); // score of the last selected = cutoff
            } else {
                app.setStatus("NOT_SELECTED");
            }
            applicationRepository.save(app);
        }
        record.setCutoffMark(cutoffMark);
        cutoffRecordRepository.save(record);

        return "Selection complete. " + Math.min(seats, ranked.size()) + " students selected. Cutoff mark: " + cutoffMark;
    }
}