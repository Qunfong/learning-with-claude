package com.example.demo.auth.service;

import com.example.demo.auth.entity.OAuthClient;
import com.example.demo.auth.repository.OAuthClientRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OAuthClientService {

    private final OAuthClientRepository clientRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public OAuthClientService(OAuthClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional
    public ClientRegistrationResult register(String name, String type, List<String> redirectUris) {
        validateRedirectUris(redirectUris);

        String clientId = UUID.randomUUID().toString();
        String plainSecret = null;
        String secretHash = null;

        if ("confidential".equals(type)) {
            plainSecret = UUID.randomUUID().toString();
            secretHash = passwordEncoder.encode(plainSecret);
        }

        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientSecretHash(secretHash);
        client.setName(name);
        client.setType(type);
        client.setRedirectUris(redirectUris);
        clientRepository.save(client);

        return new ClientRegistrationResult(clientId, plainSecret);
    }

    @Transactional
    public boolean deactivate(String clientId) {
        return clientRepository.deactivateByClientId(clientId) > 0;
    }

    public Optional<OAuthClient> findActiveClient(String clientId) {
        return clientRepository.findByClientId(clientId)
                .filter(OAuthClient::isActive);
    }

    public void validateRedirectUris(List<String> redirectUris) {
        for (String uri : redirectUris) {
            try {
                URI parsed = new URI(uri);
                if (!parsed.isAbsolute()) {
                    throw new IllegalArgumentException("Redirect URI must be absolute: " + uri);
                }
                if (parsed.getFragment() != null) {
                    throw new IllegalArgumentException("Redirect URI must not contain a fragment: " + uri);
                }
            } catch (java.net.URISyntaxException e) {
                throw new IllegalArgumentException("Invalid redirect URI: " + uri);
            }
        }
    }

    public boolean isValidRedirectUri(OAuthClient client, String redirectUri) {
        return client.getRedirectUris().contains(redirectUri);
    }

    public record ClientRegistrationResult(String clientId, String clientSecret) {}
}
