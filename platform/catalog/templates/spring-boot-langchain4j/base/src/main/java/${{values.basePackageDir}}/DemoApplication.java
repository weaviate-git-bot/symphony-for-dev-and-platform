package ${{ values.basePackage }};

import java.io.IOException;
import java.nio.file.Path;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	ChatAgent chatAgent(ChatLanguageModel chatLanguageModel) {
		ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
		return AiServices.builder(ChatAgent.class)
				.chatLanguageModel(chatLanguageModel)
				.chatMemory(chatMemory)
				.build();
	}

	@Bean
	DocumentAgent documentAgent(ChatLanguageModel chatLanguageModel, EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) throws IOException {
		Path documentPath = ResourceUtils.getFile("classpath:documents/story.md").toPath();
		DocumentParser documentParser = new TextDocumentParser();
		Document document = FileSystemDocumentLoader.loadDocument(documentPath, documentParser);

		EmbeddingStoreIngestor dataIngestor = EmbeddingStoreIngestor.builder()
				.embeddingStore(embeddingStore)
				.embeddingModel(embeddingModel)
				.documentSplitter(DocumentSplitters.recursive(300, 10))
				.build();
		dataIngestor.ingest(document);

		ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
				.embeddingStore(embeddingStore)
				.embeddingModel(embeddingModel)
				.maxResults(3)
				.minScore(0.5)
				.build();
		
		ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
		return AiServices.builder(DocumentAgent.class)
				.chatLanguageModel(chatLanguageModel)
				.contentRetriever(contentRetriever)
				.chatMemory(chatMemory)
				.build();
	}

}

@RestController
class ChatController {

	private final ChatAgent chatAgent;

	ChatController(ChatAgent chatAgent) {
		this.chatAgent = chatAgent;
	}
	
	@PostMapping("/chat")
	String chat(@RequestBody String prompt) {
    	return chatAgent.answer(prompt);
	}

}

@RestController
class DocumentController {

	private final DocumentAgent documentAgent;

	DocumentController(DocumentAgent documentAgent) {
		this.documentAgent = documentAgent;
	}
	
	@PostMapping("/chat/doc")
	String chat(@RequestBody String prompt) {
    	return documentAgent.answer(prompt);
	}

}

interface ChatAgent {
	String answer(String prompt);
}

interface DocumentAgent {
	String answer(String prompt);
}
