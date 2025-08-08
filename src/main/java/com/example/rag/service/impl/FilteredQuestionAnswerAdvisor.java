package com.example.rag.service.impl;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.Map;

public class FilteredQuestionAnswerAdvisor extends QuestionAnswerAdvisor {

    public FilteredQuestionAnswerAdvisor(VectorStore vectorStore) {
        super(vectorStore);
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        if (chatClientRequest.prompt().getUserMessage().getMetadata().containsKey("userId"))
            chatClientRequest.context().putAll(chatClientRequest.prompt().getUserMessage().getMetadata());
        return super.before(chatClientRequest, advisorChain);
    }

    @Nullable
    @Override
    protected Filter.Expression doGetFilterExpression(Map<String, Object> context) {
        String userId = context.containsKey("userId") ? context.get("userId").toString() : null;
        String conversationId = context.containsKey("conversationId") ? context.get("conversationId").toString() : null;

        Filter.Expression filterExpression = null;

        if (StringUtils.hasText(userId) && StringUtils.hasText(conversationId)) {
            filterExpression = new Filter.Expression(
                    Filter.ExpressionType.AND,
                    new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("userId"), new Filter.Value(userId)),
                    new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("conversationId"), new Filter.Value(conversationId))
            );
        } else if (StringUtils.hasText(userId)) {
            filterExpression = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("userId"), new Filter.Value(userId));
        } else if (StringUtils.hasText(conversationId)) {
            filterExpression = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("conversationId"), new Filter.Value(conversationId));
        }

        if (filterExpression != null && context.containsKey(FILTER_EXPRESSION) && StringUtils.hasText(context.get(FILTER_EXPRESSION).toString())) {
            String existingFilter = context.get(FILTER_EXPRESSION).toString();
            Filter.Expression parsedExistingFilter = new FilterExpressionTextParser().parse(existingFilter);
            filterExpression = new Filter.Expression(Filter.ExpressionType.AND, filterExpression, parsedExistingFilter);
        }

        return filterExpression != null ? filterExpression : super.doGetFilterExpression(context);
    }
}