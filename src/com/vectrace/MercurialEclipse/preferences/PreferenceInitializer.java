/*******************************************************************************
 * Copyright (c) 2005-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VecTrace (Zingo Andersen) - implementation
 *     Jérôme Nègre              - adding label decorator section
 *     Stefan C                  - Code cleanup
 *     Andrei Loskutov (Intland) - bug fixes
 *     Zsolt Koppany (intland)   - bug fixes
 *******************************************************************************/

package com.vectrace.MercurialEclipse.preferences;

import static com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = MercurialEclipsePlugin.getDefault().getPreferenceStore();

		// per default, we use exact the executable we have (if any) on board
		store.setDefault(USE_BUILT_IN_HG_EXECUTABLE, true);

		// try to find out, IF we have the built-in hg executable
		detectAndSetHgExecutable(store);

		store.setDefault(MERCURIAL_USERNAME, System.getProperty ( "user.name" )); //$NON-NLS-1$

		// Andrei: not really sure why it was ever set to "modified" as default.
		// "Highest" importance should be default, like "merge conflict"
		// when having 2 different statuses in a folder it should have the more important one
		store.setDefault(LABELDECORATOR_LOGIC, MercurialPreferenceConstants.LABELDECORATOR_LOGIC_HB);

		store.setDefault(RESOURCE_DECORATOR_COMPLETE_STATUS, false);
		store.setDefault(RESOURCE_DECORATOR_COMPUTE_DEEP_STATUS, true);
		store.setDefault(RESOURCE_DECORATOR_SHOW_CHANGESET, false);
		store.setDefault(RESOURCE_DECORATOR_SHOW_INCOMING_CHANGESET, false);

		store.setDefault(LOG_BATCH_SIZE, 200);
		store.setDefault(STATUS_BATCH_SIZE, 10);
		store.setDefault(COMMIT_MESSAGE_BATCH_SIZE, 10);

		// blue
		store.setDefault(PREF_CONSOLE_COMMAND_COLOR, "0,0,255");
		// black
		store.setDefault(PREF_CONSOLE_MESSAGE_COLOR, "0,0,0");
		// red
		store.setDefault(PREF_CONSOLE_ERROR_COLOR, "255,0,0");

		store.setDefault(PREF_DECORATE_WITH_COLORS, true);
		store.setDefault(PREF_SHOW_COMMENTS, true);
		store.setDefault(PREF_SHOW_PATHS, true);
		store.setDefault(PREF_AFFECTED_PATHS_LAYOUT, LAYOUT_HORIZONTAL);

		/*
		store.setDefault(PreferenceConstants.P_CHOICE, "choice2");
		store.setDefault(PreferenceConstants.P_STRING,"Default value");
		 */
	}

	private void detectAndSetHgExecutable(IPreferenceStore store) {
		// Currently only tested on Windows. The binary is expected to be found
		// at "os\win32\x86\hg.exe" (relative to the plugin/fragment directory)
		File hgExecutable = getIntegratedHgExecutable();
		String defaultExecPath;
		String existingValue = store.getString(MERCURIAL_EXECUTABLE);
		if(hgExecutable == null) {
			defaultExecPath = "hg";
			if(existingValue != null && !new File(existingValue).isFile()){
				store.setValue(MERCURIAL_EXECUTABLE, defaultExecPath);
			}
		} else {
			defaultExecPath = hgExecutable.getPath();
			if (store.getBoolean(USE_BUILT_IN_HG_EXECUTABLE)
					|| (existingValue == null || !new File(existingValue).isFile())) {
				store.setValue(MERCURIAL_EXECUTABLE, defaultExecPath);
			}
		}
		store.setDefault(MERCURIAL_EXECUTABLE, defaultExecPath);
	}

	/**
	 * @return an full absolute path to the embedded hg executable from the (fragment)
	 * plugin. This path is guaranteed to point to an <b>existing</b> file. Returns null
	 * if the file cann ot be found, does not exists or is not a file at all.
	 */
	public static File getIntegratedHgExecutable(){
		boolean isWindows = File.separatorChar == '\\';
		IPath path = isWindows ? new Path("$os$/hg.exe") : new Path("$os$/hg");
		URL url = FileLocator.find(MercurialEclipsePlugin.getDefault().getBundle(), path, null);
		if(url == null){
			return null;
		}
		try {
			url = FileLocator.toFileURL(url);
			File execFile = new File(url.getPath());
			if (execFile.isFile()) {
				return execFile.getAbsoluteFile();
			}
		} catch (IOException e) {
			MercurialEclipsePlugin.logError(e);
		}
		return null;
	}
}
