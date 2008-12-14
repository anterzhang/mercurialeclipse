/*******************************************************************************
 * Copyright (c) 2005-2008 Bastian Doetsch and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Bastian Doetsch  -   implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.commands;

import java.io.File;

import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants;
import com.vectrace.MercurialEclipse.storage.HgRepositoryLocation;

/**
 * @author bastian
 *
 */
public class HgSvnClient extends AbstractClient {
    
    public static String pull(File currentWorkingDirectory) throws HgException {
        HgCommand cmd = new HgCommand("svn", //$NON-NLS-1$
                getWorkingDirectory(currentWorkingDirectory), false);
        cmd.setUsePreferenceTimeout(MercurialPreferenceConstants.PULL_TIMEOUT);
        cmd.addOptions("pull"); //$NON-NLS-1$
        return cmd.executeToString();
    }

    public static String push(File currentWorkingDirectory) throws HgException {
        HgCommand cmd = new HgCommand("svn", //$NON-NLS-1$
                getWorkingDirectory(currentWorkingDirectory), false);
        cmd.setUsePreferenceTimeout(MercurialPreferenceConstants.PUSH_TIMEOUT);
        cmd.addOptions("push"); //$NON-NLS-1$
        return cmd.executeToString();
    }

    public static String rebase(File currentWorkingDirectory)
            throws HgException {
        HgCommand cmd = new HgCommand("svn", //$NON-NLS-1$
                getWorkingDirectory(currentWorkingDirectory), false);
        cmd.setUsePreferenceTimeout(MercurialPreferenceConstants.PUSH_TIMEOUT);
        cmd.addOptions("--config", "extensions.hgext.rebase="); //$NON-NLS-1$ //$NON-NLS-2$
        cmd.addOptions("rebase"); //$NON-NLS-1$
        return cmd.executeToString();
    }

    public static String clone(File currentWorkingDirectory,
            HgRepositoryLocation repo, String cloneName) throws HgException {
        HgCommand cmd = new HgCommand("svnclone", //$NON-NLS-1$
                getWorkingDirectory(currentWorkingDirectory), false);
        cmd.setUsePreferenceTimeout(MercurialPreferenceConstants.CLONE_TIMEOUT);
        addRepoToHgCommand(repo, cmd);
        cmd.addOptions(cloneName);
        return cmd.executeToString();
    }
}