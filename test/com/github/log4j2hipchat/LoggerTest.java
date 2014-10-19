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

  public static Logger log = LogManager.getLogger();

  public static void main(String[] args) {
    log.info("This is a test of the HipChat Appender.");
    log.warn("Danger, Will Robinson! Exception approaching!");
    bustArray(42);
    log.fatal("Die! Die! Die!");
  }

  public static int bustArray(int size) {
    int retVal = 0;
    try {
      int[] foo = new int[size];
      retVal = foo[size];
    } catch (Exception e) {
      log.error("Arrays are indexed by zero!", e);
    }
    return retVal;
  }
}