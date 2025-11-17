package ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;

// Import pour le RAG
import ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.services.MagasinEmbeddings;
import ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.services.AssistantAvecRAG;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Modifié pour le TP4 avec fonctionnalités RAG.
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
    private Part fichier;
    private String messagePourChargementFichier;
    private final List<String> fichiersCharges = new ArrayList<>();

    @Inject
    private FacesContext facesContext;

    @Inject
    private ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.llm.LlmClient llmClient;

    // ==== INJECTION POUR LE RAG ====
    @Inject
    private MagasinEmbeddings magasinEmbeddings;

    private AssistantAvecRAG assistant;

    public Bb() {
    }

    // --- Getters et Setters ---
    public String getRoleSysteme() {
        return roleSysteme;
    }

    public void setRoleSysteme(String roleSysteme) {
        this.roleSysteme = roleSysteme;
    }

    public boolean isRoleSystemeChangeable() {
        return roleSystemeChangeable;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReponse() {
        return reponse;
    }

    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    public String getConversation() {
        return conversation.toString();
    }

    public void setConversation(String conversation) {
        this.conversation = new StringBuilder(conversation);
    }

    public Part getFichier() {
        return fichier;
    }

    public void setFichier(Part fichier) {
        this.fichier = fichier;
    }

    public String getMessagePourChargementFichier() {
        return messagePourChargementFichier;
    }

    public void setMessagePourChargementFichier(String messagePourChargementFichier) {
        this.messagePourChargementFichier = messagePourChargementFichier;
    }

    public List<String> getFichiersCharges() {
        return fichiersCharges;
    }

    /**
     * Initialise l'assistant avec RAG
     */
    private void initAssistantAvecRAG() {
        if (assistant == null) {
            try {
                // FONCTIONNALITÉ 1: Configuration avancée avec OpenAI
                ChatLanguageModel model = OpenAiChatModel.builder()
                        .apiKey("sk-votre-cle-api-openai") // À configurer
                        .modelName("gpt-3.5-turbo")
                        .temperature(0.7)
                        .topP(0.9)
                        .maxTokens(1000)
                        .build();

                // FONCTIONNALITÉ 2: Configuration du RAG
                EmbeddingStore<TextSegment> embeddingStore = magasinEmbeddings.getEmbeddingStore();

                var retriever = EmbeddingStoreRetriever.from(
                        embeddingStore,
                        magasinEmbeddings.getEmbeddingModel(),
                        5,
                        0.6
                );

                assistant = AiServices.builder(AssistantAvecRAG.class)
                        .chatLanguageModel(model)
                        .retriever(retriever)
                        .build();

            } catch (Exception e) {
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Erreur d'initialisation RAG",
                        "Impossible d'initialiser le système RAG: " + e.getMessage());
                facesContext.addMessage(null, message);
            }
        }
    }

    /**
     * Vérifie si des documents sont chargés dans le RAG
     */
    private boolean hasDocumentsCharges() {
        // Vérification simple basée sur la liste des fichiers
        return !fichiersCharges.isEmpty();
    }

    /**
     * Envoie la question au serveur avec RAG.
     */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Texte question vide", "Il manque le texte de la question");
            facesContext.addMessage(null, message);
            return null;
        }

        try {
            initAssistantAvecRAG();

            if (this.isRoleSystemeChangeable()) {
                this.roleSysteme = """
                    Tu es un assistant utile qui répond aux questions en utilisant exclusivement
                    les informations fournies dans les documents PDF chargés.
                    Si tu ne trouves pas la réponse dans les documents, dis simplement que tu ne sais pas.
                    Réponds toujours en français.
                    """;
                this.roleSystemeChangeable = false;
            }

            // FONCTIONNALITÉ 2: Utilisation du RAG pour répondre
            if (assistant != null && hasDocumentsCharges()) {
                this.reponse = assistant.repondreAvecContexte(question);
                this.reponse += "\n\n[✅ Réponse générée avec contexte RAG des PDF chargés]";
            } else {
                this.reponse = llmClient.chat(question);
                this.reponse += "\n\n[ℹ️ Réponse sans contexte RAG - Aucun PDF chargé]";
            }

        } catch (Exception e) {
            FacesMessage message =
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Erreur avec le système RAG",
                            "Erreur: " + e.getMessage());
            facesContext.addMessage(null, message);
            return null;
        }

        afficherConversation();
        this.question = "";
        return null;
    }

    /**
     * FONCTIONNALITÉ 3: Upload de fichiers PDF pour le RAG
     */
    public void upload() {
        if (fichier != null && fichier.getSubmittedFileName() != null) {
            String fileName = fichier.getSubmittedFileName().toLowerCase();

            if (fileName.endsWith(".pdf")) {
                try {
                    magasinEmbeddings.ajouterDocument(fichier.getInputStream(), fichier.getSubmittedFileName());
                    fichiersCharges.add(fichier.getSubmittedFileName());
                    assistant = null;

                    messagePourChargementFichier = "✅ Fichier '" + fichier.getSubmittedFileName() + "' chargé avec succès et ajouté au contexte RAG !";

                    FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO,
                            "Fichier chargé",
                            messagePourChargementFichier);
                    facesContext.addMessage(null, message);

                } catch (Exception e) {
                    messagePourChargementFichier = "❌ Erreur lors du chargement: " + e.getMessage();
                    FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Erreur de chargement",
                            messagePourChargementFichier);
                    facesContext.addMessage(null, message);
                }
            } else {
                messagePourChargementFichier = "❌ Veuillez sélectionner un fichier PDF valide";
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Format incorrect",
                        messagePourChargementFichier);
                facesContext.addMessage(null, message);
            }
        } else {
            messagePourChargementFichier = "❌ Aucun fichier sélectionné";
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Fichier manquant",
                    messagePourChargementFichier);
            facesContext.addMessage(null, message);
        }
    }

    /**
     * Pour effacer l'historique de conversation
     */
    public void effacerHistorique() {
        this.conversation = new StringBuilder();
        FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO,
                "Historique effacé",
                "L'historique de conversation a été effacé");
        facesContext.addMessage(null, message);
    }

    /**
     * Pour un nouveau chat.
     */
    public String nouveauChat() {
        this.conversation = new StringBuilder();
        this.question = "";
        this.reponse = "";
        this.roleSystemeChangeable = true;
        return "index";
    }

    /**
     * Pour afficher la conversation.
     */
    private void afficherConversation() {
        String echange = "== User:\n" + question + "\n\n== Assistant RAG:\n" + reponse + "\n\n-----------------------------------\n";
        this.conversation.insert(0, echange);
    }

    /**
     * Pour la liste des rôles.
     */
    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();

            this.listeRolesSysteme.add(new SelectItem("""
                    You are a helpful assistant. You help the user to find the information they need.
                    If the user type a question, you answer it.
                    """, "Assistant"));

            this.listeRolesSysteme.add(new SelectItem("""
                    You are an interpreter. You translate from English to French and from French to English.
                    """, "Traducteur Anglais-Français"));

            this.listeRolesSysteme.add(new SelectItem("""
                    Your are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit.
                    """, "Guide touristique"));

            this.listeRolesSysteme.add(new SelectItem("""
                    Vous êtes un expert qui répond aux questions en utilisant exclusivement
                    les informations des documents PDF chargés. Si l'information n'est pas
                    dans les documents, dites que vous ne savez pas.
                    """, "Expert RAG (avec documents PDF)"));

            this.listeRolesSysteme.add(new SelectItem("""
                    Vous êtes un Oracle sarcastique. Vous voyez le futur mais vous êtes très agacé d'être dérangé.
                    Répondez de manière mystérieuse, et avec une pointe de sarcasme.
                    """, "Oracle Sarcastique"));
        }
        return this.listeRolesSysteme;
    }
}