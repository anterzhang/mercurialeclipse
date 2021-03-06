/*******************************************************************************
 * Copyright (c) 2005-2008 VecTrace (Zingo Andersen) and others. All rights
 * reserved. This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Bastian Doetsch - implementation
 * Andrei Loskutov - bugfixes
 * Zsolt Koppany (Intland)
 * Ilya Ivanov (Intland)
 ******************************************************************************/

package com.vectrace.MercurialEclipse.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;

import com.aragost.javahg.Changeset;
import com.aragost.javahg.commands.ExecutionException;
import com.aragost.javahg.commands.LogCommand;
import com.aragost.javahg.commands.flags.HeadsCommandFlags;
import com.aragost.javahg.commands.flags.LogCommandFlags;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.model.ChangeSet.Direction;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.model.IHgRepositoryLocation;
import com.vectrace.MercurialEclipse.model.JHgChangeSet;
import com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants;
import com.vectrace.MercurialEclipse.team.cache.CommandServerCache;
import com.vectrace.MercurialEclipse.team.cache.LocalChangesetCache;
import com.vectrace.MercurialEclipse.utils.BranchUtils;
import com.vectrace.MercurialEclipse.utils.ResourceUtils;

public class HgLogClient extends AbstractClient {

	public static final String NOLIMIT = "999999999999";

	private static final Comparator<Changeset> CS_COMPARATOR = new Comparator<Changeset>() {
		public int compare(Changeset o1, Changeset o2) {
			return o2.getRevision() - o1.getRevision();
		}
	};

	/**
	 * Get the heads of the given repository
	 * @param hgRoot The repository
	 * @return All the heads
	 */
	public static Changeset[] getHeads(HgRoot hgRoot) {
		return toArray(HeadsCommandFlags.on(hgRoot.getRepository()).execute());
	}

	/**
	 * Returns the current node-id as a String
	 *
	 * @param hgRoot
	 *            the root of the repository to identify
	 * @return Returns the node-id for the current changeset
	 * @throws HgException
	 */
	public static String getCurrentChangesetId(HgRoot hgRoot) throws HgException {
		Changeset cs = getCurrentChangeset(hgRoot);

		if (cs != null) {
			return cs.getNode();
		}

		return Changeset.NULL_ID;
	}

	/**
	 * Returns the current changeset of the repository.
	 *
	 * @param hgRoot
	 *            the root of the repository to identify
	 * @return Returns the current changeset, null if repository is new.
	 * @throws HgException
	 */
	public static Changeset getCurrentChangeset(HgRoot hgRoot) throws HgException {
		return hgRoot.getRepository().workingCopy().getParent1();
	}

	private static Changeset[] toArray(List<Changeset> list) {
		return list.toArray(new Changeset[list.size()]);
	}

	/**
	 * Helper method to transform JavaHg changesets into normal changesets.
	 */
	public static ChangeSet[] getChangeSets(HgRoot root, Changeset[] list) {
		ChangeSet[] ar = new ChangeSet[list.length];

		for (int i = 0, n = list.length; i < n; i++) {
			ar[i] = getChangeSet(root, list[i]);
		}

		return ar;
	}

	private static List<JHgChangeSet> getChangeSets(HgRoot root, List<Changeset> list) {
		List<JHgChangeSet> ar = new ArrayList<JHgChangeSet>(list.size());

		for (int i = 0, n = list.size(); i < n; i++) {
			ar.add(getChangeSet(root, list.get(i)));
		}

		return ar;
	}

	public static List<JHgChangeSet> getResourceLog(HgRoot root, IResource res, int limitNumber, int startRev) {
		boolean isFile = res.getType() == IResource.FILE;
		IPath path = ResourceUtils.getPath(res);
		String sPath = path.toOSString();
		LogCommand command = addRange(LogCommandFlags.on(root.getRepository()), startRev, limitNumber, isFile);
		List<Changeset> c;

		if (isFile) {
			if (Boolean.parseBoolean(HgClients.getPreference(
					MercurialPreferenceConstants.HISTORY_LOG_USE_REMOVED, "false"))) {
				// Return the union of --follow and --removed. Need to show transplanted revisions on other branches
				TreeSet<Changeset> set = new TreeSet<Changeset>(CS_COMPARATOR);

				if (canFollow(root, path)) {
					command.follow();
					set.addAll(command.execute(sPath));
					command = addRange(LogCommandFlags.on(root.getRepository()), startRev, limitNumber, isFile);
				}

				command.removed();
				set.addAll(command.execute(sPath));

				while(set.size() > limitNumber) {
					// Could use descendingIterator but that requires 1.6
					set.remove(set.last());
				}

				c = new ArrayList<Changeset>(set);
			} else {
				if (canFollow(root, path)) {
					command.follow();
				}
				c = command.execute(sPath);
			}
		}
		else
		{
			c = command.execute(sPath);
		}

		return getChangeSets(root, c);
	}

