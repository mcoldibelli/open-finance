package br.com.codaline.gateway.config;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class CertificateGenerator {

  private CertificateGenerator() {
  }

  public static KeyPair generateRsaKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048, new SecureRandom());
    return generator.generateKeyPair();
  }

  public static X509Certificate generateCertificate(KeyPair keyPair, String cn)
      throws OperatorCreationException, CertificateException {
    X500Name subject = new X500Name("CN=" + cn + ",O=Test,C=BR");
    Instant now = Instant.now();

    JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
        subject,
        BigInteger.valueOf(System.currentTimeMillis()),
        Date.from(now),
        Date.from(now.plus(365, ChronoUnit.DAYS)),
        subject,
        keyPair.getPublic()
    );

    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
        .build(keyPair.getPrivate());

    return new JcaX509CertificateConverter()
        .getCertificate(builder.build(signer));
  }

  public static String computeThumbprintS256(X509Certificate certificate)
      throws CertificateEncodingException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(digest.digest(certificate.getEncoded()));
  }
}
