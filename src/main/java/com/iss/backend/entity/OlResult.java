package com.iss.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ol_result")
public class OlResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer resultId;

    @ManyToOne
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    private String subjectName;
    private String grade;

    public Integer getResultId() { return resultId; }
    public void setResultId(Integer resultId) { this.resultId = resultId; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
}
