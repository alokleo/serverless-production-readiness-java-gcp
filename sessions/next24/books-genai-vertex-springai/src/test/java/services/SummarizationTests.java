package services;

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatClient;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

@SpringBootTest
@ActiveProfiles(value = "test")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
@EnabledIfEnvironmentVariable(named = "MODEL", matches = ".*")
public class SummarizationTests {

    @Autowired
    private VertexAiGeminiChatClient chatClient;

    @Value("classpath:/prompts/system-message.st")
    private Resource systemResource;

    @Value("classpath:/prompts/system-summary-message.st")
    private Resource systemSummaryResource;

    @Value("classpath:/prompts/initial-message.st")
    private Resource initialResource;
    @Value("classpath:/prompts/refine-message.st")
    private Resource resourceResource;

    @Value("classpath:/books/The_Wasteland-TSEliot-public.txt")
    private Resource resource;

    @Value("classpath:/prompts/subsummary-message.st")
    private Resource subsummaryResource;
    @Value("classpath:/prompts/summary-message.st")
    private Resource summaryResource;

    @Test
    public void stuffTest(){
        TextReader textReader = new TextReader(resource);
        String bookTest = textReader.get().getFirst().getContent();
        // System.out.println(bookTest);

        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Gemini", "voice", "literary critic"));


        PromptTemplate userPromptTemplate = new PromptTemplate(initialResource,Map.of("content", bookTest));
        Message userMessage = userPromptTemplate.createMessage();

        long start = System.currentTimeMillis();
        ChatResponse response = chatClient.call(new Prompt(List.of(userMessage, systemMessage),
            VertexAiGeminiChatOptions.builder()
                .withTemperature(0.4f)
                .build()));

        System.out.println(response.getResult().getOutput().getContent());
        System.out.print("Summarization took " + (System.currentTimeMillis() - start) + " milliseconds");
    }

    @Test
    public void summarizationTest(){
        TextReader textReader = new TextReader(resource);
        String bookTest = textReader.get().getFirst().getContent();

        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemSummaryResource);
        Message systemMessage = systemPromptTemplate.createMessage();

        int chunkSize = 25000;
        int length = bookTest.length();
        String subcontext = "";
        String context = "";
        for (int i = 0; i < length; i += chunkSize) {
            int end = Math.min(i + chunkSize, length);
            String chunk = bookTest.substring(i, end);

            // Process the chunk here
            subcontext = processChunk(context, chunk, systemMessage);
            context += "\n"+subcontext;
            System.out.println(subcontext+"\n\n\n\n\n");
        }

        System.out.println(context+"\n\n\n\n\n");
        PromptTemplate userPromptTemplate = new PromptTemplate(summaryResource,Map.of("content", context));
        Message userMessage = userPromptTemplate.createMessage();

        long start = System.currentTimeMillis();
        ChatResponse response = chatClient.call(new Prompt(List.of(userMessage, systemMessage),
                VertexAiGeminiChatOptions.builder()
                        .withTemperature(0.4f)
                        .build()));

        System.out.println(response.getResult().getOutput().getContent());
        System.out.print("Summarization took " + (System.currentTimeMillis() - start) + " milliseconds");
    }

    private String processChunk(String context, String chunk, Message systemMessage) {
        PromptTemplate userPromptTemplate = new PromptTemplate(subsummaryResource,Map.of("context", context,"content", chunk));
        Message userMessage = userPromptTemplate.createMessage();

        ChatResponse response = chatClient.call(new Prompt(List.of(userMessage, systemMessage),
                VertexAiGeminiChatOptions.builder()
                        .withTemperature(0.4f)
                        .build()));

        return response.getResult().getOutput().getContent();
    }
     @SpringBootConfiguration
    public static class TestConfiguration {

        @Bean
        public VertexAI vertexAiApi() {
            String projectId = System.getenv("VERTEX_AI_GEMINI_PROJECT_ID");
            String location = System.getenv("VERTEX_AI_GEMINI_LOCATION");
            return new VertexAI.Builder().setProjectId(projectId)
                .setLocation(location)
                .setTransport(Transport.REST)
                .build();
        }

        @Bean
        public VertexAiGeminiChatClient vertexAiEmbedding(VertexAI vertexAi) {
            String model = System.getenv("MODEL");
            return new VertexAiGeminiChatClient(vertexAi,
                VertexAiGeminiChatOptions.builder()
                    .withModel(model)
                    .build());
        }
    }
}
