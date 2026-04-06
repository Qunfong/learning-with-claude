package com.example.demo.auth.filter;

import com.example.demo.auth.model.UserIdentity;
import com.example.demo.auth.service.TokenService;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class BearerTokenAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenAuthFilter.class);
    public static final String IDENTITY_ATTRIBUTE = "userIdentity";

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/oauth/", "/.well-known/", "/health", "/admin/"
    );

    @Value("${AUTH_ENFORCE:false}")
    private boolean authEnforce;

    private final TokenService tokenService;

    public BearerTokenAuthFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (authEnforce) {
                sendError(response, 401, "missing_token", "Authorization header is required");
                return;
            }
            log.warn("AUDIT: Missing Authorization header for {}", path);
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        Optional<SignedJWT> jwtOpt = tokenService.validateAccessToken(token);

        if (jwtOpt.isEmpty()) {
            String error = isExpiredToken(token) ? "token_expired" : "invalid_token";
            if (authEnforce) {
                sendError(response, 401, error, "Token validation failed");
                return;
            }
            log.warn("AUDIT: Invalid token ({}) for {}", error, path);
            chain.doFilter(request, response);
            return;
        }

        try {
            SignedJWT jwt = jwtOpt.get();
            String sub = jwt.getJWTClaimsSet().getSubject();
            String clientId = (String) jwt.getJWTClaimsSet().getClaim("client_id");
            String jti = jwt.getJWTClaimsSet().getJWTID();
            request.setAttribute(IDENTITY_ATTRIBUTE, new UserIdentity(sub, clientId, jti));
        } catch (Exception e) {
            log.error("Failed to extract JWT claims", e);
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isExpiredToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            java.util.Date exp = jwt.getJWTClaimsSet().getExpirationTime();
            return exp != null && exp.before(new java.util.Date());
        } catch (Exception e) {
            return false;
        }
    }

    private void sendError(HttpServletResponse response, int status, String error, String description)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write(
                "{\"error\":\"" + error + "\",\"error_description\":\"" + description + "\"}");
    }
}
