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
public class HipChatAppender extends AbstractAppender {

  private final String roomId;

  private final String from;

  private final String message;

  private final Boolean notify;

  private final String color;

  private final String format;

  // String substitutions
  private enum Substitutions {
    Class("$class"),
    Level("$level"),
    Message("$message"),
    Marker("$marker"),
    Source("$source"),
    Context("$context"),
    Stack("$stack"),
    Date("$date"),
    Time("$time");

    private final String sub;

    /**
     * @param sub
     */
    private Substitutions(final String sub) {
      this.sub = sub;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
      return sub;
    }
  }

  // Instantiate API
  HipChatAPI hipchatApi = HipChatAPI.INSTANCE;

  // Hipchat API path for rooms
  private static final String PATH = "rooms/message";

  // Valid Hipchat colors
  private static final Set<String> VALID_COLORS = new HashSet<String>(
      Arrays.asList(new String[] {"yellow", "red", "green", "purple", "gray",
          "random"}));

  private static final String DEFAULT_COLOR = "yellow";

  /**
   * Constructor. Instantiates the HipChat API instance.
   */
  protected HipChatAppender(final String name,
      final Layout<? extends Serializable> layout, final Filter filter,
      final boolean ignoreExceptions, final String authToken,
      final String roomId, final String from, final String message,
      final boolean notify, final String color, final String format) {
    super(name, filter, layout, ignoreExceptions);
    hipchatApi.setAuthToken(authToken);
    this.roomId = roomId;
    this.from = from;
    this.message = message;
    this.notify = notify;
    this.color = color;
    this.format = format;
  }

  /**
   * Append method that does the work of logging each event
   * 
   * @param event
   *        The log event
   */
  @Override
  public void append(LogEvent event) {

    String fromStr = doSubstitutions(from, event);
    String messageStr = doSubstitutions(message, event);

    // Truncate from to 15 characters
    fromStr = fromStr.substring(0, Math.min(15, fromStr.length()));

    if (format.equals("html")) {
      messageStr = messageStr.replace("\n", "<br \\>");
    }

    String colorStr = pickColor(color, event.getLevel().toString());

    if (!postToHipchat(roomId, fromStr, messageStr, notify, colorStr, format)) {
      throw new AppenderLoggingException(
          "failed to write log event to Hipchat server: (from:" + fromStr
              + ") " + messageStr);
    }
  }

  /**
   * Chooses colors from a non-word delimited list. The last color seen before
   * the level is encountered is returned.
   * 
   * @param color
   *        Non-word delimited string of colors and levels
   * @param level
   *        the level to match
   * @return color matching the specified level
   */
  private String pickColor(String color, String level) {
    String pickColor = DEFAULT_COLOR;
    for (String s : color.split("\\W+")) {
      if (level.equals(s.toUpperCase())) {
        break;
      } else {
        String c = s.toLowerCase();
        if (VALID_COLORS.contains(c)) {
          pickColor = c;
        }
      }
    }
    return pickColor;
  }

  /**
   * Substitutes for various $-patterns in the api strings
   * 
   * @param s
   *        The string on which to perform substitutions
   * @param event
   *        The log event
   * @return String with patterns substituted
   */
  private String doSubstitutions(final String s, LogEvent event) {
    String sub = new String(s);
    final StackTraceElement source = event.getSource();

    // Class
    if (sub.contains(Substitutions.Class.toString())) {
      // Simple Source Class name
      String className = source != null ? source.getClassName() : "";
      String[] classArr = className.split("\\.");
      sub = sub.replace(Substitutions.Class.toString(),
          (classArr.length > 0) ? classArr[classArr.length - 1] : "");
    }

    // Level
    if (sub.contains(Substitutions.Level.toString())) {
      sub = sub
          .replace(Substitutions.Level.toString(), event.getLevel().name());
    }

    // Message
    if (sub.contains(Substitutions.Message.toString())) {
      sub = sub.replace(Substitutions.Message.toString(), event.getMessage()
          .getFormattedMessage());
    }

    // Marker
    if (sub.contains(Substitutions.Marker.toString())) {
      final Marker marker = event.getMarker();
      sub = sub.replace(Substitutions.Marker.toString(),
          marker != null ? marker.getName() : "");
    }

    // Source
    if (sub.contains(Substitutions.Source.toString())) {
      sub = sub.replace(
          Substitutions.Source.toString(),
          source != null ? String.format("%n%s.%s(%s:%d)",
              source.getClassName(), source.getMethodName(),
              source.getFileName(), source.getLineNumber()) : "");
    }

    // Thread Context
    if (sub.contains(Substitutions.Context.toString())) {
      final StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, String> entry : event.getContextMap().entrySet()) {
        sb.append("\n").append(entry.getKey()).append("=")
            .append(entry.getValue());
      }
      final List<String> contextStack = event.getContextStack().asList();
      if (contextStack != null && !contextStack.isEmpty()) {
        sb.append("\ncontextStack=").append(contextStack.toString());
      }
      sub = sub.replace(Substitutions.Context.toString(),
          sb.length() > 0 ? sb.toString() : "");
    }

