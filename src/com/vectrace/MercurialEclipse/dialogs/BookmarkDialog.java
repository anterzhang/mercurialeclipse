/*******************************************************************************
 * Copyright (c) 2008 Bastian Doetsch and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bastian Doetsch - implementation
 *******************************************************************************/

package com.vectrace.MercurialEclipse.dialogs;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.commands.HgBookmarkClient;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.ui.BookmarkTable;
import com.vectrace.MercurialEclipse.ui.ChangesetTable;
import com.vectrace.MercurialEclipse.ui.SWTWidgetHelper;

/**
 * @author bastian
 * 
 */
public class BookmarkDialog extends TrayDialog {

    private IProject project;
    private ChangesetTable csTable;
    private Button revCheckBox;
    private Text bmNameTextBox;
    private Button createButton;
    private Button renameCheckBox;
    private BookmarkTable bookmarkTable;
    private Text newBmNameTextBox;
    private Button deleteCheckBox;
    private boolean modifyTab = false;
    private Label renameLabel;

    public BookmarkDialog(Shell parentShell, IProject project) {
        super(parentShell);
        setShellStyle(getShellStyle() | SWT.RESIZE);
        this.project = project;
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(Messages.getString("BookmarkDialog.shell.text")); //$NON-NLS-1$
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite composite = SWTWidgetHelper.createComposite(parent, 1);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        TabFolder tabFolder = new TabFolder(composite, SWT.FILL);
        tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createCreateTabItem(tabFolder);
        createModifyTabItem(tabFolder);

        return composite;
    }

    protected TabItem createCreateTabItem(TabFolder folder) {
        // setup control
        TabItem item = new TabItem(folder, folder.getStyle());
        item.setText(Messages.getString("BookmarkDialog.createTab.name")); //$NON-NLS-1$
        Composite c = SWTWidgetHelper.createComposite(folder, 2);
        GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
        c.setLayoutData(layoutData);
        item.setControl(c);

        Listener tabSl = new Listener() {
            /*
             * (non-Javadoc)
             * 
             * @see
             * org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.
             * widgets.Event)
             */
            public void handleEvent(Event event) {
                if (event.type == SWT.Show) {
                    modifyTab = false;
                }
            }
        };

        item.addListener(SWT.Show, tabSl);

        // create widgets
        Group tipGroup = SWTWidgetHelper.createGroup(c, Messages.getString("BookmarkDialog.createGroup.label")); //$NON-NLS-1$
        SWTWidgetHelper.createLabel(tipGroup, Messages.getString("BookmarkDialog.bookmarkName")); //$NON-NLS-1$
        this.bmNameTextBox = SWTWidgetHelper.createTextField(tipGroup);
        Group revGroup = SWTWidgetHelper.createGroup(c,
                Messages.getString("BookmarkDialog.selectRevision")); //$NON-NLS-1$
        revGroup.setLayoutData(layoutData);
        this.csTable = new ChangesetTable(revGroup, project, true);
        csTable.setLayoutData(layoutData);
        this.csTable.setEnabled(true);

        SelectionListener sl = new SelectionListener() {
            /*
             * (non-Javadoc)
             * 
             * @see
             * org.eclipse.swt.events.SelectionListener#widgetDefaultSelected
             * (org.eclipse.swt.events.SelectionEvent)
             */
            public void widgetDefaultSelected(SelectionEvent e) {
                widgetSelected(e);
            }

            public void widgetSelected(SelectionEvent e) {
                modifyTab = false;
            }
        };
        csTable.addSelectionListener(sl);
        return item;
    }

