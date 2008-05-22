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
package com.vectrace.MercurialEclipse.commands;

import java.io.File;
import java.util.List;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants;

/**
 * @author bastian
 * 
 */
public class GpgCommand extends AbstractShellCommand {

    protected GpgCommand(List<String> commands, File workingDir,
            boolean escapeFiles) {
        super(commands, workingDir, escapeFiles);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.vectrace.MercurialEclipse.commands.AbstractShellCommand#getExecutable()
     */
    @Override
    protected String getExecutable() {
        return MercurialEclipsePlugin.getDefault().getPreferenceStore()
                .getString(MercurialPreferenceConstants.GPG_EXECUTABLE);
    }

}
