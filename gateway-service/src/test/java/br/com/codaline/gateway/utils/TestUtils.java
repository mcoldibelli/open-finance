package br.com.codaline.gateway.utils;

import static br.com.codaline.gateway.IntegrationTestBase.AS_KEY_PAIR;
import static br.com.codaline.gateway.IntegrationTestBase.CLIENT_THUMBPRINT;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.Map;

public class TestUtils {

  private static final String ISSUER = "https://as.localdev.codaline";
  private static final String AUDIENCE = "https://gateway.localdev.codaline";

  public static String gerarToken(String jti) throws JOSEException {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .jwtID(jti)
        .issuer(ISSUER)
        .audience(AUDIENCE)
        .claim("consent_id", "consent-filter-test")
        .claim("cpf", "12345678900")
        .claim("client_id", "tpp-simulado")
        .claim("cnf", Map.of("x5t#S256", CLIENT_THUMBPRINT))
        .issueTime(new Date())
        .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims
    );
    jwt.sign(new RSASSASigner(AS_KEY_PAIR.getPrivate()));

    return jwt.serialize();
  }


}
