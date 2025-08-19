package io.github.gergilcan.wirej.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RequestPagination {
  private Integer pageNumber = 0;
  private Integer pageSize = 10;
}
