package com.example.rag.service.impl;

import com.example.rag.service.DocumentIngestionService;
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
public class DocumentIngestionServieImpl implements DocumentIngestionService {

    private final VectorStore vectorStore;

    @Override
    public void save(String url) throws IOException {
        UrlResource urlResource = new UrlResource(url);
        if (!urlResource.exists()) {
            log.warn("PDF not found at URL: {}", url);
            return;
        }

        // 🔹 Calculate file hash (used as doc_id)
        String fileHash;
        try (InputStream is = urlResource.getInputStream()) {
            fileHash = DigestUtils.sha256Hex(is);
        }

        // ✅ Use filterExpression to check if doc_id already exists
        boolean exists = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(fileHash) // using hash as a query text (can also use embedding for stronger match)
                                .topK(1)
                                .build()
                ).stream()
                .anyMatch(result -> fileHash.equals(result.getId()));
        if (exists) {
            log.info("⚠️ Document already exists in Milvus (hash: {}). Skipping import.", fileHash);
            return;
        }

        // 🔹 Read and split PDF into chunks
        TikaDocumentReader pdfReader = new TikaDocumentReader(urlResource);
        TokenTextSplitter textSplitter = new TokenTextSplitter();
        List<Document> originalDocs = textSplitter.apply(pdfReader.get());

        // 🔹 Create new documents with explicit doc_id and metadata
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < originalDocs.size(); i++) {
            Document chunk = originalDocs.get(i);
            String chunkId = (i == 0) ? fileHash : fileHash + "_" + i; // first chunk uses fileHash as ID

            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("source", urlResource.getFilename());

            documents.add(new Document(chunkId, chunk.getText(), metadata));
        }

        // ✅ Store in Milvus
        try {
            vectorStore.add(documents);
            log.info("✅ PDF stored in Milvus vector store (hash: {}).", fileHash);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate")) {
                log.info("⚠️ Document already exists in Milvus (hash: {}). Skipping import.", fileHash);
            } else {
                log.error("❌ Failed to store document in Milvus: {}", e.getMessage(), e);
                throw e;
            }
        }
    }
}
