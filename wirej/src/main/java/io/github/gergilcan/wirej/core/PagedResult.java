package io.github.gergilcan.wirej.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PagedResult<T> {
  private T[] data;
  private long totalCount;
}
