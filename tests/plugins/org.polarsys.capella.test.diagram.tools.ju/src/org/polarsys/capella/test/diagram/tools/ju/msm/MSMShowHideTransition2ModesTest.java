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
package org.polarsys.capella.test.diagram.tools.ju.msm;

import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.test.diagram.common.ju.context.MSMDiagram;
import org.polarsys.capella.test.diagram.tools.ju.model.EmptyProject;
import org.polarsys.capella.test.framework.context.SessionContext;
import org.polarsys.capella.test.framework.model.GenericModel;

/**
 */
public class MSMShowHideTransition2ModesTest extends EmptyProject {

	
	  // diagram elements
	protected final String _reusedMode = "reusedMode"; //$NON-NLS-1$ 
	protected final String transition = "[State Transition] to Mode 2"; //$NON-NLS-1$ 

	public void test() throws Exception {

	Session session = getSession(getRequiredTestModel());
	SessionContext context = new SessionContext(session);

	MSMDiagram diagram= MSMDiagram.createDiagram(context, EmptyProject.SA__SYSTEM__SYSTEM_STATE_MACHINE__DEFAULT_REGION);

	diagram.createMode(diagram.getDiagramId(), GenericModel.MODE_1);
	diagram.createRegion(GenericModel.MODE_1, GenericModel.REGION_1);
	diagram.createMode(GenericModel.REGION_1, GenericModel.MODE_2);
	diagram.createRegion(GenericModel.MODE_2, GenericModel.REGION_2);

	diagram.createMode(diagram.getDiagramId(), GenericModel.MODE_3);
	diagram.createRegion(GenericModel.MODE_3, GenericModel.REGION_3);
	diagram.createRegion(GenericModel.MODE_3, GenericModel.REGION_4);

	diagram.createTransition(GenericModel.MODE_2, GenericModel.MODE_3, transition);

	MSMDiagram.setUnsynchronized(diagram);

	diagram.hideTransition(diagram.getDiagramId(), transition);
	diagram.showTransition(diagram.getDiagramId(), transition);
	
	diagram.hideStateMode (diagram.getDiagramId(), GenericModel.MODE_3);
	diagram.showStateMode (diagram.getDiagramId(), GenericModel.MODE_3);
	}


}
