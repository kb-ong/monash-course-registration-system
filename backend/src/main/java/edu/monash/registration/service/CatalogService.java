package edu.monash.registration.service;

import edu.monash.registration.api.dto.CourseDto;
import edu.monash.registration.api.dto.SemesterDto;
import edu.monash.registration.domain.Course;
import edu.monash.registration.error.CourseNotFoundException;
import edu.monash.registration.error.ValidationException;
import edu.monash.registration.repository.CourseRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR2: semester list, catalogue search and course detail. Reads only. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private static final Pattern SEMESTER_CODE = Pattern.compile("^\\d{4}-S[12]$");
    private static final int MAX_QUERY_LENGTH = 100;

    private final CourseRepository courseRepository;
    private final CourseDtoMapper mapper;
    private final RegistrationPeriodService periodService;

    public CatalogService(CourseRepository courseRepository, CourseDtoMapper mapper,
            RegistrationPeriodService periodService) {
        this.courseRepository = courseRepository;
        this.mapper = mapper;
        this.periodService = periodService;
    }

    public List<SemesterDto> listSemesters() {
        String active = periodService.activeSemester();
        Set<String> codes = new LinkedHashSet<>(courseRepository.findDistinctSemesters());
        codes.add(active);
        List<SemesterDto> semesters = new ArrayList<>();
        codes.stream()
                .sorted((a, b) -> b.compareTo(a))
                .forEach(code -> semesters.add(new SemesterDto(code, displayName(code), code.equals(active))));
        return semesters;
    }

    public List<CourseDto> listCourses(String semesterParam, String q) {
        String semester = resolveSemester(semesterParam);
        String term = q == null ? "" : q.trim();
        if (term.length() > MAX_QUERY_LENGTH) {
            throw new ValidationException("q must be at most " + MAX_QUERY_LENGTH + " characters.");
        }
        List<Course> courses = term.isEmpty()
                ? courseRepository.findBySemesterOrderByCodeAsc(semester)
                : courseRepository.search(semester, term);
        log.debug("catalog.search semester={} q={} results={}", semester, term, courses.size());
        return mapper.toDtos(courses);
    }

    public CourseDto getCourse(String rawId) {
        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException ex) {
            throw new CourseNotFoundException();
        }
        Course course = courseRepository.findById(id).orElseThrow(CourseNotFoundException::new);
        return mapper.toDto(course);
    }

    private String resolveSemester(String semesterParam) {
        String active = periodService.activeSemester();
        if (semesterParam == null || semesterParam.isBlank()) {
            return active;
        }
        String semester = semesterParam.trim();
        if (!SEMESTER_CODE.matcher(semester).matches()) {
            throw new ValidationException("semester must match YYYY-S1 or YYYY-S2.");
        }
        if (!semester.equals(active) && !courseRepository.existsBySemester(semester)) {
            throw new ValidationException("Unknown semester: " + semester + ".");
        }
        return semester;
    }

    private String displayName(String code) {
        if (!SEMESTER_CODE.matcher(code).matches()) {
            return code;
        }
        String year = code.substring(0, 4);
        String number = code.substring(6);
        return "Semester " + number + " " + year;
    }
}
