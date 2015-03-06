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

import io.evanwong.oss.hipchat.v2.HipChatClient;
import io.evanwong.oss.hipchat.v2.rooms.MessageColor;
import io.evanwong.oss.hipchat.v2.rooms.MessageFormat;
import io.evanwong.oss.hipchat.v2.rooms.SendRoomNotificationRequestBuilder;
import org.apache.logging.log4j.Level;
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

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

@Plugin(name = "Hipchat", category = "Core", elementType = "appender", printObject = true)
public class HipChatAppender extends AbstractAppender {

    private static final long serialVersionUID = 1724003660841233595L;

    private final String roomId;

    private final String from;

    private final String messageTemplate;

    private final Boolean notify;

    private final MessageFormat format;

    private final int rate;

    private final double per;

    // messages allowed
    private double allowance = 0d;

    // last log call
    private long lastLog = 0l;

    private final Map<Level, MessageColor> levelMessageColorMap;

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

    // HipChat Client
    HipChatClient hipChatClient;

    private static final MessageColor DEFAULT_COLOR = MessageColor.YELLOW;

    /**
     * Constructor. Instantiates the HipChat API instance.
     */
    protected HipChatAppender(final String name,
                              final Layout<? extends Serializable> layout, final Filter filter,
                              final boolean ignoreExceptions, final String authToken,
                              final String roomId, final String from, final String messageTemplate,
                              final boolean notify, final Map<Level, MessageColor> messageColorMap, final String format,
                              final int rate, final double per) {
        super(name, filter, layout, ignoreExceptions);
        hipChatClient = new HipChatClient(authToken);
        this.roomId = roomId;
        this.from = from;
        this.messageTemplate = messageTemplate;
        this.notify = notify;
        this.levelMessageColorMap = messageColorMap;
        if ("html".equals(format.toLowerCase())) {
            this.format = MessageFormat.HTML;
        } else {
            this.format = MessageFormat.TEXT;
        }
        this.rate = rate;
        this.per = per;
    }

    /**
     * Append method: checks rate limit and then appends
     *
     * @param event The log event
     */
    @Override
    public void append(LogEvent event) {
        // Check rate limiter
        long currentLog = System.currentTimeMillis();
        double elapsed = (currentLog - lastLog) / 1000d;
        lastLog = currentLog;
        allowance = Math.min(rate, allowance + elapsed * (rate / per));
        // Post or silently ignore
        if (allowance >= 1d) {
            appendEvent(event);
            allowance -= 1d;
        }
    }

    /**
     * Append method that does the work of logging each event
     *
     * @param event The log event
     */
    public void appendEvent(LogEvent event) {

        String fromStr = doSubstitutions(from, event);
        // Truncate from to 15 characters
        fromStr = fromStr.substring(0, Math.min(15, fromStr.length()));

        String messageStr = doSubstitutions(messageTemplate, event);
        // Truncate message to 10K characters
        messageStr = messageStr.substring(0, Math.min(10000, messageStr.length()));

        if (format == MessageFormat.HTML) {
            messageStr = messageStr.replace("\n", "<br \\>");
        }

        MessageColor messageColor = pickMessageColor(event.getLevel());

        try {
            postToHipChat(messageStr, messageColor);
        } catch (Exception e) {
            throw new AppenderLoggingException("failed to write log event to Hipchat server: " + messageStr);
        }
    }

    /**
     * Chooses colors from a non-word delimited list. The last levelMessageColorMap seen before
     * the level is encountered is returned. The format of the string is 'red: FATAL, ERROR; yellow: WARN; purple'
     *
     * @param level the level to match
     * @return levelMessageColorMap matching the specified level
     */
    private MessageColor pickMessageColor(Level level) {
        MessageColor pickColor = levelMessageColorMap.get(level);

        return pickColor != null ? pickColor : DEFAULT_COLOR;
    }

    /**
     * Substitutes for various $-patterns in the api strings
     *
     * @param s     The string on which to perform substitutions
     * @param event The log event
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
     * Posts to Hipchat. Fails silently.
     *
     * @param message The message to send
     * @param color   What levelMessageColorMap: options "yellow", "red", "green", "purple", "gray", or
     *                "random"; default yellow
     */
    private void postToHipChat(String message, MessageColor color) {

        SendRoomNotificationRequestBuilder builder = hipChatClient.prepareSendRoomNotificationRequestBuilder(roomId, message);
        builder.setColor(color).setNotify(notify).setMessageFormat(MessageFormat.HTML).build().execute();

    }

    /**
     * Build a map of Levels to HipChat Colors
     *
     * @param color The format of the string is 'red: FATAL, ERROR; yellow: WARN; purple'
     * @return A map of Levels to MessageColors
     */
    private static Map<Level, MessageColor> parseLevelToColors(String color) {
        Map<Level, MessageColor> messageColorMap = new HashMap<Level, MessageColor>();
        for (String current : color.split(";")) {
            if (!current.contains(":")) {
                throw new IllegalArgumentException("Color segment does not contain [levelMessageColorMap: LEVEL1, LEVEL2]");
            }
            String[] currentSplit = current.trim().split(":");
            // Figure out, if this is a levelMessageColorMap. If not this will also throw an IllegalArgumentException
            MessageColor currentColor = MessageColor.valueOf(currentSplit[0].trim().toUpperCase());

            for (String levelString : currentSplit[1].split(",")) {
                // Figure out, if this is a level. If not this will also throw an IllegalArgumentException
                Level level = Level.valueOf(levelString.trim().toUpperCase());
                messageColorMap.put(level, currentColor);
            }
        }
        return messageColorMap;
    }


    /**
     * Factory method for creating a {@link HipChatAppender} provider within the
     * plugin manager.
     *
     * @param name             The name of the Appender.
     * @param filter           A Filter to determine if the event should be handled by this
     *                         Appender.
     * @param layout           The Layout to use to format the LogEvent defaults to {@code "%m%n"}.
     * @param ignoreExceptions The default is {@code true}, causing exceptions encountered while
     *                         appending events to be internally logged and then ignored. When set
     *                         to {@code false} exceptions will be propagated to the caller,
     *                         instead. Must be set to {@code false} when wrapping this Appender in
     *                         a {@link org.apache.logging.log4j.core.appender.FailoverAppender}.
     * @param authToken        HipChat room notification token
     * @param roomId           HipChat room_id parameter
     * @param from             HipChat from parameter
     * @param message          HipChat message parameter
     * @param notify           HipChat notify parameter
     * @param color            parameter
     * @param format           parameter
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
            @PluginAttribute(value = "levelMessageColorMap", defaultString = "red: FATAL, ERROR; yellow: WARN; purple") String color,
            @PluginAttribute(value = "format", defaultString = "html") String format,
            // Rate limiter: 'rate' messages per second
            @PluginAttribute(value = "rate", defaultInt = Integer.MAX_VALUE) int rate,
            @PluginAttribute(value = "per", defaultDouble = 1d) double per)

    {
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
        if (per <= 0d) {
            LOGGER.error("Per must be positive number of seconds");
            return null;
        }

        Map<Level, MessageColor> levelToColors = parseLevelToColors(color);

        return new HipChatAppender(name, layout, filter, ignoreExceptions,
                authToken, roomId, from, message, notify, levelToColors, format, rate, per);
    }

}