/*
 * Copyright (c) Bosch.IO GmbH 2020.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.antenna.frontend.compliancetool.main;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * A test class to check special functionality of the Compliance Tool CLI
 * related to logging and error handling. As these tests require a special
 * setup, they cannot be contained in the regular test class.
 */
public class AntennaComplianceToolDebugLogTest {
    /**
     * Name of the permission to exit the current VM.
     */
    private static final String PERM_EXIT_VM = "exitVM";

    /**
     * Name of the test tool configuration file that gets executed.
     */
    private static final String PROPERTIES = "/compliancetool-exporter.properties";

    /**
     * The name of the appender to capture log output.
     */
    private static final String WRITER_APPENDER_NAME = "logCaptureAppender";

    /**
     * Stores the original SM, so that it can be restored later.
     */
    private static SecurityManager standardSecurityManager;

    /**
     * The path to the test properties file.
     */
    private static String propertiesFilePath;

    /**
     * String representing a mode of the compliance tool.
     */
    private static final String COMPLIANCE_TOOL_MODE = AntennaComplianceToolOptions.SWITCH_EXPORTER_SHORT;

    /**
     * String always contained when a debug message is executed.
     */
    private static final String DEBUG_MESSAGE = "has started";

    /**
     * String always contained when a info message is executed.
     */
    private static final String INFO_MESSAGE = "Starting Compliance Tool with mode ";


    /**
     * Stores the original output stream. Some tests change this stream to
     * capture the output generated by the CLI. In those cases, the original
     * stream has to be restored after the test.
     */
    private PrintStream originalSystemOut;

    @BeforeClass
    public static void setUpOnce() throws URISyntaxException {
        standardSecurityManager = System.getSecurityManager();
        System.setSecurityManager(createSecurityManagerThatPreventsSystemExit());

        Path configPath = Paths.get(AntennaComplianceToolDebugLogTest.class.getResource(PROPERTIES).toURI());
        propertiesFilePath = configPath.toAbsolutePath().toString();
    }

    @AfterClass
    public static void tearDownOnce() {
        System.setSecurityManager(standardSecurityManager);
    }

    @Before
    public void setUp() {
        originalSystemOut = System.out;
    }

    @After
    public void tearDown() {
        System.setOut(originalSystemOut);
        Configurator.reconfigure();  // set logging config back to defaults
    }

    /**
     * Returns a {@code SecurityManager} that prohibits exiting the VM. The
     * Compliance Tool CLI application calls {@code System.exit()} under
     * certain circumstances, e.g. if invalid arguments are passed in.
     * In order to check such constellations, it has to be prevented that
     * the VM is actually stopped.
     *
     * @return a tweaked {@code SecurityManager}
     */
    private static SecurityManager createSecurityManagerThatPreventsSystemExit() {
        return new SecurityManager() {
            @Override
            public void checkPermission(Permission perm) {
                if (perm.getName().startsWith(PERM_EXIT_VM)) {
                    throw new SecurityException("Suppressed System.exit().");
                }
            }

            @Override
            public void checkPermission(Permission perm, Object context) {
                checkPermission(perm);
            }
        };
    }

    /**
     * Supports changing the logging configuration by invoking the action
     * specified on all exiting logger configurations.
     *
     * @param action the action to configure a logger
     */
    private static void configureLoggers(Consumer<LoggerConfig> action) {
        LoggerContext context = LoggerContext.getContext(false);
        Configuration configuration = context.getConfiguration();

        configuration.getLoggers().values().forEach(action);
        action.accept(configuration.getRootLogger());  // need to handle root explicitly
    }

    /**
     * Installs a special appender to cover all the log output that is
     * generated. The {@code StringWriter} that is returned can be used to
     * obtain the output that was written.
     *
     * @return a {@code StringWriter} to obtain the log output
     */
    private static StringWriter captureLogOutput() {
        StringWriter writer = new StringWriter();
        WriterAppender appender = WriterAppender.createAppender(PatternLayout.createDefaultLayout(), null,
                writer, WRITER_APPENDER_NAME, false, true);
        appender.start();

        configureLoggers(logConfig -> logConfig.addAppender(appender, null, null));
        return writer;
    }

    /**
     * Executes a test compliance tool run with the given command line
     * arguments and returns a string with the logging output that has
     * been captured. The run is expected to fail due to a missing
     * running SW360 instance.
     *
     * @param args the command line arguments
     * @return a string with the log output that was captured
     */
    private static String runComplianceToolAndCaptureLogOutputWithExpectedFailure(String... args) {
        StringWriter logWriter = captureLogOutput();

        try {
            AntennaComplianceTool.main(args);
            fail("Compliance tool run did not fail.");
        } catch (SecurityException e) {
            //expected
        }

        logWriter.flush();
        return logWriter.toString();
    }

    @Test
    public void testLogLevelIsInfoByDefault() {
        String output = runComplianceToolAndCaptureLogOutputWithExpectedFailure(propertiesFilePath, COMPLIANCE_TOOL_MODE);

        assertThat(output).contains(INFO_MESSAGE);
        assertThat(output).doesNotContain(DEBUG_MESSAGE);
    }

    @Test
    public void testLogLevelCanBeSwitchedToDebug() {
        String output = runComplianceToolAndCaptureLogOutputWithExpectedFailure(AntennaComplianceToolOptions.SWITCH_DEBUG_SHORT, propertiesFilePath, COMPLIANCE_TOOL_MODE);

        assertThat(output).contains(INFO_MESSAGE);
        assertThat(output).contains(DEBUG_MESSAGE);
    }

    @Test
    public void testCommandLineIsValidated() {
        String output = runComplianceToolAndCaptureLogOutputWithExpectedFailure(propertiesFilePath, "--unsupported-argument");

        assertThat(output).contains(AntennaComplianceToolOptions.helpMessage());
    }

    @Test
    public void testNonExistingConfigFileIsHandled() {
        Path nonExistingPath = Paths.get("non", "existing", "config.xml");
        String output = runComplianceToolAndCaptureLogOutputWithExpectedFailure(nonExistingPath.toString(), COMPLIANCE_TOOL_MODE);

        assertThat(output).contains(Arrays.asList("Cannot find ", nonExistingPath.toString()));
    }
}
