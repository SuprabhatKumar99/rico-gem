package com.ricozknow.enterprise.audit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuditExportControllerTest {
    @Test void csvEscapesFormulaeAndQuotes() {
        assertThat(AuditExportController.csv("=HYPERLINK(\"https://example.com\")"))
                .startsWith("\"'=").contains("\"\"https://example.com\"\"");
    }
}
