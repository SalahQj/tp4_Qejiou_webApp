package ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.primefaces.model.file.UploadedFile;

import ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.services.MagasinEmbeddings;
import ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.services.AssistantAvecRAG;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Modifié pour le TP4 avec fonctionnalités RAG, PrimeFaces et configuration sécurisée de l'API.
 */
@Named
@ViewScoped
public class Bb implements Serializable {

    // --- Champs existants ---
    private String roleSysteme;
    private boolean roleSystemeChangeable = true;
    private List<SelectItem> listeRolesSysteme;
    private String question;
    private String reponse;
    private StringBuilder conversation = new StringBuilder();

    // --- Nouveaux champs pour le RAG ---
    private UploadedFile fichier;
    private String messagePourChargementFichier;
    private final List<String> fichiersCharges = new ArrayList<>();

    @Inject
    private FacesContext facesContext;

    // ==== INJECTION POUR LE RAG ====
    @Inject
    private MagasinEmbeddings magasinEmbeddings;

    private AssistantAvecRAG assistant;
    private ChatLanguageModel model;

    public Bb() {
    }

    // --- Getters et Setters (Omis pour la concision - ils sont inchangés) ---
    public String getRoleSysteme() { return roleSysteme; }
    public void setRoleSysteme(String roleSysteme) { this.roleSysteme = roleSysteme; }
    public boolean isRoleSystemeChangeable() { return roleSystemeChangeable; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getReponse() { return reponse; }
    public void setReponse(String reponse) { this.reponse = reponse; }
    public String getConversation() { return conversation.toString(); }
    public void setConversation(String conversation) { this.conversation = new StringBuilder(conversation); }
    public UploadedFile getFichier() { return fichier; }
    public void setFichier(UploadedFile fichier) { this.fichier = fichier; }
    public String getMessagePourChargementFichier() { return messagePourChargementFichier; }
    public void setMessagePourChargementFichier(String messagePourChargementFichier) { this.messagePourChargementFichier = messagePourChargementFichier; }
    public List<String> getFichiersCharges() { return fichiersCharges; }


    /**
     * Initialise le modèle de chat Gemini.
     * La clé API est lue d'abord depuis la variable d'environnement GEMINI_KEY,
     * puis en fallback depuis config.properties.
     */
    private void initChatModel() {
        if (model == null) {
            String apiKey = null;

            // 1. Tenter de lire la clé depuis la variable d'environnement (méthode préférée)
            apiKey = System.getenv("GEMINI_KEY");

            // 2. Si la variable d'environnement n'est pas trouvée, lire depuis le fichier config.properties
            if (apiKey == null || apiKey.isEmpty()) {
                try {
                    Properties props = new Properties();
                    try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                        if (is == null) {
                            throw new RuntimeException("Le fichier config.properties est introuvable. Veuillez le placer dans src/main/resources.");
                        }
                        props.load(is);
                    }
                    apiKey = props.getProperty("gemini.api.key");

                    if ("VOTRE_CLÉ_API_ICI".equals(apiKey)) {
                        apiKey = null; // Traiter cela comme une clé non configurée
                    }
                } catch (Exception e) {
                    FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Erreur de lecture de configuration",
                            "Impossible de lire config.properties : " + e.getMessage());
                    facesContext.addMessage(null, message);
                    throw new RuntimeException("Erreur de lecture de la configuration.", e);
                }
            }


            if (apiKey == null || apiKey.isEmpty()) {
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Clé API Manquante",
                        "La clé GEMINI_KEY n'est pas définie comme variable d'environnement ou dans config.properties.");
                facesContext.addMessage(null, message);
                throw new RuntimeException("Clé API Gemini non configurée.");
            }

