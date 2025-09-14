package io.github.gergilcan.wirej;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.gergilcan.wirej.core.security.RbacPermissionsRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class UserFilter extends OncePerRequestFilter {
  private static final String X_AUTH_ROLE = "x-auth-role";

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var roleName = request.getHeader(X_AUTH_ROLE);
    if (roleName != null) {
      UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("principal", null,
          List.of(new SimpleGrantedAuthority("ROLE_" + roleName)));
      if (authentication.getPrincipal() != null) {
        RbacPermissionsRegistry.findByRole(roleName)
            .ifPresent(authentication::setDetails);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }
    filterChain.doFilter(request, response);
  }
}
