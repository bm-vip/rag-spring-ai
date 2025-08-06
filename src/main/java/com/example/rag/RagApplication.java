package com.example.rag;

import com.example.rag.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.net.URL;

@SpringBootApplication
@RequiredArgsConstructor
public class RagApplication {

	private final DocumentIngestionService documentIngestionService;

	public static void main(String[] args) {
		SpringApplication.run(RagApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
        try {
            documentIngestionService.save("classpath:/data/BehroozMohamadi.pdf");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
