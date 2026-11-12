package com.iss.backend.repository;
import java.util.List;
import com.iss.backend.entity.InterviewMark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewMarkRepository extends JpaRepository<InterviewMark, Integer> {
    List<InterviewMark> findByInterviewInterviewId(Integer interviewId);
}
