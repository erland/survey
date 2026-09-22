package info.isaksson.erland.survey.exporting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunResultCsvExportServiceTest {
    @Test
    void quoteEscapesCommaQuoteAndNewline() {
        assertEquals("\"Hej, \"\"värld\"\"\nrad 2\"", RunResultCsvExportService.quote("Hej, \"värld\"\nrad 2"));
    }
}
