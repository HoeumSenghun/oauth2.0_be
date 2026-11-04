package com.oauth.oauth2_be.security;

import java.time.Duration;

public interface TokenRevocationService {

    void revoke(String jti, Duration ttl);

    boolean isRevoked(String jti);
}
