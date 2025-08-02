package com.gergilcan.wirej.rsqlParser;

public abstract class RSQLOperators {
  private RSQLOperators() {
  }

  public static final String EQUAL = "==";
  public static final String NOT_EQUAL = "!=";
  public static final String GREATER_THAN_OR_EQUAL = ">=";
  public static final String GREATER_THAN = ">";
  public static final String LESS_THAN_OR_EQUAL = "<=";
  public static final String LESS_THAN = "<";
  public static final String IN = "=in=";
  public static final String NOT_IN = "=out=";
}