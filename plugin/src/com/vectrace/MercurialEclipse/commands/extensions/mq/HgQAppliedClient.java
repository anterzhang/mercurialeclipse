/*******************************************************************************
 * Copyright (c) 2005-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * bastian	implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.commands.extensions.mq;

import java.util.List;

import com.aragost.javahg.commands.ExecutionException;
import com.aragost.javahg.ext.mq.Patch;
import com.aragost.javahg.ext.mq.flags.QAppliedCommandFlags;
import com.aragost.javahg.ext.mq.flags.QUnappliedCommandFlags;
import com.vectrace.MercurialEclipse.commands.AbstractClient;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.HgRoot;

/**
 * @author bastian
 *
 */
public class HgQAppliedClient extends AbstractClient {
	public static List<Patch> getAppliedPatches(HgRoot root) throws HgException {
		try {
			return QAppliedCommandFlags.on(root.getRepository()).execute();
		} catch (ExecutionException ex) {
			throw new HgException(ex.getLocalizedMessage(), ex);
		}
	}

	public static List<Patch> getUnappliedPatches(HgRoot root) throws HgException{
		try {
			return QUnappliedCommandFlags.on(root.getRepository()).execute();
		} catch (ExecutionException ex) {
			throw new HgException(ex.getLocalizedMessage(), ex);
		}
	}

}
