/*******************************************************************************
 * Copyright (c) 2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jerome Negre              - implementation
 *     Bastian Doetsch           - added authentication to push
 *     Andrei Loskutov (Intland) - bug fixes
 *******************************************************************************/
package com.vectrace.MercurialEclipse.commands;

import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.menu.UpdateJob;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.model.IHgRepositoryLocation;
import com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants;
import com.vectrace.MercurialEclipse.team.cache.RefreshRootJob;
import com.vectrace.MercurialEclipse.team.cache.RefreshWorkspaceStatusJob;

public class HgPushPullClient extends AbstractClient {

	public static String push(HgRoot hgRoot, IHgRepositoryLocation repo,
			boolean force, String revision, int timeout) throws HgException {
		AbstractShellCommand command = new HgCommand("push", hgRoot, true); //$NON-NLS-1$
		command.setExecutionRule(new AbstractShellCommand.ExclusiveExecutionRule(hgRoot));
		command.setUsePreferenceTimeout(MercurialPreferenceConstants.PUSH_TIMEOUT);

		if (force) {
			command.addOptions("-f"); //$NON-NLS-1$
		}

		if (revision != null && revision.length() > 0) {
			command.addOptions("-r", revision.trim()); //$NON-NLS-1$
		}

		addRepoToHgCommand(repo, command);
		return new String(command.executeToBytes(timeout));
	}

	public static String pull(HgRoot hgRoot, ChangeSet changeset,
			IHgRepositoryLocation repo, boolean update, boolean rebase,
			boolean force, boolean timeout) throws HgException {

		HgCommand command = new HgCommand("pull", hgRoot, true); //$NON-NLS-1$
		command.setExecutionRule(new AbstractShellCommand.ExclusiveExecutionRule(hgRoot));

		if (update) {
			command.addOptions("--update"); //$NON-NLS-1$
		} else if (rebase) {
			command.addOptions("--config", "extensions.hgext.rebase="); //$NON-NLS-1$ //$NON-NLS-2$
			command.addOptions("--rebase"); //$NON-NLS-1$
		}

		if (force) {
			command.addOptions("--force"); //$NON-NLS-1$
		}
		if (changeset != null) {
			command.addOptions("--rev", changeset.getChangeset()); //$NON-NLS-1$
		}

		addRepoToHgCommand(repo, command);

		String result = null;
		try {
			if (timeout) {
				command.setUsePreferenceTimeout(MercurialPreferenceConstants.PULL_TIMEOUT);
				result = new String(command.executeToBytes());
			} else {
				result = new String(command.executeToBytes(Integer.MAX_VALUE));
			}
		} finally {
			if (update && result != null && result.contains("not updating, since new heads added")) {
				// inform user about new heads and ask if he wants to merge
				UpdateJob.handleMultipleHeads(hgRoot, false);
			}

			// doesn't metter how far we was: we have to trigger update of caches in case
			// the pull was *partly* successfull (e.g. pull was ok, but update not)
			refreshProjects(update, hgRoot);
		}
		return result;
	}



	private static void refreshProjects(boolean update, final HgRoot hgRoot) {
		// The reason to use "all" instead of only "local + incoming", is that we can pull
		// from another repo as the sync clients for given project may use
		// in this case, we also need to update "outgoing" changesets
		final int flags = RefreshRootJob.ALL;
		if(update) {
			new RefreshWorkspaceStatusJob(hgRoot, flags).schedule();
		} else {
			new RefreshRootJob(hgRoot, flags).schedule();
		}
	}
}
