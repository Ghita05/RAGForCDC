package com.eb.cdcrag.export;

import com.eb.cdcrag.generation.CdcRepository;
import com.eb.cdcrag.generation.CdcSectionRepository;
import com.eb.cdcrag.settings.OrganisationSettings;
import com.eb.cdcrag.settings.OrganisationSettingsRepository;
import com.eb.cdcrag.settings.OrganisationSettingsService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/** Construit l'export d'un CDC dans l'un des trois formats et délègue le
 * rendu binaire à DocxExporter/PdfExporter (md reste le texte assemblé brut,
 * comme avant). Le modèle intermédiaire (ExportDocument) n'est construit
 * qu'une fois par appel, pas dupliqué entre les deux renderers riches. */
@Service
public class ExportService {

    private static final Set<String> FORMATS = Set.of("pdf", "docx", "md");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final CdcRepository cdcRepository;
    private final CdcSectionRepository sectionRepository;
    private final FormatTemplateService formatTemplateService;
    private final OrganisationSettingsService organisationSettingsService;

    public ExportService(CdcRepository cdcRepository, CdcSectionRepository sectionRepository,
                         FormatTemplateService formatTemplateService,
                         OrganisationSettingsService organisationSettingsService) {
        this.cdcRepository = cdcRepository;
        this.sectionRepository = sectionRepository;
        this.formatTemplateService = formatTemplateService;
        this.organisationSettingsService = organisationSettingsService;
    }

    public record ExportResult(byte[] content, String contentType, String filename) {}

    public ExportResult export(long cdcId, String format) {
        if (!FORMATS.contains(format)) {
            throw new IllegalArgumentException("Format non supporté : " + format + " (attendu : pdf, docx, md)");
        }
        CdcRepository.CdcRecord cdc = cdcRepository.findById(cdcId)
                .orElseThrow(() -> new NoSuchElementException("CDC " + cdcId + " introuvable"));

        String baseName = "CDC-" + cdcId + (cdc.cdcType() != null ? "_" + cdc.cdcType() : "");
        return switch (format) {
            case "md" -> new ExportResult(
                    sectionRepository.assembleMarkdown(cdcId).getBytes(StandardCharsets.UTF_8),
                    "text/markdown; charset=UTF-8", baseName + ".md");
            case "docx" -> new ExportResult(
                    DocxExporter.export(buildDocument(cdc), resolveStyle(cdc)),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    baseName + ".docx");
            case "pdf" -> new ExportResult(
                    PdfExporter.export(buildDocument(cdc), resolveStyle(cdc)),
                    "application/pdf", baseName + ".pdf");
            default -> throw new IllegalStateException("unreachable, voir FORMATS");
        };
    }

    /** Ordre de résolution : le modèle associé à CE CDC, sinon le modèle de
     * mise en forme marqué par défaut, sinon l'habillage intégré
     * (FormatStyle.defaultStyle()) -- jamais d'échec d'export faute de
     * modèle configuré. */
    private FormatStyle resolveStyle(CdcRepository.CdcRecord cdc) {
        if (cdc.formatTemplateId() != null) {
            return formatTemplateService.styleFor(cdc.formatTemplateId());
        }
        return formatTemplateService.defaultStyle().orElseGet(FormatStyle::defaultStyle);
    }

    private ExportDocument buildDocument(CdcRepository.CdcRecord cdc) {
        List<CdcSectionRepository.Section> sections = sectionRepository.findAllByCdc(cdc.id());
        // Même filtre que assembleMarkdown : une section EMPTY/QUEUED/GENERATING/FAILED
        // n'a pas de contenu à exporter.
        List<ExportDocument.ExportSection> exportSections = sections.stream()
                .filter(s -> s.body() != null)
                .map(s -> new ExportDocument.ExportSection(s.ordre(), s.titre(), MarkdownBlocks.parse(s.body())))
                .toList();
        String statut = "APPROVED".equals(cdc.status()) ? "Validé" : "Brouillon";
        String reference = ReferenceTemplate.render(
                organisationSettingsService.get().formatReference(), Year.now().getValue(), cdc.id(), cdc.cdcType());
        // Date d'édition du document, pas de date de création en base sur
        // CdcRecord (voir CdcRepository) -- suffisant pour une page de garde.
        return new ExportDocument(cdc.title(), cdc.cdcType(), reference,
                DATE_FMT.format(LocalDate.now()), statut, exportSections, resolveBranding());
    }

    /** Contenu de marque (nom, direction, mention, pied de page, contact, logo)
     * -- séparé de FormatStyle (voir resolveStyle) : ce sont deux notions
     * indépendantes (contenu vs apparence), voir CLAUDE.md. */
    private ExportDocument.Branding resolveBranding() {
        OrganisationSettings settings = organisationSettingsService.get();
        byte[] logo = null;
        String logoContentType = null;
        if (settings.hasLogo()) {
            OrganisationSettingsRepository.LogoData logoData = organisationSettingsService.getLogo();
            if (logoData != null) {
                logo = logoData.bytes();
                logoContentType = logoData.contentType();
            }
        }
        return new ExportDocument.Branding(settings.nomOrganisation(), settings.direction(), logo, logoContentType,
                settings.mentionConfidentialite(), settings.piedDePage(), settings.contactService(),
                settings.contactAdresse(), settings.contactCourriel());
    }
}
