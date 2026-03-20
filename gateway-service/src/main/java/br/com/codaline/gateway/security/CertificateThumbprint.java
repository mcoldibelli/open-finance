package br.com.codaline.gateway.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class CertificateThumbprint {

  private CertificateThumbprint() {
  }

  /**
   * Calculate thumbprint SHA-256 of a certificate X.509 in Base64url without padding. This value is
   * issued on JWT claim cnf.x5t#S256 (RFC 8705).
   */
  public static String computeS256(X509Certificate certificate) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(certificate.getEncoded());
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
      throw new SecurityException("Failed to compute certificate thumbprint", e);
    }
  }
}
