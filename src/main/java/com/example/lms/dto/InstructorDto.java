package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InstructorDto {
    private Long id;
    private String name;
    private String email;
}