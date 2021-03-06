/*******************************************************************************
 * Copyright (c) 2005-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Stefan	implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.commands;


/**
 * @author Stefan
 *
 */
public interface IConsole {

	void commandInvoked(String command);

	void commandCompleted(int exitCode, long timeInMillis, String message, Throwable error);

	void printError(String message, Throwable root);

	void printMessage(String message, Throwable root);

}
