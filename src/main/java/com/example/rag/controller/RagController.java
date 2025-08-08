package com.example.rag.controller;

import com.example.rag.service.impl.FilteredQuestionAnswerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("rag")
public class RagController {

    private final ChatClient chatClient;

    public RagController(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder
                .defaultAdvisors(new FilteredQuestionAnswerAdvisor(vectorStore))
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam(value = "query", defaultValue = "Give me a list of all companies which Behrooz had experience with them.") String query,
                       @RequestParam String userId, @RequestParam String conversationId) {

        UserMessage userMessage = new UserMessage(query);
        userMessage.getMetadata().put("userId", userId);
        userMessage.getMetadata().put("conversationId", conversationId);

        return chatClient
                .prompt()
                .messages(userMessage)
                .call()
                .content();
    }

//    @GetMapping("/models")
//    public Models faq(@RequestParam(value = "message", defaultValue = "Give me a list of all the models from OpenAI along with their context window.") String message) {
//        return chatClient.prompt()
//                .user(message)
//                .call()
//                .entity(Models.class);
//    }

}