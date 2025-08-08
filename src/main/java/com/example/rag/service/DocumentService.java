package com.example.rag.service;

import org.springframework.ai.document.Document;

import java.io.IOException;
import java.util.List;

public interface DocumentService {
    void save(String url, String userId, String conversationId) throws IOException;
    String generateDocId(String url, String userId, String conversationId) throws IOException;
    List<Document> search(String query, String userId, String conversationId, int topK);
}
