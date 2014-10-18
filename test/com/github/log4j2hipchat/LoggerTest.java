/**
 * Copyright 2013, Somete Group, LLC. All rights reserved. $LastChangedDate$
 * $LastChangedBy$ $Revision$
 */
package com.github.log4j2hipchat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Example of log4j2 logging
 */
public final class LoggerTest {

  public static void main(String[] args) {
    Logger log = LogManager.getLogger();

    log.warn("Danger, Will Robinson! Exception approaching!");
    try {
      int[] foo = new int[1];
      foo[1] = 0;
    } catch (Exception e) {
      log.error("Arrays are indexed by zero!", e);
    }
  }
}