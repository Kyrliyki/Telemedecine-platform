package com.telemed.auth;

import com.telemed.auth.infrastructure.config.AuthProperties;
import com.telemed.auth.infrastructure.config.BootstrapAdminProperties;
import com.telemed.auth.infrastructure.config.JwtKeyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
  AuthProperties.class,
  BootstrapAdminProperties.class,
  JwtKeyProperties.class
})
public class AuthServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthServiceApplication.class, args);
  }
}
