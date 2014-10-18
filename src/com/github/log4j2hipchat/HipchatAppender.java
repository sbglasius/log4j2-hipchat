/*-
 * Copyright 2014 Daniel Widdis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Authored by Daniel Widdis widdis@gmail.com
 */
package com.github.log4j2hipchat;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import com.hipchat.api.v1.HipChatAPI;
import com.hipchat.api.v1.HipChatAPI.Method;

@Plugin(name = "Hipchat", category = "Core", elementType = "appender", printObject = true)
public class HipchatAppender extends AbstractAppender {

  private final boolean includeSource;

  private final boolean includeThreadContext;

  private final boolean includeStackTrace;

  private final String authToken;

  private final String roomId;

  private String from;

  private boolean includeTimeStamp;

  private final Boolean notify;

  private final String color;

  // Valid Hipchat colors
  private static final Set<String> VALID_COLORS = new HashSet<String>(
      Arrays.asList(new String[] {"yellow", "red", "green", "purple", "gray",
          "random"}));

  // Hipchat API path for rooms
  private static final String PATH = "rooms/message";

  protected HipchatAppender(final String name,
      final Layout<? extends Serializable> layout, final Filter filter,
      final boolean ignoreExceptions, final boolean includeSource,
      final boolean includeThreadContext, final boolean includeStackTrace,
      final String authToken, final String roomId, final String from,
      final boolean includeTimeStamp, final boolean notify, final String color) {
    super(name, filter, layout, ignoreExceptions);
    this.includeSource = includeSource;
    this.includeThreadContext = includeThreadContext;
    this.includeStackTrace = includeStackTrace;
    this.authToken = authToken;
    this.roomId = roomId;
    this.from = from;
    this.includeTimeStamp = includeTimeStamp;
    this.notify = notify;
    this.color = color;

  }

  @Override
  public void append(LogEvent event) {
    // Instantiate API
    HipChatAPI hipchatApi = HipChatAPI.INSTANCE;
    hipchatApi.setAuthToken(authToken);

    final StringBuilder builder = new StringBuilder();

    // TimeStamp
    if (includeTimeStamp) {
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      Date timeStamp = new Date();
      timeStamp.setTime(event.getTimeMillis());
      builder.append(dateFormat.format(timeStamp));
    }

    // Level
    if (from.contains("$LEVEL")) {
      from = from.replace("$LEVEL", event.getLevel().name());
    } else {
      builder.append(builder.length() > 0 ? " " : "");
      builder.append(event.getLevel().name());
    }

    // Simple Source Class name
    final StackTraceElement source = event.getSource();
    String className = source != null ? source.getFileName() : "";
    if (className.indexOf(".") > 0) {
      className = className.substring(0, className.indexOf("."));
    }
    if (from.contains("$CLASS")) {
      from = from.replace("$CLASS", className);
    } else {
      builder.append(builder.length() > 0 ? " " : "");
      builder.append(className);
    }

    // Truncate from to 15 characters
    from = from.substring(0, Math.min(15, from.length()));
    // Add a newline if we've got any text so far.
    builder.append(builder.length() > 0 ? "\n" : "");

    // Message
    builder.append(event.getMessage().getFormattedMessage());

    // Marker
    final Marker marker = event.getMarker();
    if (marker != null) {
      builder.append("\nmarker=").append(marker.getName());
    }

    // Thread Context
    if (includeThreadContext) {
      for (Map.Entry<String, String> entry : event.getContextMap().entrySet()) {
        builder.append("\n").append(entry.getKey()).append("=")
            .append(entry.getValue());
      }

      final List<String> contextStack = event.getContextStack().asList();
      if (contextStack != null && !contextStack.isEmpty()) {
        builder.append("\ncontextStack=").append(contextStack.toString());
      }
    }

    // Stack Trace
    if (includeSource && source != null) {
      builder.append("\n<i>").append(source.getFileName());
      builder.append(":").append(source.getLineNumber());
      builder.append(" [").append(source.getClassName());
      builder.append("#").append(source.getMethodName()).append("()]</i>");
    }

    @SuppressWarnings("all")
    final Throwable thrown = event.getThrown();
    if (includeStackTrace && thrown != null) {
      final StringBuilder stackTraceBuilder = new StringBuilder();
      for (StackTraceElement stackTraceElement : thrown.getStackTrace()) {
        new Formatter(stackTraceBuilder).format("\n%s.%s(%s:%d)%n",
            stackTraceElement.getClassName(),
            stackTraceElement.getMethodName(), stackTraceElement.getFileName(),
            stackTraceElement.getLineNumber());
      }

      builder.append("\nexceptionClass=").append(
          thrown.getClass().getCanonicalName());
      builder.append("\nexceptionMessage=").append(thrown.getMessage());
      builder.append("\nexceptionStackTrace=").append(
          stackTraceBuilder.toString());
    }

    if (!postToHipchat(roomId, from, builder.toString()
        .replace("\n", "<br \\>"), notify, color)) {
      throw new AppenderLoggingException(
          "failed to write log event to Hipchat server: " + builder.toString());
    }
  }

