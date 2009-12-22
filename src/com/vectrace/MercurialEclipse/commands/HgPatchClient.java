/*******************************************************************************
 * Copyright (c) 2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jerome Negre              - implementation
 *     Steeven Lee               - import/export stuff
 *     Bastian Doetsch           - additions
 *******************************************************************************/
package com.vectrace.MercurialEclipse.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.compare.patch.IFilePatch;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;

import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.utils.PatchUtils;

public class HgPatchClient extends AbstractClient {

	public static String importPatch(IProject project, File patchLocation,
			ArrayList<String> options) throws HgException {
		assert patchLocation != null && options != null;
		AbstractShellCommand command = new HgCommand("import", project, true); //$NON-NLS-1$
		command.addFiles(patchLocation.getAbsolutePath());
		command.addOptions(options.toArray(new String[options.size()]));
		return command.executeToString();
	}

	public static boolean exportPatch(HgRoot hgRoot, Set<IPath> resources,
			File patchFile, ArrayList<String> options) throws HgException {
		AbstractShellCommand command = new HgCommand("diff", hgRoot, true); //$NON-NLS-1$
		command.addFiles(resources);
		command.addOptions(options.toArray(new String[options.size()]));
		return command.executeToFile(patchFile, 0, false);
	}

	/**
	 * export diff file to clipboard
	 *
	 * @param resources
	 * @throws HgException
	 */
	public static String exportPatch(File workDir, List<IResource> resources,
			ArrayList<String> options) throws HgException {
		AbstractShellCommand command = new HgCommand(
				"diff", getWorkingDirectory(workDir), true); //$NON-NLS-1$
		command.addFiles(resources);
		command.addOptions(options.toArray(new String[options.size()]));
		return command.executeToString();
	}

	public static String getDiff(File workDir) throws HgException {
		AbstractShellCommand command = new HgCommand(
				"diff", getWorkingDirectory(workDir), true); //$NON-NLS-1$
		return command.executeToString();
	}

	public static String getDiff(File workDir, File file) throws HgException {
		AbstractShellCommand command = new HgCommand(
				"diff", getWorkingDirectory(workDir), true); //$NON-NLS-1$
		command.addOptions(file.getAbsolutePath());
		return command.executeToString();
	}

	public IFilePatch[] getFilePatchesFromDiff(File file) throws HgException {
		AbstractShellCommand command = new HgCommand(
				"diff", getWorkingDirectory(getWorkingDirectory(file)), true); //$NON-NLS-1$
		String patchString = command.executeToString();
		return PatchUtils.getFilePatches(patchString);
	}
}
