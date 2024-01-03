/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2015, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.access.jetty;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.access.spi.Util;
import ch.qos.logback.access.testUtil.NotifyingListAppender;
import ch.qos.logback.core.testUtil.RandomUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JettyBasicTest {

    static RequestLogImpl REQUEST_LOG_IMPL;
    static JettyFixtureWithListAndConsoleAppenders JETTY_FIXTURE;

    private static final int TIMEOUT = 5;
    static int RANDOM_SERVER_PORT = RandomUtil.getRandomServerPort();

    @BeforeAll
    static public void startServer() throws Exception {
        REQUEST_LOG_IMPL = new RequestLogImpl();
        JETTY_FIXTURE = new JettyFixtureWithListAndConsoleAppenders(REQUEST_LOG_IMPL, RANDOM_SERVER_PORT);
        JETTY_FIXTURE.start();
    }

    @AfterAll
    static public void stopServer() throws Exception {
        if (JETTY_FIXTURE != null) {
            JETTY_FIXTURE.stop();
        }
    }

    @Test
    public void getRequest() throws Exception {
        URL url = new URL("http://localhost:" + RANDOM_SERVER_PORT + "/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);

        String result = Util.readToString(connection.getInputStream());

        assertEquals("hello world", result);

        NotifyingListAppender listAppender = (NotifyingListAppender) REQUEST_LOG_IMPL.getAppender("list");
        listAppender.list.clear();
    }

    @Test
    public void eventGoesToAppenders() throws Exception {
        long testStart = System.currentTimeMillis();
        URL url = new URL(JETTY_FIXTURE.getUrl() + "foo/bar?param1=value1");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);

        String result = Util.readToString(connection.getInputStream());

        assertEquals("hello world", result);

        NotifyingListAppender listAppender = (NotifyingListAppender) REQUEST_LOG_IMPL.getAppender("list");
        IAccessEvent event = listAppender.list.poll(TIMEOUT, TimeUnit.SECONDS);
        assertNotNull(event, "No events received");

        assertEquals("127.0.0.1", event.getRemoteHost());
        assertEquals("localhost", event.getServerName());
        assertTrue(event.getTimeStamp() >= testStart);
        assertTrue(event.getTimeStamp() <= System.currentTimeMillis());
        assertTrue(event.getElapsedTime() >= 0);
        assertEquals("/foo/bar", event.getRequestURI());
        assertEquals("GET /foo/bar?param1=value1 HTTP/1.1", event.getRequestURL());
        assertEquals("HTTP/1.1", event.getProtocol());
        assertEquals("GET", event.getMethod());
        assertEquals("-", event.getSessionID());
        assertEquals("?param1=value1", event.getQueryString());
        assertEquals("127.0.0.1", event.getRemoteAddr());
        assertTrue(event.getRequestHeaderMap().size() > 0);
        assertFalse(event.getRequestHeader("Host").isEmpty());
        assertArrayEquals(new String[] { "value1" }, event.getRequestParameter("param1"));
        assertTrue(event.getRequestParameterMap().size() == 1);
        assertEquals(11, event.getContentLength());
        assertEquals(200, event.getStatusCode());
        assertTrue(event.getResponseHeader("Server").toLowerCase(Locale.ENGLISH).startsWith("jetty"));
        assertTrue(event.getResponseHeaderMap().size() > 1);

        listAppender.list.clear();
    }

    @Test
    public void postContentConverter() throws Exception {
        URL url = new URL(JETTY_FIXTURE.getUrl());
        String msg = "test message";

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        // this line is necessary to make the stream aware of when the message is
        // over.
        connection.setFixedLengthStreamingMode(msg.getBytes().length);
        ((HttpURLConnection) connection).setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Content-Type", "text/plain");

        PrintWriter output = new PrintWriter(new OutputStreamWriter(connection.getOutputStream()));
        output.print(msg);
        output.flush();
        output.close();

        // StatusPrinter.print(requestLogImpl.getStatusManager());

        NotifyingListAppender listAppender = (NotifyingListAppender) REQUEST_LOG_IMPL.getAppender("list");

        IAccessEvent event = listAppender.list.poll(TIMEOUT, TimeUnit.SECONDS);
        assertNotNull(event, "No events received");

        // we should test the contents of the requests
        // assertEquals(msg, event.getRequestContent());
    }
}
