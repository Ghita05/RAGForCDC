package com.eb.cdcrag.ingestion;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class Chunker {
    private static final int SIZE = 800;      // characters per chunk
    private static final int OVERLAP = 150;    // shared characters between neighbours

    public List<String> chunk(String text) {
        String t = text.strip();
        List<String> chunks = new ArrayList<>();
        if (t.isEmpty()) return chunks;

        int step = Math.max(1, SIZE - OVERLAP);
        for (int start = 0; start < t.length(); start += step) {
            int end = Math.min(start + SIZE, t.length());
            String piece = t.substring(start, end).strip();
            if (!piece.isEmpty()) chunks.add(piece);
            if (end == t.length()) break;
        }
        return chunks;
    }
}
