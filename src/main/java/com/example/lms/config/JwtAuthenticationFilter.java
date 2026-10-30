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
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
                    var principal = new AuthPrincipal(user.getId(), user.getEmail(), user.getRole());
                    var authToken = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }
        filterChain.doFilter(request, response);
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