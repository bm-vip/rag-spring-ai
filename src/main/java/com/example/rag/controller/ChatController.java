package com.example.rag.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("chat")
public class ChatController {
    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/content")
    public String content() {
        return chatClient.prompt()
                .user("Hi, tell me an interesting fact about Java")
                .call()
                .content();
    }

    @GetMapping("/stream")
    public Flux<String> stream() {
        return chatClient.prompt()
                .user("Hi, tell me an interesting fact about Java")
                .stream()
                .content();
    }

    @GetMapping("/response")
    public ChatResponse response() {
        return chatClient.prompt()
                .user("Hi, tell me an interesting fact about Java")
                .call()
                .chatResponse();
    }
}
