/*******************************************************************************
 * Copyright (c) 2006, 2020 THALES GLOBAL SERVICES.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *    Thales - initial API and implementation
 *******************************************************************************/
package org.polarsys.capella.common.flexibility.wizards.renderer;

/**
 * Should be removed when renderers will have parameters defined in schema
 */
public class BrowseImageRenderer extends BrowseRenderer {

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean isImage() {
    return true;
  }

}
