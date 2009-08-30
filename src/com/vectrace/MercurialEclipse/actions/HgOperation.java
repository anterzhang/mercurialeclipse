/*******************************************************************************
 * Copyright (c) 2006-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Software Balm Consulting Inc (Peter Hunnisett <peter_hge at softwarebalm dot com>) - implementation
 *     VecTrace (Zingo Andersen) - some updates
 *     Stefan Groschupf          - logError
 *     Stefan C                  - Code cleanup
 *******************************************************************************/
package com.vectrace.MercurialEclipse.actions;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.team.ui.TeamOperation;
import org.eclipse.ui.IWorkbenchPart;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.team.MercurialUtilities;

public abstract class HgOperation extends TeamOperation {

    protected String result;

    public HgOperation(IWorkbenchPart part) {
        super(part);
    }

    public HgOperation(IRunnableContext context) {
        super(context);
    }

    public HgOperation(IWorkbenchPart part, IRunnableContext context) {
        super(part, context);
    }

    public void run(IProgressMonitor monitor) throws InvocationTargetException,
            InterruptedException {
        // TODO: Would be nice to have something that indicates progress
        // but that would require that functionality from the utilities.
        monitor.beginTask(getActionDescription(), 1);

        try {
            result = MercurialUtilities.executeCommand(getHgCommand(), getHgWorkingDir(), true);
        } catch (HgException e) {
            MercurialEclipsePlugin.logError(
                    getActionDescription() + Messages.getString("HgOperation.failed"), e); //$NON-NLS-1$
        } finally {
            monitor.done();
        }
    }

    protected String[] getHgCommand() {
        return null;
    }

    protected File getHgWorkingDir() throws HgException {
        return null;
    }

    public String getResult() {
        return result;
    }

    // TODO: No background for now.
    @Override
    protected boolean canRunAsJob() {
        return false;
    }

    @Override
    protected String getJobName() {
        return getActionDescription();
    }

    abstract protected String getActionDescription();
}
