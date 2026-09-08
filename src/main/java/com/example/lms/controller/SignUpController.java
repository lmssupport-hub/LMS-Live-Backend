package com.example.lms.controller;
import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.SignUpDto;
import com.example.lms.service.SignUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/signup")
@RequiredArgsConstructor
public class SignUpController {
    private final SignUpService signUpService;

    @PostMapping
    public ResponseEntity<ApiResponse<SignUpDto.SignUpResponse>> signUp(@Valid @RequestBody SignUpDto.SignUpRequest request) {
        SignUpDto.SignUpResponse response = signUpService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully.", response));
    }

    /** GET /api/auth/signup/admins — Super Admin only. User-table fields only. */
    @GetMapping("/admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SignUpDto.AdminSummary>>> listAdmins(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<SignUpDto.AdminSummary> result = signUpService.listAdmins(search, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Admin list fetched successfully.", result));
    }
}