package io.github.gergilcan.wirej.core.security;

import io.github.gergilcan.wirej.annotations.ValidatePermission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Component
@Slf4j
public class PermissionValidator implements ConstraintValidator<ValidatePermission, Object> {
  private RoleEntityPermissions role;
  private ValidatePermission annotation;

  @Override
  public void initialize(ValidatePermission constraintAnnotation) {
    log.debug("Initializing PermissionValidator..."); // Debug output
    this.annotation = constraintAnnotation;
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated.");
    }
    // Fetch role details from security context or user service from the details
    if (SecurityContextHolder.getContext().getAuthentication()
        .getDetails() instanceof RoleEntityPermissions existingRole) {
      role = existingRole;
    } else {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User role not found.");
    }
  }

  @Override
  public boolean isValid(Object objectToValidate, ConstraintValidatorContext cxt) {
    boolean isValid = false;

    isValid = validateRolePermission(role, (Method) objectToValidate);

    if (!isValid) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to access this resource."); // Throw
                                                                                                                      // exception
    }

    return isValid;
  }

  private boolean validateRolePermission(RoleEntityPermissions role2, Method objectToValidate) {
    var groupName = objectToValidate.getDeclaringClass().getSimpleName().replace("Controller", "");
    var groups = role2.getPermissions();
    if (groups != null && groups.containsKey(groupName)) {
      var set = groups.get(groupName);
      if (set != null && set.contains(annotation.value()))
        return true;
    }
    return false;
  }
}
