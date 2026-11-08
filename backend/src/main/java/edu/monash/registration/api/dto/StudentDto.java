package edu.monash.registration.api.dto;

import edu.monash.registration.domain.Student;

/** Public student profile — never contains password material (NFR2-S6). */
public record StudentDto(String studentId, String name, String email, String program) {

    public static StudentDto from(Student student) {
        return new StudentDto(student.getStudentId(), student.getName(), student.getEmail(),
                student.getProgram());
    }
}
