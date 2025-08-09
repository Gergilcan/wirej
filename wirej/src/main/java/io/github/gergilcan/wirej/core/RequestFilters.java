package io.github.gergilcan.wirej.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class RequestFilters {
  private String filters;
  private String search;
  @Builder.Default
  private String sort = "id==DESC";

  public void addFilter(String filter) {
    if (filters == null) {
      filters = filter;
    } else {
      filters += ";" + filter;
    }
  }
}
