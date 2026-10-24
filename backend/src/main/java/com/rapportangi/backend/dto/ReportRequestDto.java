package com.rapportangi.backend.dto;

public class ReportRequestDto {
    private String title;
    private String content;
    private String status;
    private Long authorId;

    // Constructeurs
    public ReportRequestDto() {}

    public ReportRequestDto(String title, String content, String status, Long authorId) {
        this.title = title;
        this.content = content;
        this.status = status;
        this.authorId = authorId;
    }

    // Getters et Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
}