package edu.monash.registration.api;

import edu.monash.registration.api.dto.CourseDto;
import edu.monash.registration.api.dto.SemesterDto;
import edu.monash.registration.service.CatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** E3, E4 and E5. */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/semesters")
    public List<SemesterDto> semesters() {
        return catalogService.listSemesters();
    }

    @GetMapping("/courses")
    public List<CourseDto> courses(
            @RequestParam(name = "semester", required = false) String semester,
            @RequestParam(name = "q", required = false) String q) {
        return catalogService.listCourses(semester, q);
    }

    @GetMapping("/courses/{id}")
    public CourseDto course(@PathVariable("id") String id) {
        return catalogService.getCourse(id);
    }
}
