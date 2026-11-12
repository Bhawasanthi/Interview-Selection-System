package com.iss.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "subject_stream")
public class SubjectStream {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer streamId;

    private String streamName;

    public Integer getStreamId() { return streamId; }
    public void setStreamId(Integer streamId) { this.streamId = streamId; }

    public String getStreamName() { return streamName; }
    public void setStreamName(String streamName) { this.streamName = streamName; }
}