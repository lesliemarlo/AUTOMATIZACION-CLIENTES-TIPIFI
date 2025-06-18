package com.informaperu.cliente.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class BatchState {
    @Id
    private String batchId;
    private LocalDateTime lastProcessedStart;
    private LocalDateTime lastProcessedEnd;
    private boolean completed;

    // Getters and Setters
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public LocalDateTime getLastProcessedStart() { return lastProcessedStart; }
    public void setLastProcessedStart(LocalDateTime lastProcessedStart) { this.lastProcessedStart = lastProcessedStart; }
    public LocalDateTime getLastProcessedEnd() { return lastProcessedEnd; }
    public void setLastProcessedEnd(LocalDateTime lastProcessedEnd) { this.lastProcessedEnd = lastProcessedEnd; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
