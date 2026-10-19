package com.eb.cdcrag.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Turns an uploaded file's bytes into plain text.
 *   .pdf  -> PDFBox   (reads the text layer of the PDF)
 *   .docx -> Apache POI (reads the Word XML)
 *   .txt  -> UTF-8 as-is
 * Note: a scanned/image-only PDF has no text layer, so it extracts nothing —
 * that would need OCR, which is out of scope. Use text-based PDFs for the demo.
 */
@Component
public class DocumentTextExtractor {

    public String extract(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase();
        try {
            if (name.endsWith(".pdf"))  return extractPdf(bytes);
            if (name.endsWith(".docx")) return extractDocx(bytes);
            if (name.endsWith(".txt"))  return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + filename, e);
        }
        throw new IllegalArgumentException("Unsupported type (use .pdf, .docx, .txt): " + filename);
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }
}
