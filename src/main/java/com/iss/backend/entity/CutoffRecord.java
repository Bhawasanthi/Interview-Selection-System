package com.iss.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cutoff_record")
public class CutoffRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer cutoffId;

    private Integer totalCapacity;
    private Integer internalStudentsCount;

    public Integer getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(Integer totalCapacity) { this.totalCapacity = totalCapacity; }

    public Integer getInternalStudentsCount() { return internalStudentsCount; }
    public void setInternalStudentsCount(Integer internalStudentsCount) { this.internalStudentsCount = internalStudentsCount; }

    @ManyToOne
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne
    @JoinColumn(name = "stream_id", nullable = false)
    private SubjectStream stream;

    private Integer year;
    private Integer seatsAvailable;
    private BigDecimal cutoffMark;

    public Integer getCutoffId() { return cutoffId; }
    public void setCutoffId(Integer cutoffId) { this.cutoffId = cutoffId; }

    public School getSchool() { return school; }
    public void setSchool(School school) { this.school = school; }

    public SubjectStream getStream() { return stream; }
    public void setStream(SubjectStream stream) { this.stream = stream; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getSeatsAvailable() { return seatsAvailable; }
    public void setSeatsAvailable(Integer seatsAvailable) { this.seatsAvailable = seatsAvailable; }

    public BigDecimal getCutoffMark() { return cutoffMark; }
    public void setCutoffMark(BigDecimal cutoffMark) { this.cutoffMark = cutoffMark; }
}
