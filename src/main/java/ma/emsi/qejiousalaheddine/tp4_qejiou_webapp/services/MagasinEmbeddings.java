package ma.emsi.qejiousalaheddine.tp4_qejiou_webapp.services;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
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

        // Utilisation d'un embedding model simple pour le développement
        this.embeddingModel = new SimpleEmbeddingModel();
        this.documentParser = new ApachePdfBoxDocumentParser();
        this.documentSplitter = DocumentSplitters.recursive(300, 0);
    }

    /**
     * Ajoute un document PDF au magasin d'embeddings
     */
    public void ajouterDocument(InputStream pdfStream, String fileName) {
        try {
            Document document = documentParser.parse(pdfStream);
            document.metadata().put("file_name", fileName);

            List<TextSegment> segments = documentSplitter.split(document);

            for (TextSegment segment : segments) {
                Embedding embedding = embeddingModel.embed(segment.text()).content();
                embeddingStore.add(embedding, segment);
            }

            System.out.println("✅ Document '" + fileName + "' ajouté avec " + segments.size() + " segments");

        } catch (Exception e) {
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
        System.out.println("✅ Magasin d'embeddings initialisé (mode développement)");
    }

    /**
     * EmbeddingModel simple pour le développement
     * Implémente toutes les méthodes requises
     */
    private static class SimpleEmbeddingModel implements EmbeddingModel {

        @Override
        public dev.langchain4j.model.output.Response<Embedding> embed(String text) {
            // Crée un embedding simple pour le développement
            float[] vector = new float[384]; // Taille standard
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) Math.random(); // Valeurs aléatoires pour le développement
            }
            return dev.langchain4j.model.output.Response.from(Embedding.from(vector));
        }

        @Override
        public dev.langchain4j.model.output.Response<Embedding> embed(TextSegment textSegment) {
            return embed(textSegment.text());
        }

        @Override
        public dev.langchain4j.model.output.Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            // Implémentation de embedAll requise par l'interface
            List<Embedding> embeddings = textSegments.stream()
                    .map(segment -> embed(segment.text()).content())
                    .toList();
            return dev.langchain4j.model.output.Response.from(embeddings);
        }
    }
}