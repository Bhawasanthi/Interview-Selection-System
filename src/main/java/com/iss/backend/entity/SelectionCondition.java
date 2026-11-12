package com.iss.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "selection_condition")
public class SelectionCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer conditionId;

    @ManyToOne
    @JoinColumn(name = "stream_id", nullable = false)
    private SubjectStream stream;

    private Integer year;
    private String subjectName;
    private String minGrade;

    public Integer getConditionId() { return conditionId; }
    public void setConditionId(Integer conditionId) { this.conditionId = conditionId; }

    public SubjectStream getStream() { return stream; }
    public void setStream(SubjectStream stream) { this.stream = stream; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getMinGrade() { return minGrade; }
    public void setMinGrade(String minGrade) { this.minGrade = minGrade; }
}
