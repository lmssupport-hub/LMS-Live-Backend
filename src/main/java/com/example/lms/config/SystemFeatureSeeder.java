package com.example.lms.config;

import com.example.lms.entity.SystemFeatureEntity;
import com.example.lms.repository.SystemFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SystemFeatureSeeder implements ApplicationRunner {

    private final SystemFeatureRepository systemFeatureRepository;

    @Override
    public void run(ApplicationArguments args) {
        seed("AUTH_USER_ACCESS", "AUTHENTICATION", "Authentication", "User Authentication & Access", 10);

        seed("COURSE_MGMT_CREATE_COURSE", "COURSE_MGMT", "Course Management", "Create Course", 20);
        seed("COURSE_MGMT_COURSE_LIST", "COURSE_MGMT", "Course Management", "Course List", 21);
        seed("COURSE_MGMT_ORGANIZE_MODULES", "COURSE_MGMT", "Course Management", "Organize Modules", 22);

        seed("CONTENT_MGMT_MULTIMEDIA", "CONTENT_MGMT", "Content Management", "Multimedia", 30);
        seed("CONTENT_MGMT_EBOOK", "CONTENT_MGMT", "Content Management", "Ebook", 31);
        seed("CONTENT_MGMT_ORGANIZE_MODULES", "CONTENT_MGMT", "Content Management", "Organize Modules", 32);

        seed("ENROLLMENT_ENROLL_UNENROLL", "ENROLLMENT", "Enrollment", "Enroll/Unroll in Course", 40);
        seed("ENROLLMENT_ENROLLED_LIST", "ENROLLMENT", "Enrollment", "Enrolled Course List", 41);
    }

    private void seed(String id, String categoryId, String categoryName, String name, int displayOrder) {
        SystemFeatureEntity entity = systemFeatureRepository.findById(id).orElse(null);
        if (entity != null) {
            // Existing row: only sync the display order, leave everything else untouched.
            if (entity.getDisplayOrder() != displayOrder) {
                entity.setDisplayOrder(displayOrder);
                systemFeatureRepository.save(entity);
            }
            return;
        }
        entity = new SystemFeatureEntity();
        entity.setId(id);
        entity.setCategoryId(categoryId);
        entity.setCategoryName(categoryName);
        entity.setName(name);
        entity.setDisplayOrder(displayOrder);
        entity.setActive(true);
        systemFeatureRepository.save(entity);
    }
}