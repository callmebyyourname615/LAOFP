package com.example.switching.security.oauth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.switching.security.oauth.entity.OAuthClientEntity;

@Repository
public interface OAuthClientRepository extends JpaRepository<OAuthClientEntity, String> {

    /** Returns all OAuth clients for a given PSP (there may be more than one per PSP). */
    List<OAuthClientEntity> findByPspId(String pspId);
}
