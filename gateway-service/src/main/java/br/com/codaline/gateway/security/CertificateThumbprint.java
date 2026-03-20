package br.com.codaline.gateway.security;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class CertificateThumbprint {

  private CertificateThumbprint() {
  }

  /**
   * Calcula o thumbprint SHA-256 de um certificado X.509 em Base64url sem padding. Esse é o valor
   * que deve estar no claim cnf.x5t#S256 do JWT (RFC 8705).
   */
  public static String computeS256(X509Certificate certificate) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(certificate.getEncoded());
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (Exception e) {
      throw new SecurityException("Failed to compute certificate thumbprint", e);
    }
  }
}
