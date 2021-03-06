/*******************************************************************************
 * Copyright (c) 2005-2010 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * John Peberdy	implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.dialogs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.part.FileEditorInput;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.ui.SWTWidgetHelper;
import com.vectrace.MercurialEclipse.utils.ResourceUtils;

public class RejectsDialog extends TrayDialog {
	private static final Pattern REJECT_PATTERN = Pattern.compile("saving rejects to file (.*)\\s");
	private static final Pattern CHANGESET_PATTERN = Pattern.compile("applying (.*)\\s");

	private final List<IFile> rejects;
	protected final HgRoot hgRoot;
	protected final String failedChangeSetId;

	private CheckboxTableViewer viewer;
	private Button openAllRejectsCheckBox;

	protected IFile selectedFile;
	private final String title;
	private final String detail;

	public RejectsDialog(Shell shell, HgRoot hgRoot, String message, String title, String detail)
			throws HgException {
		this(shell, hgRoot, message, title, detail, true);
	}

	public RejectsDialog(Shell shell, HgRoot hgRoot, String message, String title, String detail, boolean requireSource)
	throws HgException {
		super(shell);

		this.title = title;
		this.detail = detail;
		this.hgRoot = hgRoot;

		Matcher matcher = CHANGESET_PATTERN.matcher(message);
		boolean found = matcher.find() && matcher.groupCount() > 0;
		if (!found && requireSource) {
			throw new HgException("Couldn't find source");
		}
		this.failedChangeSetId = found ? matcher.group(1) : "";

		rejects = new ArrayList<IFile>();

		matcher = REJECT_PATTERN.matcher(message);
		int lastMatchOffset = 0;
		while (matcher.find(lastMatchOffset) && matcher.groupCount() > 0) {
			String filename = matcher.group(1);
			IPath path = new Path(hgRoot.getPath()).append(filename);
			IFile file = FileBuffers.getWorkspaceFileAtLocation(path);
			if (file != null) {
				rejects.add(file);
			}
			lastMatchOffset = matcher.end();
		}

		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	@Override
	protected IDialogSettings getDialogBoundsSettings() {
		IDialogSettings dialogSettings = MercurialEclipsePlugin.getDefault().getDialogSettings();
		String sectionName = getClass().getSimpleName();
		IDialogSettings section = dialogSettings.getSection(sectionName);
		if (section == null) {
			dialogSettings.addNewSection(sectionName);
		}
		return section;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		getShell().setText(Messages.getString(title, failedChangeSetId));

		Composite container = SWTWidgetHelper.createComposite(parent, 1);
		GridData gd = SWTWidgetHelper.getFillGD(400);
		gd.minimumWidth = 500;
		gd.widthHint = container.getBorderWidth();
		container.setLayoutData(gd);
		super.createDialogArea(parent);

		Label label = new Label(container, SWT.WRAP);
		final GridData data = new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1);
		label.setLayoutData(data);
		label.setText(Messages.getString(detail));

		Table table = createTable(container);
		viewer = new CheckboxTableViewer(table);
		viewer.setContentProvider(new ArrayContentProvider());
		viewer.setLabelProvider(new RejectLabelProvider());
		viewer.setInput(rejects);

		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				showRejectedDiff(selectedFile);
			}
		});

		addSelectionListener();

		openAllRejectsCheckBox = SWTWidgetHelper.createCheckBox(container, Messages
				.getString("RejectsDialog.openAllRejects"));

		return container;
	}

	private void addSelectionListener() {
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				ISelection selection = event.getSelection();

				if (selection instanceof IStructuredSelection) {
					IStructuredSelection sel = (IStructuredSelection) selection;
					IFile res = (IFile) sel.getFirstElement();
					selectedFile = res;
				}
			}

		});
	}

	private static void showRejectedDiff(IFile file) {
		if (file != null) {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
					.getActivePage();

			IEditorDescriptor desc = PlatformUI.getWorkbench().getEditorRegistry().findEditor(
					org.eclipse.ui.editors.text.EditorsUI.DEFAULT_TEXT_EDITOR_ID);

			try {
				page.openEditor(new FileEditorInput(file), desc.getId());
			} catch (PartInitException e) {
			}
		}
	}

	private static Table createTable(Composite composite) {
		int flags = SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER;
		flags |= SWT.READ_ONLY | SWT.HIDE_SELECTION;

		Table table = new Table(composite, flags);
		table.setHeaderVisible(false);
		table.setLinesVisible(true);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		table.setLayoutData(data);
		return table;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
	}

	@Override
	protected void okPressed() {
		boolean editRejects = openAllRejectsCheckBox.getSelection();
		close();
		if (editRejects) {
			for (IFile file : rejects) {
				showRejectedDiff(file);
			}
		}
	}

	private final class RejectLabelProvider extends LabelProvider {

		private final ILabelProvider workbenchLabelProvider;

		public RejectLabelProvider() {
			super();
			workbenchLabelProvider = WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider();
			workbenchLabelProvider.addListener(new ILabelProviderListener() {
				public void labelProviderChanged(LabelProviderChangedEvent event) {
					fireLabelProviderChanged(event);
				}
			});
		}

		@Override
		public boolean isLabelProperty(Object element, String property) {
			return true;
		}

		@Override
		public Image getImage(Object element) {
			if (!(element instanceof IResource)) {
				return null;
			}
			return workbenchLabelProvider.getImage(element);
		}

		@Override
		public String getText(Object element) {
			if (!(element instanceof IResource)) {
				return null;
			}
			return hgRoot.toRelative(ResourceUtils.getFileHandle(((IResource) element)));
		}

		@Override
		public void dispose() {
			workbenchLabelProvider.dispose();
			super.dispose();
		}
	}
}
