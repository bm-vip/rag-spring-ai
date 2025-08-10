package com.example.rag.service.impl;

import ch.helvethink.odoo4java.models.OdooId;
import ch.helvethink.odoo4java.rpc.OdooObjectLoader;
import ch.helvethink.odoo4java.xmlrpc.OdooClient;
import com.example.rag.models.project.Project;
import com.example.rag.models.project.ProjectTask;
import com.example.rag.models.res.ResUsers;
import com.example.rag.models.timesheets.analysis.TimesheetsAnalysisReport;
import com.example.rag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.xmlrpc.XmlRpcException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.*;
import java.util.stream.Collectors;

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

    public static final String USERNAME = "info@tunoo.de";
    public static final String DBNAME = "tunoo";
    public static final String PASSWORD = "Tunoo#2025";
    public static final String ODOO_URL = "https://crm.tunoo.de";
    public static void main(String[] args) throws MalformedURLException, XmlRpcException {
        OdooClient cli = new OdooClient(ODOO_URL, DBNAME, USERNAME, PASSWORD, true);
        OdooObjectLoader loader = new OdooObjectLoader(cli);

        OdooId idToFetch = new OdooId();
        idToFetch.id = 2;

        // Fetch a single Object by Odoo ID
        Project project = cli.findObjectById(idToFetch, Project.class);
        log.info(project.getDisplayName());

        OdooId id2fetch = new OdooId();
        id2fetch.id = 3;
        // Fetch multiple objects by Odoo IDs
        List<Project> projects = cli.findListByIds(Arrays.asList(idToFetch, id2fetch), Project.class);
        log.info(projects.stream().map(pt -> pt.getDisplayName()).collect(Collectors.joining(",")));

        // Fetch relationships for an odoo object, not recursively, filtering classes we want to fetch

        loader.fetchRelationShips(project, Arrays.asList(ProjectTask.class, ResUsers.class));

        log.info("1) Find by criteria 'equals' - {}", cli.findByCriteria(1, Project.class, "name", "=", "Sample Project").stream().map(a -> a.getName()).collect(Collectors.joining(",")));
        log.info("2) Find by criteria 'like' - {}", cli.findByCriteria(1, Project.class, "name", "like", "%Sample%").stream().map(a -> a.getName()).collect(Collectors.joining(",")));
        log.info("3) Find by criteria limit 1 without criterion - {}", cli.findByCriteria(1, Project.class).stream().map(a -> a.getName()).collect(Collectors.joining(",")));
        log.info("4) Find by criteria id equals - {}", cli.findByCriteria(1, Project.class, "id", "=", "1").stream().map(a -> a.getName()).collect(Collectors.joining(",")));

        // Find a list of objects using search criteria, with a limit specified - the first parameter, here 1.
        // If no criteria is specified then everything will be fetched.
        List<TimesheetsAnalysisReport> timesheet = cli.findByCriteria(1, TimesheetsAnalysisReport.class);
        log.info("5) Find by criteria limit 1 without criterion - {}", timesheet.stream().map(a -> a.getName()).collect(Collectors.joining(",")));

        // If 0, then will fetch all objects.
        List<TimesheetsAnalysisReport> ts = cli.findByCriteria(0, TimesheetsAnalysisReport.class);
        log.info("6) Find by criteria no limit without criterion - {}", ts.stream().map(a -> a.getName()).collect(Collectors.joining(",")));

        // Fetch recursively with depth = 2
        final TimesheetsAnalysisReport firstAccAnalyticLine = ts.get(ts.size() - 1);
        System.out.println(firstAccAnalyticLine.getName());
        loader.fetchRecursivelyRelationShips(firstAccAnalyticLine, 2, Collections.emptyList());
        // Check that we fetched Currency Too
        log.info(firstAccAnalyticLine.getCompanyIdAsObject().getName());
        log.info(firstAccAnalyticLine.getCompanyIdAsObject().getCurrencyIdAsObject().getDisplayName());

        // Fetch using the criterion name like %Sample%
        List<Project> sampleProjects = cli.findByCriteria(1, Project.class, "name", "like", "%Sample%");
        loader.fetchRelationShips(sampleProjects.get(0), Collections.emptyList());
        log.info(sampleProjects.get(0).getTasksAsList().get(0).getDisplayName());

        // Fetch using the criterion name like %Sample%
//        sampleProjects = cli.findByCriteria(1, Project.class, "name", "like", "%Sample%");
//        loader.fetchRelationShips(sampleProjects.getFirst(), Collections.emptyList());
//        log.info(sampleProjects.getFirst().getTasksAsList().get(0).getDisplayName());
    }
}
