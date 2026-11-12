package com.iss.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "application")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer applicationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne
    @JoinColumn(name = "stream_id", nullable = false)
    private SubjectStream stream;

    private LocalDate applicationDate;
    private String status = "APPLIED";

    private String desiredSubject1;
    private String desiredSubject2;
    private String desiredSubject3;

    public String getDesiredSubject1() { return desiredSubject1; }
    public void setDesiredSubject1(String desiredSubject1) { this.desiredSubject1 = desiredSubject1; }

    public String getDesiredSubject2() { return desiredSubject2; }
    public void setDesiredSubject2(String desiredSubject2) { this.desiredSubject2 = desiredSubject2; }

    public String getDesiredSubject3() { return desiredSubject3; }
    public void setDesiredSubject3(String desiredSubject3) { this.desiredSubject3 = desiredSubject3; }
    public Integer getApplicationId() { return applicationId; }
    public void setApplicationId(Integer applicationId) { this.applicationId = applicationId; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public School getSchool() { return school; }
    public void setSchool(School school) { this.school = school; }

    public SubjectStream getStream() { return stream; }
    public void setStream(SubjectStream stream) { this.stream = stream; }

    public LocalDate getApplicationDate() { return applicationDate; }
    public void setApplicationDate(LocalDate applicationDate) { this.applicationDate = applicationDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

}