/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.views.console;

import org.eclipse.jface.action.Action;
import org.eclipse.team.internal.ui.Utils;

/**
 * Action that removed the CVS console from the console view. The console
 * can be re-added via the console view "Open Console" drop-down.
 *
 * @since 3.1
 */
public class ConsoleRemoveAction extends Action {

	ConsoleRemoveAction() {
		Utils.initAction(this, "ConsoleRemoveAction."); //$NON-NLS-1$
	}

	@Override
	public void run() {
		HgConsoleHolder.getInstance().closeConsole();
	}
}