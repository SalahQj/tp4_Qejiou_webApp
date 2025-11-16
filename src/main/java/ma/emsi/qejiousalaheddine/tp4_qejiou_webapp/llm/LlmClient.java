package ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.llm;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.data.message.SystemMessage;
import jakarta.enterprise.context.Dependent;

// 1. AJOUT DE L'IMPORT NÉCESSAIRE
import java.io.Serializable;

@Dependent // Annotation CDI pour que le Backing Bean puisse l'injecter
// 2. AJOUT DE "implements Serializable"
public class LlmClient implements Serializable {

    private Assistant assistant;
    private ChatMemory chatMemory;

    public LlmClient() {
        // 1. Récupérer la clé API
        String geminiApiKey = System.getenv("GEMINI_KEY");
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new RuntimeException("Erreur : GEMINI_KEY n'est pas définie.");
        }

        // 2. Créer le modèle de chat (en utilisant votre modèle qui fonctionne)
        ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName("gemini-2.5-flash") // Le modèle de vos tests
                .logRequests(true)
                .logResponses(true)
                .build();

        // 3. Créer la mémoire (comme dans les instructions du TP)
        this.chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        // 4. Créer l'assistant
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                .build();
    }

    // Méthode pour définir le rôle système
    public void setSystemRole(String systemRole) {
        // Vider la mémoire pour commencer une nouvelle conversation
        this.chatMemory.clear();
        // Ajouter le nouveau rôle système
        this.chatMemory.add(SystemMessage.from(systemRole));
    }

    // Méthode pour envoyer la question au LLM
    public String chat(String userQuestion) {
        return this.assistant.chat(userQuestion);
    }
}