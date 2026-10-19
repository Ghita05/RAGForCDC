package com.eb.cdcrag.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/** Un modèle de mise en forme est un .docx fourni par le client : ses styles
 * (polices, couleurs, tailles de titres, marges) sont extraits une seule
 * fois à l'upload (TemplateStyleExtractor) et stockés en JSON
 * (format_template.style_summary) -- pas ré-analysés à chaque export. */
@Service
public class FormatTemplateService {

    private final FormatTemplateRepository repository;
    private final ObjectMapper mapper;

    public FormatTemplateService(FormatTemplateRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public record FormatTemplateView(FormatTemplateRepository.FormatTemplateSummary summary, FormatStyle style) {}

    public List<FormatTemplateView> list() {
        return repository.findAll().stream().map(this::toView).toList();
    }

    public FormatTemplateView get(long id) {
        return toView(repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Modèle de mise en forme " + id + " introuvable")));
    }

    public FormatTemplateView upload(String name, String filename, byte[] docxData) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du modèle est obligatoire");
        }
        if (docxData == null || docxData.length == 0) {
            throw new IllegalArgumentException("Le fichier est vide");
        }
        FormatStyle style = TemplateStyleExtractor.extract(docxData);
        String styleJson = writeJson(style);
        long id = repository.insert(name.strip(), filename, docxData, styleJson);
        return get(id);
    }

    private FormatTemplateView toView(FormatTemplateRepository.FormatTemplateSummary summary) {
        return new FormatTemplateView(summary, readJson(summary.styleSummaryJson()));
    }

    @Transactional
    public void setDefault(long id) {
        repository.findById(id).orElseThrow(() -> new NoSuchElementException("Modèle de mise en forme " + id + " introuvable"));
        repository.clearDefault();
        repository.markDefault(id);
    }

    public void delete(long id) {
        int deleted = repository.delete(id);
        if (deleted == 0) throw new NoSuchElementException("Modèle de mise en forme " + id + " introuvable");
    }

    /** FormatStyle déjà extrait, prêt pour DocxExporter/PdfExporter -- pas de
     * re-parsing du .docx original à chaque export. */
    public FormatStyle styleFor(long id) {
        FormatTemplateRepository.FormatStyleRow row = repository.findStyle(id)
                .orElseThrow(() -> new NoSuchElementException("Modèle de mise en forme " + id + " introuvable"));
        return readJson(row.styleJson());
    }

    public java.util.Optional<FormatStyle> defaultStyle() {
        return repository.findDefaultStyle().map(row -> readJson(row.styleJson()));
    }

    private String writeJson(FormatStyle style) {
        try {
            return mapper.writeValueAsString(style);
        } catch (Exception e) {
            throw new IllegalStateException("Échec de sérialisation du style extrait", e);
        }
    }

    private FormatStyle readJson(String json) {
        try {
            return mapper.readValue(json, FormatStyle.class);
        } catch (Exception e) {
            throw new IllegalStateException("Style de mise en forme illisible en base", e);
        }
    }
}
