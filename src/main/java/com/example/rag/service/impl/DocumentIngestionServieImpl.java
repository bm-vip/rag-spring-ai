package com.example.rag.service.impl;

import com.example.rag.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionServieImpl implements DocumentIngestionService {

    private final VectorStore vectorStore;

    @Override
    public void save(String url) throws IOException {
        UrlResource urlResource = new UrlResource(url);
        if (urlResource.exists()) {
            TikaDocumentReader pdfReader = new TikaDocumentReader(urlResource);
            TokenTextSplitter textSplitter = new TokenTextSplitter();
            List<Document> documents = textSplitter.apply(pdfReader.get());
            vectorStore.add(documents);
            log.info("PDF documents loaded and stored in Milvus vector store.");
        }
    }

}