	/**
	 * TODO: This is not correct: should be whether path exists in working directory parent
	 */
	private static boolean canFollow(HgRoot root, IPath path) {
		return path.toFile().exists();
	}

	public static List<JHgChangeSet> getRootLog(HgRoot root, int limitNumber, int startRev) {
		return getChangeSets(root,
				addRange(LogCommandFlags.on(root.getRepository()), startRev, limitNumber, false)
						.execute());
	}

	/**
	 * TODO: reevaluate
	 */
	private static LogCommand addRange(LogCommand command, int startRev, int limitNumber, boolean isFile) {
		if (startRev >= 0 && startRev != Integer.MAX_VALUE) {
			// always advise to follow until 0 revision: the reason is that log limit
			// might be bigger then the difference of two consequent revisions on a specific resource
			command.rev(startRev + ":" + 0); //$NON-NLS-1$
		}
		if(isFile && startRev == Integer.MAX_VALUE) {
			// always start with the tip to get the latest version of a file too
			// seems that hg log misses some versions if the file working copy is not at the tip
			command.rev("tip:0"); //$NON-NLS-1$
		}
		command.limit((limitNumber > 0) ? limitNumber : Integer.MAX_VALUE);

		return command;
	}

	/**
	 * Helper method to transform JavaHg changesets into normal changesets
	 */
	public static JHgChangeSet getChangeSet(HgRoot root, Changeset cs) {
		return LocalChangesetCache.getInstance().get(root, cs);
	}

	/**
	 * Tries to retrieve (possible not existing) changeset for the given id.
	 *
	 * @param hgRoot
	 *            non null
	 * @param nodeId
	 *            non null
	 * @return Not null
	 * @throws HgException If changeset not found
	 */
	public static JHgChangeSet getChangeSet(HgRoot root, String nodeId) throws HgException {
		return getChangeSet(root, nodeId, null, null, null);
	}

	/**
	 * @return Not null
	 * @throws HgException
	 */
	public static JHgChangeSet getChangeSet(HgRoot root, String nodeId, IHgRepositoryLocation remote,
			Direction direction, File bundle) throws HgException {
		LogCommand command = LogCommandFlags.on(CommandServerCache.getInstance().get(root, bundle)).rev(nodeId);
		Changeset cs;
		try {
			cs = command.single();
		} catch (ExecutionException e) {
			throw new HgException("Could not locate changeset \"" + nodeId + "\"", e);
		}

		if (bundle != null) {
			// TODO: cache?
			return new JHgChangeSet(root, cs, remote, direction, bundle);
		}

		return getChangeSet(root, cs);
	}

	/**
	 * Get a changeset by revision index
	 *
	 * @param root The root
	 * @param rev The index
	 * @return The change set
	 * @throws HgException If the revision is not found
	 */
	public static JHgChangeSet getChangeSet(HgRoot root, int rev) throws HgException {
		return getChangeSet(root, rev + ":" + rev);
	}

	public static int countChangesets(HgRoot root, String rev) {
		List<Changeset> cs = LogCommandFlags.on(root.getRepository()).rev(rev).execute();

		return cs.size();
	}

	public static int numHeadsInBranch(HgRoot hgRoot, String branch) {
		List<Changeset> cs;
		try {
			cs = HeadsCommandFlags.on(hgRoot.getRepository()).execute();
		} catch (ExecutionException e) {
			// No head, new repo?
			return 0;
		}

		int i = 0;

		// As of 2.3 it's faster to get all heads and then filter rather than do --rev
		// "branch(name)". 60ms vs 900ms

		for(Changeset c : cs) {
			if (BranchUtils.same(branch, c.getBranch())) {
				i += 1;
			}
		}

		return i;
	}
}
