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

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;

import com.vectrace.MercurialEclipse.commands.AbstractClient;
import com.vectrace.MercurialEclipse.commands.HgCommand;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.Patch;

/**
 * @author bastian
 *
 */
public class HgQAppliedClient extends AbstractClient {
    public static List<Patch> getAppliedPatches(IResource resource) throws HgException {
        Assert.isNotNull(resource);
        HgCommand command = new HgCommand("qapplied",getWorkingDirectory(resource),true);       //$NON-NLS-1$
        command.addOptions("--config", "extensions.hgext.mq="); //$NON-NLS-1$ //$NON-NLS-2$
        command.addOptions("-v"); //$NON-NLS-1$
        command.addOptions("-s"); //$NON-NLS-1$
        return HgQSeriesClient.parse(command.executeToString());
    }
    
    public static List<Patch> getUnappliedPatches(IResource resource) throws HgException{
        Assert.isNotNull(resource);
        HgCommand command = new HgCommand("qunapplied",getWorkingDirectory(resource),true);       //$NON-NLS-1$
        command.addOptions("--config", "extensions.hgext.mq="); //$NON-NLS-1$ //$NON-NLS-2$
        command.addOptions("-v"); //$NON-NLS-1$
        command.addOptions("-s"); //$NON-NLS-1$
        return HgQSeriesClient.parse(command.executeToString());
    }
       
}