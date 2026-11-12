package com.iss.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "interview_mark")
public class InterviewMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer markId;

    @ManyToOne
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    private String criteria;
    private BigDecimal score;

    public Integer getMarkId() { return markId; }
    public void setMarkId(Integer markId) { this.markId = markId; }

    public Interview getInterview() { return interview; }
    public void setInterview(Interview interview) { this.interview = interview; }

    public String getCriteria() { return criteria; }
    public void setCriteria(String criteria) { this.criteria = criteria; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
}