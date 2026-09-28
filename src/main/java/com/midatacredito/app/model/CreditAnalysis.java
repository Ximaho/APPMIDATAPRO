package com.midatacredito.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Registro histórico de un análisis crediticio realizado por un usuario.
 * Las listas de problemas y recomendaciones se guardan serializadas en JSON y los
 * textos largos como VARCHAR amplios para mantener el esquema portable entre H2 y PostgreSQL.
 */
@Entity
@Table(name = "credit_analyses")
public class CreditAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "activity_description", nullable = false, length = 4000)
    private String activityDescription;

    @Column(name = "image_file_name", length = 255)
    private String imageFileName;

    @Column(name = "image_media_type", length = 20)
    private String imageMediaType;

    @Column(name = "estimated_score", nullable = false)
    private Integer estimatedScore;

    @Column(nullable = false, length = 20000)
    private String summary;

    @Column(name = "problems_json", nullable = false, length = 20000)
    private String problemsJson;

    @Column(name = "recommendations_json", nullable = false, length = 20000)
    private String recommendationsJson;

    @Column(name = "model_used", length = 60)
    private String modelUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getActivityDescription() {
        return activityDescription;
    }

    public void setActivityDescription(String activityDescription) {
        this.activityDescription = activityDescription;
    }

    public String getImageFileName() {
        return imageFileName;
    }

    public void setImageFileName(String imageFileName) {
        this.imageFileName = imageFileName;
    }

    public String getImageMediaType() {
        return imageMediaType;
    }

    public void setImageMediaType(String imageMediaType) {
        this.imageMediaType = imageMediaType;
    }

    public Integer getEstimatedScore() {
        return estimatedScore;
    }

    public void setEstimatedScore(Integer estimatedScore) {
        this.estimatedScore = estimatedScore;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getProblemsJson() {
        return problemsJson;
    }

    public void setProblemsJson(String problemsJson) {
        this.problemsJson = problemsJson;
    }

    public String getRecommendationsJson() {
        return recommendationsJson;
    }

    public void setRecommendationsJson(String recommendationsJson) {
        this.recommendationsJson = recommendationsJson;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
