Log4j2 Appender for HipChat
============================

This appender forwards log events to a HipChat room.

## Appender Configuration

This appender uses [HipChat's room notification API](https://www.hipchat.com/docs/apiv2/method/send_room_notification) for message delivery.

To use this appender you first need to create a [HipChat authentication token](https://www.hipchat.com/docs/api/auth) for notification.

You need to configure, at a minimum, in your `log4j2.xml` or equivalent:
   1. In `<Configuration>`, tell Log4j2 where to find this plugin
   2. Specify a HipChat roomId
   3. Specify your notification authToken 

```xml
<Configuration packages="com.github.log4j2hipchat">
  <Appenders>
    <HipChat name="HipChatAppender" 
      authToken="yourauthtoken" 
      roomId="Logging"/>
  </Appenders>
  <Loggers>
    <!-- which package(s) to log -->
    <Logger name="com.github.log4j2hipchat" level="info"/>
    <Root level="trace">
      <AppenderRef ref="HipChatAppender" level="error"/>
    </Root>
  </Loggers>
</Configuration>
```

This appender uses synchronous delivery of log messages. 
You may wish to wrap it into Log4j2's [AsyncAppender](http://logging.apache.org/log4j/2.x/manual/appenders.html#AsyncAppender). 
You may also wish to use a filter, to only log ERROR messages to HipChat, as HipChat is not intended to be used for high volume logging.
HipChat recommends [Exceptional](http://exceptional.io/) or [Papertrail](http://papertrailapp.com/) if you need higher volume than this appender will support.

In addition to the above minimum configuration, you may specify which components of the log event notification to include in either the `from` field (the username the message is posted from) and the `message` field (the contents of the notification). You may also choose whether to `notify` HipChat room members of the post, which `color` to make the post, and which `format` to post in. Note the `from` field will be truncated to 15 characters.  See the [HipChat API](https://www.hipchat.com/docs/api/method/rooms/message) for more information.

The following strings will be substituted as indicated:

String  | Substitution
------- | -------------
$class | The class name in which the log event originated
$level | The [Level](http://logging.apache.org/log4j/2.0/log4j-api/apidocs/org/apache/logging/log4j/Level.html) of the log event
$message | The contents of the log message
$marker | The [Marker](http://logging.apache.org/log4j/2.0/manual/markers.html), if any
$source | The class/method and filename/line number of the event 
$context | The [NDC](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/NDC.html) thread context, if any
$stack | A stack trace, if propagated to the log event
$date | Date of the log event as YYYY-MM-DD
$time | Time of the log event as HH:mm:ss
   
The following optional fields may be added to the `log4j2.xml` configuration. Defaults are shown:
* `from="$class"`
* `message="$level: $message $marker <i>$source</i> $context $stack"`
* `notify="true"`
* `format="html"` 
* `color="red: FATAL, ERROR; yellow: WARN; purple"` 
   
 Format options are "text" or "html" and control how HipChat parses @mentions, links, etc. 
 Color options are "red", "yellow", "green", "purple", "gray", and "random". 
 The non-word-delimited list will be iterated; the last color seen when the log level is encountered will be used.
 The default prints FATAL and ERROR messages in red, WARN messages in yellow, and all other messages in purple.

## Dependencies

In addition to log4j2's core and api jars, this code depends on:
   - [Apache Commons IO](http://commons.apache.org/proper/commons-io/)
   - [CollabSoft's HipChatAPI](https://bitbucket.org/collabsoft/hipchatapi/) 

## Acknowledgments

* Source code initially derived from [Graylog2's log4j2 Gelf Appender](https://github.com/Graylog2/log4j2-gelf).
* Documentation initially derived from [AnjLab's logbaack HipChat Appender](https://github.com/anjlab/logback-hipchat-appender)

## License

Copyright 2014 Daniel Widdis

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
