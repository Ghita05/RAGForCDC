package com.eb.cdcrag.ingestion;

import com.eb.cdcrag.generation.CdcTemplateRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Découpe un CDC en segments correspondant aux sections du template standard,
 * en repérant les titres numérotés ("6. Spécifications techniques"). Le
 * corpus synthétique suit exactement ces titres ; les CDC réels (uploads/)
 * n'ont aucune raison de le faire. Un segment dont le titre ne correspond à
 * aucune section connue du template par défaut reste non tagué (sectionCode
 * null) plutôt que d'être rattaché arbitrairement à la mauvaise section.
 */
@Component
public class SectionDetector {

    private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}\\d{1,2}\\.\\s+(.{3,80})$");

    private final CdcTemplateRepository templates;

    public SectionDetector(CdcTemplateRepository templates) {
        this.templates = templates;
    }

    public List<Segment> split(String text) {
        Map<String, String> titleToCode = titleIndex();
        List<Segment> segments = new ArrayList<>();
        Matcher m = HEADING.matcher(text);

        int prevEnd = 0;
        String currentCode = null;
        while (m.find()) {
            if (m.start() > prevEnd) {
                segments.add(new Segment(currentCode, text.substring(prevEnd, m.start())));
            }
            currentCode = titleToCode.get(normalize(m.group(1)));
            prevEnd = m.start();
        }
        if (prevEnd < text.length()) {
            segments.add(new Segment(currentCode, text.substring(prevEnd)));
        }
        return segments;
    }

    private Map<String, String> titleIndex() {
        Map<String, String> idx = new HashMap<>();
        for (var s : templates.findDefaultSections()) {
            idx.put(normalize(s.titre()), s.code());
        }
        return idx;
    }

    private String normalize(String s) {
        return s.strip().toLowerCase();
    }

    public record Segment(String sectionCode, String text) {}
}
