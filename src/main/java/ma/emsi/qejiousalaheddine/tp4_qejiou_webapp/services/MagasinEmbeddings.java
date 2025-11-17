package ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.services;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.util.List;

@ApplicationScoped
public class MagasinEmbeddings {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DocumentParser documentParser;
    private final DocumentSplitter documentSplitter;

    public MagasinEmbeddings() {
        this.embeddingStore = new InMemoryEmbeddingStore<>();

        // ✅ CORRECTION : Utiliser un vrai modèle d'embedding
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        this.documentParser = new ApachePdfBoxDocumentParser();

        // ✅ CORRECTION : Ajouter un overlap de 30 tokens
        this.documentSplitter = DocumentSplitters.recursive(300, 30);
    }

    /**
     * Ajoute un document PDF au magasin d'embeddings
     */
    public void ajouterDocument(InputStream pdfStream, String fileName) {
        try {
            // Parser le document PDF
            Document document = documentParser.parse(pdfStream);
            document.metadata().put("file_name", fileName);

            // Découper en segments (chunks)
            List<TextSegment> segments = documentSplitter.split(document);

            // Créer les embeddings pour chaque segment
            for (TextSegment segment : segments) {
                Embedding embedding = embeddingModel.embed(segment.text()).content();
                embeddingStore.add(embedding, segment);
            }

            System.out.println("✅ Document '" + fileName + "' ajouté avec " + segments.size() + " segments");

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'ajout du document: " + fileName);
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de l'ajout du document: " + fileName, e);
        }
    }

    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    @PostConstruct
    public void init() {
        System.out.println("✅ Magasin d'embeddings initialisé avec AllMiniLmL6V2EmbeddingModel");
    }

    // ❌ SUPPRIMÉ : La classe SimpleEmbeddingModel n'est plus nécessaire
}