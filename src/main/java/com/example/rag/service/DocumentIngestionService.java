package com.example.rag.service;

import java.io.IOException;

public interface DocumentIngestionService {
    void save(String url) throws IOException;
}
