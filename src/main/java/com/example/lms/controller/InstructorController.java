package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.InstructorDto;
import com.example.lms.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instructors")
public class InstructorController {

    private final UserRepository userRepository;

    public InstructorController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<InstructorDto>>> getActiveInstructors() {
        List<InstructorDto> instructors = userRepository
                .findByRoleIgnoreCaseAndActiveTrue("INSTRUCTOR")   // CHANGED
                .stream()
                .map(u -> new InstructorDto(
                        u.getId(),
                        (u.getFirstName() + " " + u.getLastName()).trim(),
                        u.getEmail()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Instructors fetched successfully.", instructors));
    }
}