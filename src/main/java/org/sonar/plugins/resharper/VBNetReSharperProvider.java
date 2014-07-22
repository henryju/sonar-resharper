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
import org.sonar.api.rules.XMLRuleParser;

import java.util.List;

public class VBNetReSharperProvider {

  private static final ReSharperConfiguration RESHARPER_CONF = new ReSharperConfiguration("vbnet", "resharper-vbnet");

  private VBNetReSharperProvider() {
  }

  public static List extensions() {
    return ImmutableList.of(
      VBNetReSharperRuleRepository.class,
      VBNetReSharperSensor.class);
  }

  public static class VBNetReSharperRuleRepository extends ReSharperRuleRepository {

    public VBNetReSharperRuleRepository(XMLRuleParser xmlRuleParser) {
      super(RESHARPER_CONF, xmlRuleParser);
    }

  }

  public static class VBNetReSharperSensor extends ReSharperSensor {

    public VBNetReSharperSensor(ReSharperExecutor executor, ReSharperReportParser parser, ReSharperDotSettingsWriter writer) {
      super(RESHARPER_CONF, executor, parser, writer);
    }

  }

}
