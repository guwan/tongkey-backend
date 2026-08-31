package com.tongkey.openapi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<ClientEntity, String> {

    Optional<ClientEntity> findByApiKey(String apiKey);

    Optional<ClientEntity> findByClientId(String clientId);
}
