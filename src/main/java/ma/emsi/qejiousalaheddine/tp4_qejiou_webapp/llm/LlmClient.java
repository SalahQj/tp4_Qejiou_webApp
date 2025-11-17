package ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.Dependent;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Dependent
public class LlmClient implements Serializable {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String systemRole = "You are a helpful assistant.";

    public LlmClient() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    // Méthode pour définir le rôle système
    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
    }

    // Méthode pour envoyer la question au LLM (approche HTTP directe)
    public String chat(String userQuestion) {
        try {
            String apiKey = System.getenv("GEMINI_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                return "❌ Erreur : GEMINI_API_KEY n'est pas définie. Utilisez OpenAI à la place.";
            }

            // Construction du payload pour Gemini
            Map<String, Object> payload = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", systemRole + "\n\nUser: " + userQuestion + "\n\nAssistant:")
                            ))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.7,
                            "topK", 20,
                            "topP", 0.9,
                            "maxOutputTokens", 1000
                    )
            );

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Extraction de la réponse
                Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    return (String) parts.get(0).get("text");
                }
            }

            return "❌ Erreur API Gemini: " + response.body();

        } catch (Exception e) {
            return "❌ Erreur lors de l'appel à Gemini: " + e.getMessage();
        }
    }
}