package ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

// ==== IMPORT CORRIGÉ (on importe le NOUVEAU client) ====
import ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.llm.LlmClient;
// =======================================================

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Corrigé pour le TP2 (LangChain4j).
 */

@Named
@ViewScoped
public class Bb implements Serializable {

    // --- Les champs pour le rôle, la question, la réponse, etc. restent identiques ---
    private String roleSysteme;
    private boolean roleSystemeChangeable = true;
    private List<SelectItem> listeRolesSysteme;
    private String question;
    private String reponse;
    private StringBuilder conversation = new StringBuilder();

    @Inject
    private FacesContext facesContext;

    // ==== INJECTION CORRIGÉE (on injecte le NOUVEAU client) ====
    @Inject
    private LlmClient llmClient; // Au lieu de JsonUtilPourGemini
    // ===========================================================

    // ==== TOUS LES CHAMPS DE DEBUG SONT SUPPRIMÉS ====
    // private boolean debug = false;
    // private String texteRequeteJson;
    // private String texteReponseJson;
    // ===============================================

    public Bb() {
    }

    // --- Getters et Setters (inchangés, SAUF les getters/setters de debug) ---
    // ... (getRoleSysteme, setRoleSysteme, isRoleSystemeChangeable, etc.) ...

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


    /**
     * Envoie la question au serveur.
     * CE CODE EST MAINTENANT MODIFIÉ POUR LE TP2 (LangChain4j).
     *
     * @return null pour rester sur la même page.
     */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Texte question vide", "Il manque le texte de la question");
            facesContext.addMessage(null, message);
            return null;
        }

        // --- DÉBUT DE LA NOUVELLE LOGIQUE TP2 (LangChain4j) ---
        try {
            // Si c'est le premier message, on envoie le rôle système et on verrouille la liste
            if (this.isRoleSystemeChangeable()) {
                llmClient.setSystemRole(this.roleSysteme); // <-- MODIFIÉ
                this.roleSystemeChangeable = false; // Verrouille la liste déroulante
            }

            // Appelle le nouveau service LangChain4j
            this.reponse = llmClient.chat(this.question); // <-- MODIFIÉ

        } catch (Exception e) {
            // En cas d'erreur (ex: clé API incorrecte, pas de réseau)
            FacesMessage message =
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Problème de connexion avec le LLM",
                            "Problème de connexion avec l'API du LLM: " + e.getMessage());
            facesContext.addMessage(null, message);
            return null; // Arrête le traitement en cas d'erreur
        }
        // --- FIN DE LA NOUVELLE LOGIQUE TP2 ---


        // La conversation contient l'historique des questions-réponses depuis le début.
        afficherConversation();

        // C'est une bonne pratique de vider le champ question après l'envoi
        this.question = "";

        return null;
    }

    /**
     * Pour un nouveau chat. (Cette méthode est correcte et reste inchangée)
     * @return "index"
     */
    public String nouveauChat() {
        return "index";
    }

    /**
     * Pour afficher la conversation. (Cette méthode est correcte et reste inchangée)
     */
    private void afficherConversation() {
        // Note : j'ai inversé l'ordre pour que le dernier message soit en haut
        String echange = "== User:\n" + question + "\n\n== Serveur:\n" + reponse + "\n\n-----------------------------------\n";
        this.conversation.insert(0, echange); // Insère au début
    }

    /**
     * Pour la liste des rôles. (Cette méthode est correcte et reste inchangée)
     */
    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();
            String role = """
                    You are a helpful assistant. You help the user to find the information they need.
                    If the user type a question, you answer it.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Assistant"));

            role = """
                    You are an interpreter. You translate from English to French and from French to English.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Traducteur Anglais-Français"));

            role = """
                    Your are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Guide touristique"));

            // Votre rôle bonus est parfait
            role = """
                    Vous êtes un Oracle sarcastique. Vous voyez le futur mais vous êtes très agacé d'être dérangé. 
                    Répondez de manière mystérieuse, et avec une pointe de sarcasme. 
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Oracle Sarcastique"));
        }
        return this.listeRolesSysteme;
    }

    // ==== TOUTES LES MÉTHODES DE DEBUG SONT SUPPRIMÉES ====
    // toggleDebug()
    // isDebug()
    // setDebug()
    // getTexteRequeteJson()
    // setTexteRequeteJson()
    // getTexteReponseJson()
    // setTexteReponseJson()
    // =====================================================
}