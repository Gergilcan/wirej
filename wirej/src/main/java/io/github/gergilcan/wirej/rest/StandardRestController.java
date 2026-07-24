package io.github.gergilcan.wirej.rest;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.github.gergilcan.wirej.annotations.ServiceMethod;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;

/**
 * A standard REST CRUD surface a controller interface can extend instead of
 * redeclaring GET/POST/PUT/PATCH/DELETE by hand. The extending interface still
 * needs its own {@code @RestController}, {@code @RequestMapping} and
 * {@code @ServiceClass(YourService.class)} - only the methods are inherited.
 *
 * Each method relies on the default {@code @ServiceMethod} name matching, so
 * the service class must implement matching methods directly: {@code get},
 * {@code getAll}, {@code create}, {@code update}, {@code patch}, {@code delete}.
 * Overriding one of these methods to change its mapping requires re-adding
 * {@code @ServiceMethod} on the override - annotations don't carry across a
 * Java override, so a bare override silently drops out of generation.
 *
 * {@code update} (PUT) is a full replacement of the entity at {@code id};
 * {@code patch} (PATCH) is a partial update from a field/value map. They are
 * distinct HTTP verbs on {@code /{id}} and map to distinct service methods
 * ({@code update(ID, T)} vs {@code patch(ID, Map)}).
 */
public interface StandardRestController<T, ID> {
  @GetMapping("/{id}")
  @ServiceMethod
  @ResponseStatus(HttpStatus.OK)
  ResponseEntity<?> get(@PathVariable("id") ID id);

  @GetMapping("/")
  @ServiceMethod
  @ResponseStatus(HttpStatus.OK)
  ResponseEntity<?> getAll(RequestFilters filters, RequestPagination pagination);

  @PostMapping("/")
  @ServiceMethod
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<?> create(@RequestBody T entity);

  @PutMapping("/{id}")
  @ServiceMethod
  @ResponseStatus(HttpStatus.OK)
  ResponseEntity<?> update(@PathVariable("id") ID id, @RequestBody T entity);

  @PatchMapping("/{id}")
  @ServiceMethod
  @ResponseStatus(HttpStatus.OK)
  ResponseEntity<?> patch(@PathVariable("id") ID id, @RequestBody Map<String, Object> changes);

  @DeleteMapping("/{id}")
  @ServiceMethod
  @ResponseStatus(HttpStatus.NO_CONTENT)
  ResponseEntity<?> delete(@PathVariable("id") ID id);
}
