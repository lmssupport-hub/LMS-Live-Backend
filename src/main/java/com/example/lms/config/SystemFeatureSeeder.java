package com.example.lms.config;

import com.example.lms.entity.SystemFeatureEntity;
import com.example.lms.repository.SystemFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SystemFeatureSeeder implements ApplicationRunner {

    private final SystemFeatureRepository systemFeatureRepository;

    @Override
    public void run(ApplicationArguments args) {
        seed("AUTH_USER_ACCESS", "AUTHENTICATION", "Authentication", "User Authentication & Access");

        seed("COURSE_MGMT_CREATE_COURSE", "COURSE_MGMT", "Course Management", "Create Course");
        seed("COURSE_MGMT_COURSE_LIST", "COURSE_MGMT", "Course Management", "Course List");
        seed("COURSE_MGMT_ORGANIZE_MODULES", "COURSE_MGMT", "Course Management", "Organize Modules");

        seed("CONTENT_MGMT_MULTIMEDIA", "CONTENT_MGMT", "Content Management", "Multimedia");
        seed("CONTENT_MGMT_EBOOK", "CONTENT_MGMT", "Content Management", "Ebook");
        seed("CONTENT_MGMT_ORGANIZE_MODULES", "CONTENT_MGMT", "Content Management", "Organize Modules");

        seed("ENROLLMENT_ENROLL_UNENROLL", "ENROLLMENT", "Enrollment", "Enroll/Unroll in Course");
        seed("ENROLLMENT_ENROLLED_LIST", "ENROLLMENT", "Enrollment", "Enrolled Course List");
    }

    private void seed(String id, String categoryId, String categoryName, String name) {
        if (systemFeatureRepository.existsById(id)) {
            return;
        }
        SystemFeatureEntity entity = new SystemFeatureEntity();
        entity.setId(id);
        entity.setCategoryId(categoryId);
        entity.setCategoryName(categoryName);
        entity.setName(name);
        entity.setActive(true);
        systemFeatureRepository.save(entity);
    }
}