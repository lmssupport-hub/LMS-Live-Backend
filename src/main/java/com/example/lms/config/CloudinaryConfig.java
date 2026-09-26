package com.example.lms.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {

    // Set this as a single environment variable in Render, e.g.:
    // CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>
    // (Cloudinary's dashboard shows this exact string ready to copy.)
    @Value("${app.cloudinary.url}")
    private String cloudinaryUrl;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(cloudinaryUrl);
    }
}