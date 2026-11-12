package com.iss.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "marking_config")
public class MarkingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer configId;

    private Integer year;
    private String criterionType;
    private String optionLabel;
    private BigDecimal marks;

    public Integer getConfigId() { return configId; }
    public void setConfigId(Integer configId) { this.configId = configId; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getCriterionType() { return criterionType; }
    public void setCriterionType(String criterionType) { this.criterionType = criterionType; }

    public String getOptionLabel() { return optionLabel; }
    public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }

    public BigDecimal getMarks() { return marks; }
    public void setMarks(BigDecimal marks) { this.marks = marks; }
}