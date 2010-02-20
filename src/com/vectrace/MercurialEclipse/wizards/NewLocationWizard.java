/*******************************************************************************
 * Copyright (c) 2003, 2006 Subclipse project and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Subclipse project committers - initial API and implementation
 *     Andrei Loskutov (Intland) - bug fixes
 ******************************************************************************/
package com.vectrace.MercurialEclipse.wizards;

import java.io.File;
import java.util.Properties;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.commands.HgInitClient;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.IHgRepositoryLocation;
import com.vectrace.MercurialEclipse.storage.HgRepositoryLocationManager;

/**
 * Wizard to add a new location. Uses ConfigurationWizardMainPage for entering
 * informations about Hg repository location
 */
public class NewLocationWizard extends HgWizard implements INewWizard {

	private IHgRepositoryLocation repository;

	public NewLocationWizard() {
		super(Messages.getString("NewLocationWizard.name")); //$NON-NLS-1$
	}

	public NewLocationWizard(Properties initialProperties) {
		this();
		this.properties = initialProperties;
	}

	@Override
	public void addPages() {
		page = createPage(Messages.getString("NewLocationWizard.repoCreationPage.description")); //$NON-NLS-1$
		addPage(page);
	}

	/**
	 * @see IWizard#performFinish
	 */
	@Override
	public boolean performFinish() {
		super.performFinish();
		File localRepo = ((CreateRepoPage)page).getLocalRepo();
		if(localRepo != null){
			try {
				HgInitClient.init(localRepo);
			} catch (HgException e) {
				MercurialEclipsePlugin.logError(e);
				page.setErrorMessage(e.getMessage());
				return false;
			}
		}
		Properties props = page.getProperties();
		HgRepositoryLocationManager manager = MercurialEclipsePlugin.getRepoManager();
		try {
			repository = manager.createRepository(props);
		} catch (HgException ex) {
			MercurialEclipsePlugin.logError(ex);
			return false;
		}
		return true;
	}

	/**
	 * Creates a ConfigurationWizardPage.
	 */
	protected HgWizardPage createPage(String description) {
		page = new CreateRepoPage();
		initPage(description, page);
		return page;
	}

	public IHgRepositoryLocation getRepository() {
		return repository;
	}

	public void init(IWorkbench workbench, IStructuredSelection selection) {
		// noop
	}
}
