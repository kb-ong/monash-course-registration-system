package edu.monash.registration.service;

import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.CourseSchedule;
import edu.monash.registration.domain.DayOfWeekCode;
import edu.monash.registration.domain.Registration;
import edu.monash.registration.domain.Student;
import edu.monash.registration.error.UnauthorizedException;
import edu.monash.registration.repository.RegistrationRepository;
import edu.monash.registration.repository.StudentRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR5 CSV export: RFC 4180, CRLF, UTF-8 without BOM, one row per registration × schedule slot.
 */
@Service
public class CsvExportService {

    private static final String HEADER =
            "Course Code,Course Name,Credit Hours,Instructor,Day,Time,Location";

    private static final String CRLF = "\r\n";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final RegistrationRepository registrationRepository;
    private final StudentRepository studentRepository;
    private final RegistrationPeriodService periodService;

    public CsvExportService(RegistrationRepository registrationRepository,
            StudentRepository studentRepository, RegistrationPeriodService periodService) {
        this.registrationRepository = registrationRepository;
        this.studentRepository = studentRepository;
        this.periodService = periodService;
    }

    @Transactional(readOnly = true)
    public byte[] export(String studentId) {
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(UnauthorizedException::new);
        List<Registration> registrations = registrationRepository
                .findAllForStudentInSemester(student.getId(), periodService.activeSemester());

        List<Row> rows = new ArrayList<>();
        for (Registration registration : registrations) {
            Course course = registration.getCourse();
            for (CourseSchedule slot : course.getSchedule()) {
                rows.add(new Row(course.getCode(), course.getName(), course.getCreditHours(),
                        course.getInstructor(), slot.getDay(), slot.getStartTime(),
                        slot.getEndTime(), slot.getLocation()));
            }
        }
        rows.sort(Comparator.comparing(Row::code)
                .thenComparing(Row::day)
                .thenComparing(Row::startTime));

        StringBuilder csv = new StringBuilder(HEADER).append(CRLF);
        for (Row row : rows) {
            csv.append(escape(row.code())).append(',')
                    .append(escape(row.name())).append(',')
                    .append(row.creditHours()).append(',')
                    .append(escape(row.instructor())).append(',')
                    .append(row.day().name()).append(',')
                    .append(row.startTime().format(TIME)).append('-')
                    .append(row.endTime().format(TIME)).append(',')
                    .append(escape(row.location())).append(CRLF);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private record Row(String code, String name, int creditHours, String instructor,
            DayOfWeekCode day, LocalTime startTime, LocalTime endTime, String location) {
    }
}
