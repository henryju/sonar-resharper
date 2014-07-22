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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.Settings;
import org.sonar.api.rule.RuleKey;

import java.io.File;
import java.util.List;

public class ReSharperSensor implements Sensor {

  private static final Logger LOG = LoggerFactory.getLogger(ReSharperSensor.class);

  private final ReSharperConfiguration reSharperConf;

  public ReSharperSensor(ReSharperConfiguration reSharperConf) {
    this.reSharperConf = reSharperConf;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .name("ReSharper")
      .workOnLanguages(reSharperConf.languageKey())
      .workOnFileTypes(InputFile.Type.MAIN)
      // TODO Lost ability to log messages to inform that ReSharper rules should be enabled
      .createIssuesForRuleRepositories(reSharperConf.repositoryKey());
  }

  @Override
  public void execute(SensorContext context) {
    execute(context, new ReSharperDotSettingsWriter(), new ReSharperReportParser(), new ReSharperExecutor());
  }

  @VisibleForTesting
  void execute(SensorContext context, ReSharperDotSettingsWriter writer, ReSharperReportParser parser, ReSharperExecutor executor) {
    Settings settings = context.settings();

    checkProperties(settings);

    File rulesetFile = new File(context.fileSystem().workDir(), "resharper-sonarqube.DotSettings");
    writer.write(enabledRuleKeys(context.activeRules()), rulesetFile);

    File reportFile = new File(context.fileSystem().workDir(), "resharper-report.xml");

    executor.execute(
      settings.getString(ReSharperPlugin.INSPECTCODE_PATH_PROPERTY_KEY), settings.getString(ReSharperPlugin.PROJECT_NAME_PROPERTY_KEY),
      settings.getString(ReSharperPlugin.SOLUTION_FILE_PROPERTY_KEY), rulesetFile, reportFile, settings.getInt(ReSharperPlugin.TIMEOUT_MINUTES_PROPERTY_KEY));

    FileSystem fs = context.fileSystem();
    new File(settings.getString(ReSharperPlugin.SOLUTION_FILE_PROPERTY_KEY));
    for (ReSharperIssue issue : parser.parse(reportFile)) {
      if (!hasFileAndLine(issue)) {
        logSkippedIssue(issue, "which has no associated file.");
        continue;
      }

      // TODO FileSystem.files() is found before FileSystem.inputFile()
      InputFile sonarFile = fs.inputFile(fs.predicates().hasAbsolutePath(issue.filePath()));
      if (sonarFile == null) {
        logSkippedIssueOutsideOfSonarQube(issue);
      } else if (reSharperConf.languageKey().equals(sonarFile.language())) {
        if (!enabledRuleKeys(context.activeRules()).contains(issue.ruleKey())) {
          logSkippedIssue(issue, "because the rule \"" + issue.ruleKey() + "\" is either missing or inactive in the quality profile.");
        } else {
          context.addIssue(context.issueBuilder()
            .ruleKey(RuleKey.of(reSharperConf.repositoryKey(), issue.ruleKey()))
            .onFile(sonarFile)
            .atLine(issue.line())
            .message(issue.message())
            .build());
        }
      }
    }
  }

  private static boolean hasFileAndLine(ReSharperIssue issue) {
    return issue.filePath() != null && issue.line() != null;
  }

  private static void logSkippedIssueOutsideOfSonarQube(ReSharperIssue issue) {
    logSkippedIssue(issue, "whose file \"" + issue.filePath() + "\" is not in SonarQube.");
  }

  private static void logSkippedIssue(ReSharperIssue issue, String reason) {
    LOG.info("Skipping the ReSharper issue at line " + issue.reportLine() + " " + reason);
  }

  private List<String> enabledRuleKeys(ActiveRules activeRules) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (ActiveRule activeRule : activeRules.findByRepository(reSharperConf.repositoryKey())) {
      // TODO WTF? ActiveRule.ruleKey() should be a string, and perhaps there should be a method ActiveRule.rule()
      builder.add(activeRule.ruleKey().rule());
    }
    return builder.build();
  }

  public void checkProperties(Settings settings) {
    checkProperty(settings, ReSharperPlugin.PROJECT_NAME_PROPERTY_KEY);
    checkProperty(settings, ReSharperPlugin.SOLUTION_FILE_PROPERTY_KEY);
  }

  private static void checkProperty(Settings settings, String property) {
    if (!settings.hasKey(property)) {
      throw new IllegalStateException("The property \"" + property + "\" must be set.");
    }
  }

}
