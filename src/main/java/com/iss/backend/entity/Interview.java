package com.iss.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview")
public class Interview {

    private String status = "SCHEDULED";

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer interviewId;

    @ManyToOne
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime interviewDate;
    private String venue;

    public Integer getInterviewId() { return interviewId; }
    public void setInterviewId(Integer interviewId) { this.interviewId = interviewId; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getInterviewDate() { return interviewDate; }
    public void setInterviewDate(LocalDateTime interviewDate) { this.interviewDate = interviewDate; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
}
