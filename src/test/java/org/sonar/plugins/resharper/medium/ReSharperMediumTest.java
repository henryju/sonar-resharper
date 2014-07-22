/*
 * SonarQube ReSharper Plugin
 * Copyright (C) 2014 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.resharper.medium;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.batch.mediumtest.BatchMediumTester;
import org.sonar.batch.mediumtest.BatchMediumTester.TaskResult;
import org.sonar.batch.protocol.input.ActiveRule;
import org.sonar.plugins.resharper.ReSharperExecutor;

import java.io.File;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.fest.assertions.Assertions.assertThat;

public class ReSharperMediumTest {

  @org.junit.Rule
  public TemporaryFolder temp = new TemporaryFolder();

  public BatchMediumTester tester = BatchMediumTester.builder()
    .registerPlugin("resharper", mockedResharper())
    .addDefaultQProfile("cs", "Sonar Way")
    .activateRule(new ActiveRule("resharper-cs", "JoinDeclarationAndInitializer", "MAJOR", null, "cs"))
    .activateRule(new ActiveRule("resharper-cs", "RedundantUsingDirective", "MAJOR", null, "cs"))
    .bootstrapProperties(ImmutableMap.of("sonar.analysis.mode", "sensor"))
    .build();

  private MockedReSharperPlugin mockedResharper() {
    return new MockedReSharperPlugin(mockedResharperExecutor());
  }

  private ReSharperExecutor mockedResharperExecutor() {
    return new ReSharperExecutor() {
      public void execute(String executable, String project, String solutionFile, File rulesetFile, File reportFile, int timeout) {
        try {
          FileUtils.copyFile(new File(ReSharperMediumTest.class.getResource("/csharp-sample/valid.xml").toURI()), reportFile);
          String content = FileUtils.readFileToString(reportFile);
          String baseDir = FilenameUtils.normalize(baseDir().getAbsolutePath(), false);
          content = content.replaceAll(Pattern.quote("${basedir}"), Matcher.quoteReplacement(baseDir));
          FileUtils.write(reportFile, content);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      };
    };
  }

  @Before
  public void prepare() {
    tester.start();
  }

  @After
  public void stop() {
    tester.stop();
  }

  @Test
  public void runResharperSensor() throws Exception {
    File projectDir = baseDir();

    TaskResult result = tester
      .newScanTask(new File(projectDir, "sonar-project.properties"))
      .property("sonar.resharper.projectName", "MyLibrary")
      .property("sonar.resharper.solutionFile", "Example.sln")
      .start();

    assertThat(result.issues()).hasSize(2);
  }

  private File baseDir() throws URISyntaxException {
    return new File(ReSharperMediumTest.class.getResource("/csharp-sample").toURI());
  }
}
