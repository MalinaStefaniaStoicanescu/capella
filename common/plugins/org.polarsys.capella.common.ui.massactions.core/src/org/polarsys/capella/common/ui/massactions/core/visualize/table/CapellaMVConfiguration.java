/*******************************************************************************
 * Copyright (c) 2019, 2020 THALES GLOBAL SERVICES.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Thales - initial API and implementation
 *******************************************************************************/
package org.polarsys.capella.common.ui.massactions.core.visualize.table;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.polarsys.capella.common.ui.preferences.IExportCSVPreferences;
import org.polarsys.kitalpha.massactions.core.config.MAExportConfiguration;
import org.polarsys.kitalpha.massactions.visualize.config.MVConfiguration;

public class CapellaMVConfiguration extends MVConfiguration {

  public CapellaMVConfiguration(NatTable natTable, IConfigRegistry configRegistry) {
    super(natTable, configRegistry);
  }

  @Override
  protected MAExportConfiguration createExportConfiguration() {
    return new MAExportConfiguration(Character.toString(IExportCSVPreferences.INSTANCE.getDelimiterCurrentValue()));
  }
}
