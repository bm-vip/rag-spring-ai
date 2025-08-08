package com.example.rag;

import com.example.rag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;

@SpringBootApplication
@RequiredArgsConstructor
public class RagApplication {

	private final DocumentService documentService;

	public static void main(String[] args) {
		SpringApplication.run(RagApplication.class, args);
	}

//	@EventListener(ApplicationReadyEvent.class)
//	public void init() {
//        try {
//            documentService.save("classpath:/data/BehroozMohamadi.pdf", "eef3fa4c-7dfa-4089-90d8-4481bce98b97","ad0d66c6-0a18-45f4-8d6a-a6afd86389ce");
//            documentService.save("classpath:/data/BackendInterviewsQuestions.pdf", "eef3fa4c-7dfa-4089-90d8-4481bce98b97","899090e6-5f0e-4105-b90d-2612b5021bb2");
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

}
