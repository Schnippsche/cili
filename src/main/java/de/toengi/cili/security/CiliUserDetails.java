package de.toengi.cili.security;

import de.toengi.cili.model.entity.User;
import de.toengi.cili.model.enums.UserRole;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
public class CiliUserDetails implements UserDetails {

    @Getter
    private final User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override public String getPassword()  { return user.getPasswordHash(); }
    @Override public String getUsername()  { return user.getUsername(); }
    @Override public boolean isEnabled()   { return user.isActive(); }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    public Long getUserId() { return user.getId(); }
    public UserRole getRole() { return user.getRole(); }
}
