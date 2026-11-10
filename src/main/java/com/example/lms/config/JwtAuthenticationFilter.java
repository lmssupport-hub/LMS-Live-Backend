package com.example.lms.config;

import com.example.lms.entity.User;
import com.example.lms.repository.UserRepository;
import com.example.lms.service.PackageService;
import com.example.lms.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;


@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PackageService packageService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.isTokenValid(token)) {
                Long userId = jwtUtil.extractUserId(token);
                User user = userId == null ? null : userRepository.findById(userId).orElse(null);

                
                if (user != null && isSessionAllowed(user)) {
                    var authorities = List.of(new SimpleGrantedAuthority(toAuthority(user)));
                    var principal = new AuthPrincipal(user.getId(), user.getEmail(), user.getRole());
                    var authToken = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Spring authority for the user's role. The role is stored the way it was typed in Role Management
     * ("Instructor", "Content Manager"), so it is upper-cased and spaces become "_":
     * "Instructor" -> ROLE_INSTRUCTOR, which makes hasRole('INSTRUCTOR') work.
     * An invited team member can NEVER get ROLE_ADMIN / ROLE_SUPER_ADMIN, even if their role name is
     * "Admin" - only the company's own admin (no companyAdminId) and the app owner can.
     */
    private static String toAuthority(User user) {
        String normalized = user.getRole() == null
                ? ""
                : user.getRole().trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
        boolean teamMember = user.getCompanyAdminId() != null;
        if (teamMember && ("ADMIN".equals(normalized) || "SUPER_ADMIN".equals(normalized))) {
            normalized = "TEAM_MEMBER";
        }
        return "ROLE_" + normalized;
    }

    /** Active user, and company ADMIN-ku Active package + login access irukkanum. */
    private boolean isSessionAllowed(User user) {
        if (!Boolean.TRUE.equals(user.getActive())) {
            return false;
        }
        if (PackageService.isPackageGatedAdmin(user)) {
            return packageService.hasLoginAccess(user.getPackageId());
        }
        return true;
    }
}