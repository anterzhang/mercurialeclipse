/*******************************************************************************
 * Copyright (c) 2006-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bastian Doetsch
 *     Andrei Loskutov (Intland) - bug fixes
 *******************************************************************************/
package com.vectrace.MercurialEclipse.menu;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;

import com.vectrace.MercurialEclipse.commands.RefreshWorkspaceStatusJob;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.team.MercurialTeamProvider;
import com.vectrace.MercurialEclipse.team.cache.RefreshJob;
import com.vectrace.MercurialEclipse.team.cache.RefreshRootJob;
import com.vectrace.MercurialEclipse.wizards.TransplantWizard;

public class TransplantHandler extends SingleResourceHandler {

	@Override
	protected void run(IResource resource) throws Exception {
		IProject project = resource.getProject();
		TransplantWizard transplantWizard = new TransplantWizard(project);
		WizardDialog transplantWizardDialog = new WizardDialog(getShell(), transplantWizard);
		int result = transplantWizardDialog.open();
		if (result == Window.OK) {
			final HgRoot hgRoot = MercurialTeamProvider.getHgRoot(project);

			RefreshWorkspaceStatusJob job = new RefreshWorkspaceStatusJob(hgRoot);
			job.addJobChangeListener(new JobChangeAdapter(){
			@Override
				public void done(IJobChangeEvent event) {
					new RefreshRootJob("Refreshing " + hgRoot.getName(), hgRoot, RefreshJob.LOCAL_AND_OUTGOING).schedule();
				}
			});
			job.schedule();
		}
	}

}
