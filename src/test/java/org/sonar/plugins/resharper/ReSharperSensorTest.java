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
package org.sonar.plugins.resharper;

import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.IssueBuilder;
import org.sonar.api.batch.sensor.issue.internal.DefaultIssueBuilder;
import org.sonar.api.config.Settings;
import org.sonar.api.rule.RuleKey;

import javax.annotation.Nullable;

import java.io.File;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ReSharperSensorTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private ReSharperDotSettingsWriter writer = mock(ReSharperDotSettingsWriter.class);
  private ReSharperExecutor executor = mock(ReSharperExecutor.class);
  private ReSharperReportParser parser = mock(ReSharperReportParser.class);

  @Test
  public void describe() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();
    new ReSharperSensor(new ReSharperConfiguration("foo", "bar"), executor, parser, writer).describe(descriptor);
    assertThat(descriptor.name()).isEqualTo("ReSharper");
    assertThat(descriptor.languages()).containsOnly("foo");
    assertThat(descriptor.types()).containsOnly(InputFile.Type.MAIN);
    assertThat(descriptor.ruleRepositories()).containsOnly("bar");
  }

  @Test
  public void analyze() throws Exception {
    ReSharperSensor sensor = new ReSharperSensor(new ReSharperConfiguration("foo", "foo-resharper"), executor, parser, writer);

    ActiveRules activeRules = mockActiveRules("foo-resharper", "AccessToDisposedClosure", "AccessToForEachVariableInClosure");

    File workingDir = new File("target/ReSharperSensorTest/working-dir").getAbsoluteFile();
    DefaultFileSystem fileSystem = new DefaultFileSystem();
    fileSystem.setWorkDir(workingDir);

    InputFile inputFileClass4 = mockInputFile("foo", "Class4.cs");
    InputFile inputFileClass5 = mockInputFile("foo", "Class5.cs");

    fileSystem.add(inputFileClass4);
    fileSystem.add(inputFileClass5);
    fileSystem.add(mockInputFile("bar", "Class6.cs"));
    fileSystem.add(mockInputFile("foo", "Class7.cs"));

    SensorContext context = mockSensorContext(mockSettings("MyLibrary", "CSharpPlayground.sln", "inspectcode.exe"));
    when(context.activeRules()).thenReturn(activeRules);
    when(context.fileSystem()).thenReturn(fileSystem);
    // TODO Cannot do when(context.issueBuilder()).thenReturn(new DefaultIssueBuilder());
    // will throw IllegalStateException
    // "onFile or onProject can be called only once", because it will attempt to reuse the same builder over and over again
    when(context.issueBuilder()).thenAnswer(new Answer<IssueBuilder>() {

      @Override
      public IssueBuilder answer(InvocationOnMock invocation) throws Throwable {
        return new DefaultIssueBuilder();
      }

    });

    when(parser.parse(new File(workingDir, "resharper-report.xml"))).thenReturn(
      ImmutableList.of(
        new ReSharperIssue(100, "AccessToDisposedClosure", null, 1, "Dummy message"),
        new ReSharperIssue(200, "AccessToDisposedClosure", "Class2.cs", null, "Dummy message"),
        new ReSharperIssue(400, "AccessToDisposedClosure", "Class3.cs", 3, "First message"),
        new ReSharperIssue(500, "AccessToDisposedClosure", "Class4.cs", 4, "Second message"),
        new ReSharperIssue(600, "AccessToForEachVariableInClosure", "Class5.cs", 5, "Third message"),
        new ReSharperIssue(700, "AccessToDisposedClosure", "Class6.cs", 6, "Fourth message"),
        new ReSharperIssue(800, "InactiveRule", "Class7.cs", 7, "Fifth message")));

    sensor.execute(context);

    verify(writer).write(
      ImmutableList.of("AccessToDisposedClosure", "AccessToForEachVariableInClosure"),
      new File(workingDir, "resharper-sonarqube.DotSettings"));
    verify(executor).execute(
      "inspectcode.exe", "MyLibrary", "CSharpPlayground.sln",
      new File(workingDir, "resharper-sonarqube.DotSettings"),
      new File(workingDir, "resharper-report.xml"), 10);

    ArgumentCaptor<Issue> issues = ArgumentCaptor.forClass(Issue.class);

    verify(context, Mockito.times(2)).addIssue(issues.capture());

    Issue issue1 = issues.getAllValues().get(0);
    assertThat(issue1.ruleKey().rule()).isEqualTo("AccessToDisposedClosure");
    assertThat(issue1.inputPath()).isSameAs(inputFileClass4);
    assertThat(issue1.line()).isEqualTo(4);
    assertThat(issue1.message()).isEqualTo("Second message");

    Issue issue2 = issues.getAllValues().get(1);
    assertThat(issue2.ruleKey().rule()).isEqualTo("AccessToForEachVariableInClosure");
    assertThat(issue2.inputPath()).isSameAs(inputFileClass5);
    assertThat(issue2.line()).isEqualTo(5);
    assertThat(issue2.message()).isEqualTo("Third message");
  }

  @Test
  public void check_project_name_property() {
    thrown.expectMessage(ReSharperPlugin.PROJECT_NAME_PROPERTY_KEY);
    thrown.expect(IllegalStateException.class);

    new ReSharperSensor(new ReSharperConfiguration("", ""), executor, parser, writer).execute(mockSensorContext(mockSettings(null, "dummy.sln", null)));
  }

  @Test
  public void check_solution_file_property() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(ReSharperPlugin.SOLUTION_FILE_PROPERTY_KEY);

    new ReSharperSensor(new ReSharperConfiguration("", ""), executor, parser, writer).execute(mockSensorContext(mockSettings("Dummy Project", null, null)));
  }

  private static ActiveRules mockActiveRules(String repository, String... activeRuleKeys) {
    ActiveRulesBuilder builder = new ActiveRulesBuilder();
    for (String activeRuleKey : activeRuleKeys) {
      builder.create(RuleKey.of(repository, activeRuleKey)).activate();
    }
    return builder.build();
  }

  private static InputFile mockInputFile(String language, String absolutePath) {
    // TODO This whole "relative path" thing is terribly annoying, it's used for equals() - so simply set it to the absolute path
    return new DefaultInputFile(absolutePath).setLanguage(language).setAbsolutePath(absolutePath);
  }

  private static SensorContext mockSensorContext(Settings settings) {
    SensorContext context = mock(SensorContext.class);
    when(context.settings()).thenReturn(settings);
    return context;
  }

  private static Settings mockSettings(@Nullable String projectName, @Nullable String solutionFile, @Nullable String inspectcodePath) {
    Settings settings = new Settings();

    if (projectName != null) {
      settings.setProperty(ReSharperPlugin.PROJECT_NAME_PROPERTY_KEY, projectName);
    }
    if (solutionFile != null) {
      settings.setProperty(ReSharperPlugin.SOLUTION_FILE_PROPERTY_KEY, solutionFile);
    }
    if (inspectcodePath != null) {
      settings.setProperty(ReSharperPlugin.INSPECTCODE_PATH_PROPERTY_KEY, inspectcodePath);
    }

    settings.setProperty(ReSharperPlugin.TIMEOUT_MINUTES_PROPERTY_KEY, "10");

    return settings;
  }

}
