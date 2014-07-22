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

import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import org.sonar.api.resources.AbstractLanguage;
import org.sonar.plugins.resharper.ReSharperExecutor;
import org.sonar.plugins.resharper.ReSharperPlugin;

import java.util.List;

public class MockedReSharperPlugin extends ReSharperPlugin {

  private ReSharperExecutor executor;

  public MockedReSharperPlugin(ReSharperExecutor executor) {
    this.executor = executor;
  }

  @Override
  public List getExtensions() {
    List list = Lists.newArrayList(Collections2.filter(super.getExtensions(), Predicates.not(Predicates.equalTo(ReSharperExecutor.class))));
    list.add(new AbstractLanguage("cs") {

      @Override
      public String[] getFileSuffixes() {
        return new String[] {"cs"};
      }
    });
    list.add(executor);
    return list;
  }

}
