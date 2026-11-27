package com.telemed.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemed.auth.infrastructure.config.JwtKeyProperties;
import java.nio.file.Path;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistentKeyPairProviderTest {

  @TempDir Path tempDirectory;

  @Test
  void createsAndReloadsTheSameKeyPair() {
    JwtKeyProperties properties = new JwtKeyProperties(tempDirectory.toString(), "test-key");
    PersistentKeyPairProvider provider = new PersistentKeyPairProvider(properties);

    KeyPair first = provider.loadOrCreate();
    KeyPair second = provider.loadOrCreate();

    assertThat(second.getPublic().getEncoded()).isEqualTo(first.getPublic().getEncoded());
    assertThat(second.getPrivate().getEncoded()).isEqualTo(first.getPrivate().getEncoded());
    assertThat(tempDirectory.resolve("private.pem")).exists();
    assertThat(tempDirectory.resolve("public.pem")).exists();
  }
}