    // Stack Trace
    @SuppressWarnings("all")
    final Throwable thrown = event.getThrown();
    if (sub.contains(Substitutions.Stack.toString())) {
      if (thrown != null) {
        final StringBuilder stackTraceBuilder = new StringBuilder();
        for (StackTraceElement stackTraceElement : thrown.getStackTrace()) {
          new Formatter(stackTraceBuilder).format("%nat %s.%s(%s:%d)",
              stackTraceElement.getClassName(),
              stackTraceElement.getMethodName(),
              stackTraceElement.getFileName(),
              stackTraceElement.getLineNumber());
        }
        sub = sub.replace(Substitutions.Stack.toString(), String.format(
            "%n%s: %s%s", thrown.getClass().getCanonicalName(),
            thrown.getMessage(), stackTraceBuilder.toString()));
      } else {
        sub = sub.replace(Substitutions.Stack.toString(), "");
      }
    }

    // TimeStamp
    if (sub.contains(Substitutions.Date.toString())) {
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
      Date timeStamp = new Date();
      timeStamp.setTime(event.getTimeMillis());
      sub = sub.replace(Substitutions.Date.toString(),
          dateFormat.format(timeStamp));
    }
    if (sub.contains(Substitutions.Time.toString())) {
      SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
      Date timeStamp = new Date();
      timeStamp.setTime(event.getTimeMillis());
      sub = sub.replace(Substitutions.Time.toString(),
          dateFormat.format(timeStamp));
    }

    return sub;
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
      boolean notify, String color, String format) {
    // Build POST string
    StringBuilder data = new StringBuilder();
    try {
      data.append("room_id=").append(URLEncoder.encode(room, "UTF-8"))
          .append("&from=").append(URLEncoder.encode(username, "UTF-8"))
          .append("&message=").append(URLEncoder.encode(message, "UTF-8"))
          .append("&notify=").append(notify ? "1" : "0").append("&color=")
          .append(color.toLowerCase()).append("&format=").append(format);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    // POST the data
    return hipchatApi.execute(PATH, data.toString(), Method.POST);
  }

  /**
   * Factory method for creating a {@link HipChatAppender} provider within the
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
   * @param authToken
   *        HipChat room notification token
   * @param roomId
   *        HipChat room_id parameter
   * @param from
   *        HipChat from parameter
   * @param message
   *        HipChat message parameter
   * @param notify
   *        HipChat notify parameter
   * @param color
   *        parameter
   * @param format
   *        parameter
   * @return The Appender
   */
  @PluginFactory
  public static HipChatAppender createHipchatAppender(
      @PluginElement("Filter") Filter filter,
      @PluginElement("Layout") Layout<? extends Serializable> layout,
      @PluginAttribute(value = "name") String name,
      @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) Boolean ignoreExceptions,
      // Hipchat API specific
      @PluginAttribute(value = "authToken") String authToken,
      @PluginAttribute(value = "roomId") String roomId,
      @PluginAttribute(value = "from", defaultString = "$class") String from,
      @PluginAttribute(value = "message", defaultString = "$level: $message $marker <i>$source</i> $context $stack") String message,
      @PluginAttribute(value = "notify", defaultBoolean = true) Boolean notify,
      @PluginAttribute(value = "color", defaultString = "red: FATAL, ERROR; yellow: WARN; purple") String color,
      @PluginAttribute(value = "format", defaultString = "html") String format) {
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

    return new HipChatAppender(name, layout, filter, ignoreExceptions,
        authToken, roomId, from, message, notify, color, format);
  }
}