  /**
   * Posts to Hipchat. Fails silently and returns false if any of the parameters
   * are invalid.
   * 
   * @param room
   *        Name of the room to post to
   * @param username
   *        Name to post as. Limited to 15 characters
   * @param message
   *        Text of the post. Must be URL-safe (no ?,&,+, maybe others)
   * @param notify
   *        Whether to notify users of the message
   * @param color
   *        What color: options "yellow", "red", "green", "purple", "gray", or
   *        "random"; default yellow
   * @return True if the post was successfully received
   */
  private boolean postToHipchat(String room, String username, String message,
      boolean notify, String color) {
    // Instantiate API
    HipChatAPI hipchatApi = HipChatAPI.INSTANCE;
    hipchatApi.setAuthToken(authToken);
    // Build POST string
    StringBuilder data = new StringBuilder();
    try {
      data.append("room_id=").append(URLEncoder.encode(room, "UTF-8"))
          .append("&from=").append(URLEncoder.encode(username, "UTF-8"))
          .append("&message=").append(URLEncoder.encode(message, "UTF-8"))
          .append("&notify=").append(notify ? "1" : "0").append("&color=")
          .append(color.toLowerCase());
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    // POST the data
    return hipchatApi.execute(PATH, data.toString(), Method.POST);
  }

  /**
   * Factory method for creating a {@link HipchatAppender} provider within the
   * plugin manager.
   *
   * @param name
   *        The name of the Appender.
   * @param filter
   *        A Filter to determine if the event should be handled by this
   *        Appender.
   * @param layout
   *        The Layout to use to format the LogEvent defaults to {@code "%m%n"}.
   * @param ignoreExceptions
   *        The default is {@code true}, causing exceptions encountered while
   *        appending events to be internally logged and then ignored. When set
   *        to {@code false} exceptions will be propagated to the caller,
   *        instead. Must be set to {@code false} when wrapping this Appender in
   *        a {@link org.apache.logging.log4j.core.appender.FailoverAppender}.
   * @param includeSource
   *        Whether the source of the log message should be included, defaults
   *        to {@code true}.
   * @param includeThreadContext
   *        Whether the contents of the
   *        {@link org.apache.logging.log4j.ThreadContext} should be included,
   *        defaults to {@code true}.
   * @param includeStackTrace
   *        Whether a full stack trace should be included, defaults to
   *        {@code true}.
   * @return a new Hipchat provider
   */
  @PluginFactory
  public static HipchatAppender createHipchatAppender(
      @PluginElement("Filter") Filter filter,
      @PluginElement("Layout") Layout<? extends Serializable> layout,
      @PluginAttribute(value = "name") String name,
      @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) Boolean ignoreExceptions,
      @PluginAttribute(value = "includeSource", defaultBoolean = true) Boolean includeSource,
      @PluginAttribute(value = "includeThreadContext", defaultBoolean = true) Boolean includeThreadContext,
      @PluginAttribute(value = "includeStackTrace", defaultBoolean = true) Boolean includeStackTrace,
      // Hipchat API specific
      @PluginAttribute(value = "authToken") String authToken,
      @PluginAttribute(value = "roomId") String roomId,
      @PluginAttribute(value = "from", defaultString = "log4j2") String from,
      @PluginAttribute(value = "includeTimeStamp", defaultBoolean = false) Boolean includeTimeStamp,
      @PluginAttribute(value = "notify", defaultBoolean = false) Boolean notify,
      @PluginAttribute(value = "color", defaultString = "yellow") String color) {
    if (name == null) {
      LOGGER.error("No name provided for ConsoleAppender");
      return null;
    }
    if (layout == null) {
      layout = PatternLayout.createDefaultLayout();
    }
    if (authToken == null) {
      LOGGER.error("A Hipchat authToken is required");
      return null;
    }
    if (roomId == null) {
      LOGGER.error("No Hipchat roomId provided");
      return null;
    }
    color = color.toLowerCase();
    if (!VALID_COLORS.contains(color)) {
      LOGGER.error("Invalid Hipchat color specified");
      return null;
    }

    return new HipchatAppender(name, layout, filter, ignoreExceptions,
        includeSource, includeThreadContext, includeStackTrace, authToken,
        roomId, from, includeTimeStamp, notify, color);
  }
}