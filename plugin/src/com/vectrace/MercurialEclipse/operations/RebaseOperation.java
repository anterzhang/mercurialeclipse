/*******************************************************************************
 * Copyright (c) 2005-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * bastian	implementation
 *     Andrei Loskutov - bug fixes
 *******************************************************************************/
package com.vectrace.MercurialEclipse.operations;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableContext;

import com.aragost.javahg.ext.rebase.merge.RebaseConflictResolvingContext;
import com.vectrace.MercurialEclipse.actions.HgOperation;
import com.vectrace.MercurialEclipse.commands.HgResolveClient;
import com.vectrace.MercurialEclipse.commands.extensions.HgRebaseClient;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.team.cache.RefreshRootJob;
import com.vectrace.MercurialEclipse.team.cache.RefreshWorkspaceStatusJob;
import com.vectrace.MercurialEclipse.views.MergeView;

/**
 * @author bastian
 *
 * The operation doesn't fail if a rebase conflict occurs.
 */
public class RebaseOperation extends HgOperation {

	private final HgRoot hgRoot;
	private final String sourceRev;
	private final String destRev;
	private final String baseRev;
	private final boolean collapse;
	private final boolean abort;
	private final boolean cont;
	private boolean keepBranches;
	private boolean keep;
	private final String user;

	public RebaseOperation(IRunnableContext context, HgRoot hgRoot,
			String sourceRev, String destRev, String baseRev, boolean collapse,
			boolean abort, boolean cont) {
		this(context, hgRoot, sourceRev, destRev, baseRev, collapse, abort, cont, false, null);
	}

	protected RebaseOperation(IRunnableContext context, HgRoot hgRoot,
			String sourceRev, String destRev, String baseRev, boolean collapse,
			boolean abort, boolean cont, boolean keepBranches, String user) {
		super(context);
		this.hgRoot = hgRoot;
		this.sourceRev = sourceRev;
		this.destRev = destRev;
		this.baseRev = baseRev;
		this.collapse = collapse;
		this.abort = abort;
		this.cont = cont;
		this.keepBranches = keepBranches;
		this.user = user;
	}

	public void setKeep(boolean keep) {
		this.keep = keep;
	}

	public void setKeepBranches(boolean keepBranches) {
		this.keepBranches = keepBranches;
	}

	/**
	 * @see org.eclipse.jface.operation.IRunnableWithProgress#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void run(IProgressMonitor monitor) throws InvocationTargetException,
			InterruptedException {
		monitor.beginTask(getActionDescription(), 2);
		boolean rebaseConflict = false;
		try {
			monitor.worked(1);
			monitor.subTask(Messages.getString("RebaseOperation.calling")); //$NON-NLS-1$

			if (abort) {
				HgRebaseClient.abortRebase(hgRoot);
			} else {
				RebaseConflictResolvingContext ctx = HgRebaseClient.rebase(hgRoot, sourceRev, baseRev, destRev, collapse, cont,
						keepBranches, keep, user);
				if (HgRebaseClient.isRebasing(hgRoot)) {
					HgResolveClient.autoResolve(hgRoot, ctx);
					rebaseConflict = true;
				}
			}
			monitor.worked(1);

		} catch (HgException e) {
			if(rebaseConflict) {
				result = e.getMessage();
			} else {
				throw new InvocationTargetException(e, e.getLocalizedMessage());
			}
		} finally {
			RefreshWorkspaceStatusJob job = new RefreshWorkspaceStatusJob(hgRoot,
					RefreshRootJob.ALL);

			if(rebaseConflict) {
				job.addJobChangeListener(MergeView.makeConflictJobChangeListener(hgRoot, getShell(), false));
			}

			job.schedule();
			monitor.done();
		}
	}

	@Override
	protected String getActionDescription() {
		return Messages.getString("RebaseOperation.rebasing"); //$NON-NLS-1$
	}

	/**
	 * Factory method to create a continue rebase operation
	 */
	public static RebaseOperation createContinue(IRunnableContext context, HgRoot root, String user)
	{
		return new RebaseOperation(context, root, null, null, null, false, false, true, false, user);
	}

	/**
	 * Factory method to create a abort rebase operation
	 */
	public static RebaseOperation createAbort(IRunnableContext context, HgRoot root)
	{
		return new RebaseOperation(context, root, null, null, null, false, true, false);
	}
}