            // Si la clé est trouvée, initialiser le modèle
            try {
                model = GoogleAiGeminiChatModel.builder()
                        .apiKey(apiKey)
                        // CORRECTION CLÉ : Le modèle correct est gemini-2.5-flash
                        .modelName("gemini-2.5-flash")
                        .temperature(0.7)
                        .topP(0.9)
                        .maxOutputTokens(1000)
                        .logRequestsAndResponses(true)
                        .build();

            } catch (Exception e) {
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Erreur d'initialisation du modèle",
                        "Impossible d'initialiser Gemini: " + e.getMessage());
                facesContext.addMessage(null, message);
                throw new RuntimeException("Erreur fatale lors de l'initialisation de Gemini.", e);
            }
        }
    }


    /**
     * Initialise l'assistant avec RAG
     */
    private void initAssistantAvecRAG() {
        if (hasDocumentsCharges()) {
            try {
                initChatModel();

                EmbeddingStore<TextSegment> embeddingStore = magasinEmbeddings.getEmbeddingStore();

                ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(magasinEmbeddings.getEmbeddingModel())
                        .maxResults(5) // Top 5 résultats
                        .minScore(0.6) // Score minimum de 0.6
                        .build();

                assistant = AiServices.builder(AssistantAvecRAG.class)
                        .chatLanguageModel(model)
                        .contentRetriever(retriever)
                        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                        .build();

                System.out.println("✅ Assistant RAG initialisé avec succès");

            } catch (Exception e) {
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Erreur d'initialisation RAG",
                        "Impossible d'initialiser le système RAG: " + e.getMessage());
                facesContext.addMessage(null, message);
                e.printStackTrace();
            }
        }
    }

    private boolean hasDocumentsCharges() {
        return !fichiersCharges.isEmpty();
    }

    /**
     * Envoie la question au serveur avec RAG
     */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Texte question vide", "Il manque le texte de la question");
            facesContext.addMessage(null, message);
            return null;
        }

        try {
            initChatModel();

            if (this.isRoleSystemeChangeable()) {
                if (roleSysteme == null || roleSysteme.isBlank()) {
                    this.roleSysteme = """
                        Tu es un assistant utile qui répond aux questions.
                        Si des documents PDF ont été chargés, utilise-les pour répondre.
                        Si tu ne trouves pas la réponse dans les documents, dis-le clairement.
                        Réponds toujours en français.
                        """;
                }
                this.roleSystemeChangeable = false;
            }

            if (hasDocumentsCharges()) {
                if(assistant == null) {
                    initAssistantAvecRAG();
                }

                if (assistant != null) {
                    this.reponse = assistant.repondreAvecContexte(question);
                    this.reponse += "\n\n[✅ Réponse générée avec contexte RAG - "
                            + fichiersCharges.size() + " PDF chargé(s)]";
                } else {
                    this.reponse = model.generate(roleSysteme + "\n\nUser: " + question);
                    this.reponse += "\n\n[⚠️ Problème RAG, réponse standard]";
                }
            } else {
                this.reponse = model.generate(roleSysteme + "\n\nUser: " + question);
                this.reponse += "\n\n[ℹ️ Réponse sans contexte RAG - Aucun PDF chargé]";
            }

        } catch (Exception e) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Erreur lors de l'envoi de la question",
                    "Erreur: " + e.getMessage());
            facesContext.addMessage(null, message);
            e.printStackTrace();
            return null;
        }

        afficherConversation();
        this.question = "";
        return null;
    }

    /**
     * Upload de fichiers PDF pour le RAG
     */
    public void upload() {
        if (fichier != null && fichier.getFileName() != null && !fichier.getFileName().isBlank()) {
            String fileName = fichier.getFileName();

            if (fileName.toLowerCase().endsWith(".pdf")) {
                try {
                    magasinEmbeddings.ajouterDocument(
                            fichier.getInputStream(),
                            fileName
                    );

                    fichiersCharges.add(fileName);
                    assistant = null;

                    messagePourChargementFichier = "✅ Fichier '" + fileName
                            + "' chargé avec succès et ajouté au contexte RAG !";

                    FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO,
                            "Fichier chargé",
                            messagePourChargementFichier);
                    facesContext.addMessage(null, message);

                    System.out.println("✅ Fichier chargé: " + fileName);

                } catch (Exception e) {
                    messagePourChargementFichier = "❌ Erreur lors du chargement: " + e.getMessage();
                    FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Erreur de chargement",
                            messagePourChargementFichier);
                    facesContext.addMessage(null, message);
                    e.printStackTrace();
                }
            } else {
                messagePourChargementFichier = "❌ Veuillez sélectionner un fichier PDF valide";
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Format incorrect",
                        messagePourChargementFichier);
                facesContext.addMessage(null, message);
            }
        } else {
            messagePourChargementFichier = "❌ Aucun fichier sélectionné (ou fichier vide)";
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Fichier manquant",
                    messagePourChargementFichier);
            facesContext.addMessage(null, message);
        }
    }

    public void effacerHistorique() {
        this.conversation = new StringBuilder();
        FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO,
                "Historique effacé",
                "L'historique de conversation a été effacé");
        facesContext.addMessage(null, message);
    }

    public String nouveauChat() {
        this.conversation = new StringBuilder();
        this.question = "";
        this.reponse = "";
        this.roleSystemeChangeable = true;
        this.assistant = null;
        return "index";
    }

    private void afficherConversation() {
        String echange = "== User:\n" + question + "\n\n== Assistant RAG:\n"
                + reponse + "\n\n-----------------------------------\n";
        this.conversation.insert(0, echange);
    }

    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();
            this.listeRolesSysteme.add(new SelectItem("""
                    You are a helpful assistant. You help the user to find the information they need.
                    """, "Assistant"));
            this.listeRolesSysteme.add(new SelectItem("""
                    Vous êtes un expert qui répond aux questions en utilisant exclusivement
                    les informations des documents PDF chargés.
                    """, "Expert RAG (avec documents PDF)"));
        }
        return this.listeRolesSysteme;
    }
}