    protected TabItem createModifyTabItem(TabFolder folder) {
        // setup control
        TabItem item = new TabItem(folder, folder.getStyle());
        item.setText(Messages.getString("BookmarkDialog.modifyTab.name")); //$NON-NLS-1$
        Composite c = SWTWidgetHelper.createComposite(folder, 2);
        GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
        c.setLayoutData(layoutData);
        item.setControl(c);

        Listener tabSl = new Listener() {
            /*
             * (non-Javadoc)
             * 
             * @see
             * org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.
             * widgets.Event)
             */
            public void handleEvent(Event event) {
                if (event.type == SWT.Show) {
                    modifyTab = true;
                }
            }
        };

        item.addListener(SWT.Show, tabSl);

        // create widgets
        Group selGroup = SWTWidgetHelper.createGroup(c, Messages.getString("BookmarkDialog.selectBookmark")); //$NON-NLS-1$
        selGroup.setLayoutData(layoutData);
        this.bookmarkTable = new BookmarkTable(selGroup, project);
        this.bookmarkTable.setLayoutData(layoutData);
        SelectionListener sl = new SelectionListener() {
            /*
             * (non-Javadoc)
             * 
             * @see
             * org.eclipse.swt.events.SelectionListener#widgetDefaultSelected
             * (org.eclipse.swt.events.SelectionEvent)
             */
            public void widgetDefaultSelected(SelectionEvent e) {
                widgetSelected(e);
            }

            public void widgetSelected(SelectionEvent e) {
                modifyTab = true;
            }
        };
        this.bookmarkTable.addSelectionListener(sl);

        Group renameGroup = SWTWidgetHelper.createGroup(c, Messages.getString("BookmarkDialog.renameGroup.label")); //$NON-NLS-1$
        this.deleteCheckBox(SWTWidgetHelper.createCheckBox(renameGroup,
                Messages.getString("BookmarkDialog.option.delete"))); //$NON-NLS-1$

        SelectionListener delSl = new SelectionListener() {
            /*
             * (non-Javadoc)
             * 
             * @see
             * org.eclipse.swt.events.SelectionListener#widgetDefaultSelected
             * (org.eclipse.swt.events.SelectionEvent)
             */
            public void widgetDefaultSelected(SelectionEvent e) {
                widgetSelected(e);
            }

            /*
             * (non-Javadoc)
             * 
             * @see
             * org.eclipse.swt.events.SelectionListener#widgetSelected(org.eclipse
             * .swt.events.SelectionEvent)
             */
            public void widgetSelected(SelectionEvent e) {
                boolean selection = deleteCheckBox.getSelection();
                if (selection) {
                    renameCheckBox.setSelection(false);
                    renameLabel.setEnabled(false);
                    bmNameTextBox.setEnabled(false);
                    modifyTab = true;
                }

            }
        };
        this.deleteCheckBox.addSelectionListener(delSl);
        this.renameCheckBox = SWTWidgetHelper.createCheckBox(renameGroup,
                Messages.getString("BookmarkDialog.option.rename")); //$NON-NLS-1$
        renameLabel = SWTWidgetHelper.createLabel(renameGroup,
                Messages.getString("BookmarkDialog.newName")); //$NON-NLS-1$
        this.newBmNameTextBox = SWTWidgetHelper.createTextField(renameGroup);
        this.newBmNameTextBox.setEnabled(false);
        renameLabel.setEnabled(false);

        SelectionListener renSl = new SelectionListener() {
            /*
             * (non-Javadoc)
             * 
             * @see
             * org.eclipse.swt.events.SelectionListener#widgetDefaultSelected
             * (org.eclipse.swt.events.SelectionEvent)
             */
            public void widgetDefaultSelected(SelectionEvent e) {
                widgetSelected(e);
            }

            /*
             * (non-Javadoc)
             * 
             * @see
             * org.eclipse.swt.events.SelectionListener#widgetSelected(org.eclipse
             * .swt.events.SelectionEvent)
             */
            public void widgetSelected(SelectionEvent e) {
                boolean selection = renameCheckBox.getSelection();
                if (selection) {
                    deleteCheckBox.setSelection(false);
                    modifyTab = true;
                }
                renameLabel.setEnabled(selection);
                newBmNameTextBox.setEnabled(selection);
            }
        };
        renameCheckBox.addSelectionListener(renSl);
        return item;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.dialogs.Dialog#okPressed()
     */
    @Override
    protected void okPressed() {
        try {
            if (!modifyTab) {
                // create new bookmark
                String targetRev = "tip"; //$NON-NLS-1$
                if (csTable.getSelection() != null) {
                    targetRev = csTable.getSelection().getChangeset();
                }
                HgBookmarkClient.create(project.getLocation().toFile(),
                        bmNameTextBox.getText(), targetRev);
            } else {
                if (renameCheckBox.getSelection()) {
                    HgBookmarkClient.rename(project.getLocation().toFile(),
                            bookmarkTable.getSelection().getName(),
                            newBmNameTextBox.getText());
                } else if (deleteCheckBox.getSelection()) {
                    HgBookmarkClient.delete(project.getLocation().toFile(),
                            bookmarkTable.getSelection().getName());
                }
            }
        } catch (HgException e) {
            MercurialEclipsePlugin.logError(e);
            MercurialEclipsePlugin.showError(e);
        }
        super.okPressed();
    }

    /**
     * @return the project
     */
    public IProject getProject() {
        return project;
    }

    /**
     * @return the csTable
     */
    public ChangesetTable getCsTable() {
        return csTable;
    }

    /**
     * @return the revCheckBox
     */
    public Button getRevCheckBox() {
        return revCheckBox;
    }

    /**
     * @return the bmNameTextBox
     */
    public Text getBmNameTextBox() {
        return bmNameTextBox;
    }

    /**
     * @return the createButton
     */
    public Button getCreateButton() {
        return createButton;
    }

    /**
     * @return the renameCheckBox
     */
    public Button getRenameCheckBox() {
        return renameCheckBox;
    }

    /**
     * @return the bookmarkTable
     */
    public BookmarkTable getBookmarkTable() {
        return bookmarkTable;
    }

    /**
     * @param deleteCheckBox
     *            the deleteCheckBox to set
     */
    public void deleteCheckBox(Button deleteCheckBox) {
        this.deleteCheckBox = deleteCheckBox;
    }

    /**
     * @return the deleteCheckBox
     */
    public Button getDeleteCheckBox() {
        return deleteCheckBox;
    }
}