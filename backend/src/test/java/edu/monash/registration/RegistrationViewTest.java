package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.MutableClock;
import edu.monash.registration.support.TestFixtures;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR5 — registered-course view, totals, ordering and CSV export. */
@DisplayName("FR5 — View registered courses")
class RegistrationViewTest extends BaseIntegrationTest {

    private static final String CSV_HEADER =
            "Course Code,Course Name,Credit Hours,Instructor,Day,Time,Location";

    private Student aisha;
    private Student daniel;
    private Course fit1045;
    private Course fit2004;
    private Course tst0009;

    @BeforeEach
    void seed() {
        aisha = fixtures.aisha();
        daniel = fixtures.daniel();
        // Deliberately hostile location: comma and embedded quotes exercise RFC 4180 escaping.
        fit1045 = fixtures.course("FIT1045", "Introduction to Programming", 6, "Dr Alan Ng",
                120, 120, "2026-S2", "Information Technology",
                List.of(TestFixtures.Slot.of("MON", "10:00", "12:00", "Clayton, \"Hall\" 25"),
                        TestFixtures.Slot.of("WED", "14:00", "16:00",
                                "Clayton, Building 25, Rm 130")));
        fit2004 = fixtures.singleSlotCourse("FIT2004", 6, 80, 80);
        tst0009 = fixtures.singleSlotCourse("TST0009", 6, 10, 10);
    }

    @Test
    @DisplayName("T-FR5-01 two registrations produce totals, maxCredits and full course objects")
    void tFr5_01_populatedView() throws Exception {
        register(fit2004, "2026-11-08T12:00:00+08:00");
        register(fit1045, "2026-11-08T12:00:05+08:00");

        JsonNode list = body(getAuthed("/api/registrations", bearer(aisha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCredits").value(12))
                .andExpect(jsonPath("$.maxCredits").value(24))
                .andExpect(jsonPath("$.semester").value("2026-S2")));

        assertThat(list.path("items").size()).isEqualTo(2);
        for (JsonNode item : list.path("items")) {
            assertThat(item.path("registrationId").asText()).isNotBlank();
            assertThat(item.path("registeredAt").asText()).isNotBlank();
            assertThat(item.path("course").path("code").asText()).isNotBlank();
            assertThat(item.path("course").path("schedule").size()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("T-FR5-02 no registrations is 200 with an empty list, not 404")
    void tFr5_02_empty() throws Exception {
        mockMvc.perform(get("/api/registrations").header("Authorization", bearer(aisha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalCredits").value(0))
                .andExpect(jsonPath("$.maxCredits").value(24));
    }

    @Test
    @DisplayName("T-FR5-03 export has the CSV content type and attachment disposition")
    void tFr5_03_csvHeaders() throws Exception {
        register(fit1045, "2026-11-08T12:00:00+08:00");
        mockMvc.perform(get("/api/registrations/export").header("Authorization", bearer(aisha)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("attachment"),
                                org.hamcrest.Matchers.containsString(
                                        "filename=\"course-list.csv\""))));
    }

    @Test
    @DisplayName("T-FR5-04 header is exact, a two-slot course emits two rows, Time is HH:mm-HH:mm")
    void tFr5_04_rows() throws Exception {
        register(fit1045, "2026-11-08T12:00:00+08:00");
        String csv = export();

        String[] lines = csv.split("\r\n", -1);
        assertThat(lines[0]).isEqualTo(CSV_HEADER);
        long fit1045Rows = 0;
        Pattern time = Pattern.compile("^\\d{2}:\\d{2}-\\d{2}:\\d{2}$");
        for (String line : lines) {
            if (line.startsWith("FIT1045,")) {
                fit1045Rows++;
                List<String> fields = parseCsvLine(line);
                assertThat(fields).hasSize(7);
                assertThat(fields.get(5)).matches(time);
            }
        }
        assertThat(fit1045Rows).isEqualTo(2);
    }

    @Test
    @DisplayName("T-FR5-05 commas and quotes round-trip through the CSV")
    void tFr5_05_quoting() throws Exception {
        register(fit1045, "2026-11-08T12:00:00+08:00");
        String csv = export();

        String row = csv.lines()
                .filter(l -> l.startsWith("FIT1045,") && l.contains(",MON,"))
                .findFirst()
                .orElseThrow();
        assertThat(row).contains("\"Clayton, \"\"Hall\"\" 25\"");
        List<String> fields = parseCsvLine(row);
        assertThat(fields.get(6)).isEqualTo("Clayton, \"Hall\" 25");
    }

    @Test
    @DisplayName("T-FR5-06 empty export is exactly the header plus CRLF")
    void tFr5_06_emptyExport() throws Exception {
        byte[] bytes = exportBytes();
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .isEqualTo(CSV_HEADER + "\r\n");
    }

    @Test
    @DisplayName("T-FR5-07 both read endpoints reject missing tokens")
    void tFr5_07_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/registrations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/registrations/export"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("T-FR5-08 one student never sees another student's registration ids")
    void tFr5_08_scopedToCaller() throws Exception {
        register(fit1045, "2026-11-08T12:00:00+08:00");
        JsonNode danielRegistration = body(postRegisterAuthed(bearer(daniel), tst0009.getId())
                .andExpect(status().isCreated()));
        String danielId = danielRegistration.path("registrationId").asText();

        JsonNode list = body(getAuthed("/api/registrations", bearer(aisha)));
        assertThat(list.path("items").size()).isEqualTo(1);
        for (JsonNode item : list.path("items")) {
            assertThat(item.path("registrationId").asText()).isNotEqualTo(danielId);
        }
    }

    @Test
    @DisplayName("T-FR5-09 export is UTF-8 without BOM and uses CRLF line endings")
    void tFr5_09_encoding() throws Exception {
        register(fit1045, "2026-11-08T12:00:00+08:00");
        byte[] bytes = exportBytes();

        assertThat(bytes.length).isGreaterThan(3);
        assertThat(bytes[0] & 0xFF).isNotEqualTo(0xEF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("\r\n");
        String withoutCrlf = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "");
        assertThat(withoutCrlf).doesNotContain("\n");
    }

    @Test
    @DisplayName("T-FR5-10 items follow registeredAt ASC; export follows course code ASC")
    void tFr5_10_ordering() throws Exception {
        // Inserted FIT2004 first; code order would put FIT1045 first.
        register(fit2004, "2026-11-08T12:00:00+08:00");
        register(fit1045, "2026-11-08T12:00:05+08:00");

        JsonNode list = body(getAuthed("/api/registrations", bearer(aisha)));
        assertThat(list.path("items").get(0).path("course").path("code").asText())
                .isEqualTo("FIT2004");

        String csv = export();
        String[] lines = csv.split("\r\n");
        String firstDataRow = lines[1];
        assertThat(firstDataRow).startsWith("FIT1045,");
    }

    private void register(Course course, String clock) throws Exception {
        MutableClock.setOffset(clock);
        postRegisterAuthed(bearer(aisha), course.getId()).andExpect(status().isCreated());
    }

    private String export() throws Exception {
        return new String(exportBytes(), StandardCharsets.UTF_8);
    }

    private byte[] exportBytes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/registrations/export")
                        .header("Authorization", bearer(aisha)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsByteArray();
    }

    /** Minimal RFC 4180 parser: handles quoted fields, doubled quotes and commas. */
    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
