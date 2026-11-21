package com.ricozknow.ingestion;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
// import java.io.IOException;

@Component
public class DocumentTextExtractor {
    private final Tika tika = new Tika();

    public String extract(byte[] bytes, String filename) throws TikaException {
        try (var in = new ByteArrayInputStream(bytes)) {
            String text = tika.parseToString(in);
            if (text == null || text.isBlank()) throw new IllegalArgumentException("No readable text was found in " + filename);
            return text.replace("\u0000", " ").trim();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to extract text from " + filename, ex);
        }
    }
}
