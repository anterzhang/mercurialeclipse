/*******************************************************************************
 * Copyright (c) 2005-2010 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * bastian       implementation
 * Philip Graf   bug fix
 *******************************************************************************/
package com.vectrace.MercurialEclipse.wizards.mq;

import static com.vectrace.MercurialEclipse.ui.SWTWidgetHelper.*;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;
import org.eclipse.ui.texteditor.AnnotationPreference;
import org.eclipse.ui.texteditor.DefaultMarkerAnnotationAccess;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.eclipse.ui.texteditor.spelling.SpellingAnnotation;

import com.vectrace.MercurialEclipse.team.MercurialUtilities;
import com.vectrace.MercurialEclipse.wizards.HgWizardPage;

/**
 * @author bastian
 *
 */
public class QNewWizardPage extends HgWizardPage {

	private final IResource resource;
	private Text patchNameTextField;
	private Text userTextField;
	private Text date;
	private Button forceCheckBox;
	private Button gitCheckBox;
	private Text includeTextField;
	private Text excludeTextField;
	private final boolean showPatchName;
	private SourceViewer commitTextBox;
	private SourceViewerDecorationSupport decorationSupport;
	private IDocument commitTextDocument;

	/**
	 * @param pageName
	 * @param title
	 * @param titleImage
	 * @param description
	 */
	public QNewWizardPage(String pageName, String title,
			ImageDescriptor titleImage, String description, IResource resource,
			boolean showPatchName) {
		super(pageName, title, titleImage, description);
		this.resource = resource;
		this.showPatchName = showPatchName;
		this.commitTextDocument = new Document();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 2);
		Group g = createGroup(composite, Messages.getString("QNewWizardPage.patchDataGroup.title")); //$NON-NLS-1$
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.minimumHeight = 150;
		g.setLayoutData(data);
		if (showPatchName) {
			createLabel(g, Messages.getString("QNewWizardPage.patchNameLabel.title")); //$NON-NLS-1$
			this.patchNameTextField = createTextField(g);
		}

		createLabel(g, Messages.getString("QNewWizardPage.userNameLabel.title")); //$NON-NLS-1$
		userTextField = createTextField(g);
		userTextField.setText(MercurialUtilities.getDefaultUserName());

		createLabel(g, Messages.getString("QNewWizardPage.dateLabel.title")); //$NON-NLS-1$
		date = createTextField(g);

		createLabel(g, Messages
				.getString("QNewWizardPage.commitMessageLabel.title")); //$NON-NLS-1$
		commitTextBox = new SourceViewer(g, null, SWT.V_SCROLL | SWT.MULTI | SWT.BORDER | SWT.WRAP);
		commitTextBox.getTextWidget().setLayoutData(data);


		// set up spell-check annotations
		decorationSupport = new SourceViewerDecorationSupport(commitTextBox,
				null, new DefaultMarkerAnnotationAccess(), EditorsUI
						.getSharedTextColors());

		AnnotationPreference pref = EditorsUI.getAnnotationPreferenceLookup()
				.getAnnotationPreference(SpellingAnnotation.TYPE);

		decorationSupport.setAnnotationPreference(pref);
		decorationSupport.install(EditorsUI.getPreferenceStore());

		commitTextBox.configure(new TextSourceViewerConfiguration(EditorsUI
				.getPreferenceStore()));
		AnnotationModel annotationModel = new AnnotationModel();
		commitTextBox.setDocument(commitTextDocument, annotationModel);
		commitTextBox.getTextWidget().addDisposeListener(new DisposeListener() {

			public void widgetDisposed(DisposeEvent e) {
				decorationSupport.uninstall();
			}

		});

		g = createGroup(composite, Messages.getString("QNewWizardPage.optionsGroup.title")); //$NON-NLS-1$
		this.forceCheckBox = createCheckBox(g,
				Messages.getString("QNewWizardPage.forceCheckBox.title")); //$NON-NLS-1$
		this.gitCheckBox = createCheckBox(g, Messages.getString("QNewWizardPage.gitCheckBox.title")); //$NON-NLS-1$
		this.gitCheckBox.setSelection(true);

		createLabel(g, Messages.getString("QNewWizardPage.includeLabel.title")); //$NON-NLS-1$
		this.includeTextField = createTextField(g);

		createLabel(g, Messages.getString("QNewWizardPage.excludeLabel.title")); //$NON-NLS-1$
		this.excludeTextField = createTextField(g);

		setControl(composite);
	}

	/**
	 * @return the resource
	 */
	public IResource getResource() {
		return resource;
	}

	/**
	 * @return the patchNameTextField
	 */
	public Text getPatchNameTextField() {
		return patchNameTextField;
	}

	/**
	 * @param patchNameTextField
	 *            the patchNameTextField to set
	 */
	public void setPatchNameTextField(Text patchNameTextField) {
		this.patchNameTextField = patchNameTextField;
	}

	/**
	 * @return the date
	 */
	public Text getDate() {
		return date;
	}

	/**
	 * @param date
	 *            the date to set
	 */
	public void setDate(Text date) {
		this.date = date;
	}

	/**
	 * @return the forceCheckBox
	 */
	public Button getForceCheckBox() {
		return forceCheckBox;
	}

	/**
	 * @param forceCheckBox
	 *            the forceCheckBox to set
	 */
	public void setForceCheckBox(Button forceCheckBox) {
		this.forceCheckBox = forceCheckBox;
	}

	/**
	 * @return the gitCheckBox
	 */
	public Button getGitCheckBox() {
		return gitCheckBox;
	}

	/**
	 * @param gitCheckBox
	 *            the gitCheckBox to set
	 */
	public void setGitCheckBox(Button gitCheckBox) {
		this.gitCheckBox = gitCheckBox;
	}

	/**
	 * @return the includeTextField
	 */
	public Text getIncludeTextField() {
		return includeTextField;
	}

	/**
	 * @param includeTextField
	 *            the includeTextField to set
	 */
	public void setIncludeTextField(Text includeTextField) {
		this.includeTextField = includeTextField;
	}

	/**
	 * @return the excludeTextField
	 */
	public Text getExcludeTextField() {
		return excludeTextField;
	}

	/**
	 * @param excludeTextField
	 *            the excludeTextField to set
	 */
	public void setExcludeTextField(Text excludeTextField) {
		this.excludeTextField = excludeTextField;
	}

	/**
	 * @return the userTextField
	 */
	public Text getUserTextField() {
		return userTextField;
	}

	/**
	 * @param userTextField
	 *            the userTextField to set
	 */
	public void setUserTextField(Text userTextField) {
		this.userTextField = userTextField;
	}

	/**
	 * @return the commitTextDocument
	 */
	public IDocument getCommitTextDocument() {
		return commitTextDocument;
	}

	/**
	 * @param commitTextDocument
	 *            the commitTextDocument to set
	 */
	public void setCommitTextDocument(IDocument commitTextDocument) {
		this.commitTextDocument = commitTextDocument;
	}

}