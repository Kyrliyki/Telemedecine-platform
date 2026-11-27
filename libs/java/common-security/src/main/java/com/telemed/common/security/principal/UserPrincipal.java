package com.telemed.common.security.principal;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class UserPrincipal implements UserDetails {

  private final UUID userId;
  private final String email;
  private final Set<String> roles;
  private final boolean enabled;

  public UserPrincipal(UUID userId, String email, Set<String> roles, boolean enabled) {
    this.userId = userId;
    this.email = email;
    this.roles = Set.copyOf(roles);
    this.enabled = enabled;
  }

  public UUID userId() {
    return userId;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles.stream()
        .map(r -> (GrantedAuthority) () -> "ROLE_" + r)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public String getPassword() {
    return null;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return enabled;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }
}
