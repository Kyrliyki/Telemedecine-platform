package com.telemed.auth.infrastructure.security;

import com.telemed.auth.infrastructure.config.JwtKeyProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PersistentKeyPairProvider {

  private static final Logger log = LoggerFactory.getLogger(PersistentKeyPairProvider.class);

  private static final String PRIVATE_KEY_FILE = "private.pem";
  private static final String PUBLIC_KEY_FILE = "public.pem";
  private static final String PRIVATE_KEY_TYPE = "PRIVATE KEY";
  private static final String PUBLIC_KEY_TYPE = "PUBLIC KEY";

  private final Path directory;
  private final String keyId;

  public PersistentKeyPairProvider(JwtKeyProperties properties) {
    this.directory = Paths.get(properties.keyDirectory()).toAbsolutePath().normalize();
    this.keyId = properties.keyId();
  }

  public synchronized KeyPair loadOrCreate() {
    try {
      Files.createDirectories(directory);

      Path privatePath = directory.resolve(PRIVATE_KEY_FILE);
      Path publicPath = directory.resolve(PUBLIC_KEY_FILE);
      boolean privateExists = Files.exists(privatePath);
      boolean publicExists = Files.exists(publicPath);

      if (privateExists && publicExists) {
        KeyPair keyPair = readKeyPair(privatePath, publicPath);
        log.info("Loaded persistent JWT key pair from {} (kid={})", directory, keyId);
        return keyPair;
      }

      if (privateExists || publicExists) {
        throw new IllegalStateException(
            "Incomplete JWT key material in "
                + directory
                + ": both private.pem and public.pem are required");
      }

      KeyPair keyPair = generateKeyPair();
      writePem(privatePath, PRIVATE_KEY_TYPE, keyPair.getPrivate().getEncoded());
      writePem(publicPath, PUBLIC_KEY_TYPE, keyPair.getPublic().getEncoded());
      log.info("Generated persistent JWT key pair in {} (kid={})", directory, keyId);
      return keyPair;
    } catch (IOException | GeneralSecurityException ex) {
      throw new IllegalStateException("Failed to load or create JWT key pair in " + directory, ex);
    }
  }

  private KeyPair readKeyPair(Path privatePath, Path publicPath)
      throws IOException, GeneralSecurityException {
    byte[] privateBytes = readPem(privatePath, PRIVATE_KEY_TYPE);
    byte[] publicBytes = readPem(publicPath, PUBLIC_KEY_TYPE);

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    RSAPrivateKey privateKey =
        (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
    RSAPublicKey publicKey =
        (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));
    return new KeyPair(publicKey, privateKey);
  }

  private KeyPair generateKeyPair() throws GeneralSecurityException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static void writePem(Path path, String type, byte[] encoded) throws IOException {
    String pem =
        "-----BEGIN "
            + type
            + "-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(encoded)
            + "\n-----END "
            + type
            + "-----\n";
    Files.writeString(path, pem, StandardCharsets.US_ASCII);
    try {
      Set<PosixFilePermission> permissions =
          type.equals(PRIVATE_KEY_TYPE)
              ? EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
              : EnumSet.of(
                  PosixFilePermission.OWNER_READ,
                  PosixFilePermission.OWNER_WRITE,
                  PosixFilePermission.GROUP_READ,
                  PosixFilePermission.OTHERS_READ);
      Files.setPosixFilePermissions(path, permissions);
    } catch (UnsupportedOperationException ignored) {

    }
  }

  private static byte[] readPem(Path path, String type) throws IOException {
    String content = Files.readString(path, StandardCharsets.US_ASCII);
    String begin = "-----BEGIN " + type + "-----";
    String end = "-----END " + type + "-----";
    int start = content.indexOf(begin);
    int finish = content.indexOf(end);
    if (start < 0 || finish < 0 || finish <= start) {
      throw new IOException("Invalid " + type + " PEM in " + path);
    }
    String base64 = content.substring(start + begin.length(), finish).replaceAll("\\s", "");
    return Base64.getDecoder().decode(base64);
  }
}
