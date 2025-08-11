package com.example.rag.service.impl;

import com.example.rag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final VectorStore vectorStore;

    @Override
    public void save(String url, String userId, String conversationId) throws IOException {
        var docId = generateDocId(url, userId, conversationId);
        // ✅ Use filterExpression to check if doc_id already exists
        boolean exists = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(docId) // using docId as a query text (can also use embedding for stronger match)
                                .topK(5)
                                .build()
                ).stream()
                .anyMatch(result -> {
                    String resultId = result.getId();
                    String baseId = resultId.contains("_")
                            ? resultId.substring(0, resultId.lastIndexOf("_"))
                            : resultId;
                    return docId.equals(baseId);
                });
        if (exists) {
            log.info("⚠️ Document already exists in Milvus (docId: {}). Skipping import.", docId);
            return;
        }

        // 🔹 Read and split PDF into chunks
        UrlResource urlResource = new UrlResource(url);
        TikaDocumentReader documentReader = new TikaDocumentReader(urlResource);
        TokenTextSplitter textSplitter = new TokenTextSplitter();
        List<Document> originalDocs = textSplitter.apply(documentReader.get());

        // 🔹 Create new documents with explicit doc_id and metadata
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < originalDocs.size(); i++) {
            Document chunk = originalDocs.get(i);
            String chunkId = (i == 0) ? docId : docId + "_" + i; // first chunk uses docId as ID

            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("source", urlResource.getFilename());
            metadata.put("userId", userId);
            metadata.put("conversationId", conversationId);

            documents.add(new Document(chunkId, chunk.getText(), metadata));
        }

        // ✅ Store in Milvus
        try {
            vectorStore.add(documents);
            log.info("✅ Document stored in Milvus vector store (docId: {}).", docId);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate")) {
                log.info("⚠️ Document already exists in Milvus (docId: {}). Skipping import.", docId);
            } else {
                log.error("❌ Failed to store document in Milvus: {}", e.getMessage(), e);
                throw e;
            }
        }
    }

    @Override
    public String generateDocId(String url, String userId, String conversationId) throws IOException {
        UrlResource urlResource = new UrlResource(url);
        if (!urlResource.exists()) {
            log.warn("file not found at URL: {}", url);
            return null;
        }
        // 🔹 Calculate file hash (used as doc_id)
        String fileHash;
        try (InputStream is = urlResource.getInputStream()) {
            fileHash = DigestUtils.sha256Hex(is);
        }
        String combined = String.format("%s:%s:%s", userId.toString(), conversationId.toString(), fileHash);
        return DigestUtils.sha256Hex(combined); // 64-character hex string
    }

    @Override
    public List<Document> search(String query, String userId, String conversationId, int topK) {
        // Build filter expression dynamically to handle null conversationId
        StringBuilder filterExpression = new StringBuilder();
        filterExpression.append("userId == '").append(userId.replace("'", "\\'")).append("'");

        if (conversationId != null && !conversationId.isEmpty()) {
            filterExpression.append(" and conversationId == '")
                    .append(conversationId.replace("'", "\\'"))
                    .append("'");
        }

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filterExpression.toString())
                .build();

        return vectorStore.similaritySearch(searchRequest);
    }
}
