/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Juerg Billeter, juergbi@ethz.ch - 47136 Search view should show match objects
 *     Ulrich Etter, etteru@ethz.ch - 47136 Search view should show match objects
 *     Roman Fuchs, fuchsro@ethz.ch - 47136 Search view should show match objects
 *     Bastian Doetsch - adaptation for MercurialEclipse
 *******************************************************************************/
package com.vectrace.MercurialEclipse.search;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.search.internal.ui.Messages;
import org.eclipse.search.internal.ui.SearchMessages;
import org.eclipse.search.internal.ui.text.NewTextSearchActionGroup;
import org.eclipse.search.ui.IContextMenuConstants;
import org.eclipse.search.ui.ISearchResultViewPart;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;
import org.eclipse.search.ui.text.Match;
import org.eclipse.search2.internal.ui.OpenSearchPreferencesAction;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.part.IPageSite;

@SuppressWarnings("restriction")
public class MercurialTextSearchResultPage extends AbstractTextSearchViewPage
		implements IAdaptable {

	private static final String KEY_LIMIT = "org.eclipse.search.resultpage.limit"; //$NON-NLS-1$

	private static final int DEFAULT_ELEMENT_LIMIT = 1000;

	private ActionGroup fActionGroup;
	private IMercurialTextSearchContentProvider contentProvider;

	public MercurialTextSearchResultPage() {
		setElementLimit(new Integer(DEFAULT_ELEMENT_LIMIT));
	}

	@Override
	public void setElementLimit(Integer elementLimit) {
		super.setElementLimit(elementLimit);
		int limit = elementLimit.intValue();
		getSettings().put(KEY_LIMIT, limit);
	}

	@Override
	public StructuredViewer getViewer() {
		return super.getViewer();
	}

	@Override
	protected void configureTableViewer(TableViewer viewer) {
		viewer.setUseHashlookup(true);
		contentProvider = new MercurialTextSearchTableContentProvider(this);
		viewer.setContentProvider(contentProvider);
		MercurialTextSearchLabelProvider innerLabelProvider = new MercurialTextSearchLabelProvider(
				this, MercurialTextSearchLabelProvider.SHOW_LABEL);
		viewer.setLabelProvider(new DecoratingFileSearchLabelProvider(
				innerLabelProvider));
	}

	@Override
	protected void configureTreeViewer(TreeViewer viewer) {
		viewer.setUseHashlookup(true);
		contentProvider = new MercurialTextSearchTreeContentProvider(this,
				viewer);
		viewer.setContentProvider(contentProvider);
		MercurialTextSearchLabelProvider innerLabelProvider = new MercurialTextSearchLabelProvider(
				this, MercurialTextSearchLabelProvider.SHOW_LABEL);
		viewer.setLabelProvider(new DecoratingFileSearchLabelProvider(
				innerLabelProvider));
	}

	@Override
	protected void showMatch(Match match, int offset, int length,
			boolean activate) throws PartInitException {
		// TODO
	}

	@Override
	protected void handleOpen(OpenEvent event) {
		// TODO
	}

	@Override
	protected void fillContextMenu(IMenuManager mgr) {
		// TODO
	}

	@Override
	public void setViewPart(ISearchResultViewPart part) {
		super.setViewPart(part);
		fActionGroup = new NewTextSearchActionGroup(part);
	}

	@Override
	public void init(IPageSite site) {
		super.init(site);
		IMenuManager menuManager = site.getActionBars().getMenuManager();
		menuManager.appendToGroup(IContextMenuConstants.GROUP_PROPERTIES,
				new OpenSearchPreferencesAction());
	}

	@Override
	public void dispose() {
		fActionGroup.dispose();
		super.dispose();
	}

	@Override
	protected void elementsChanged(Object[] objects) {
		if (contentProvider != null) {
			contentProvider.elementsChanged(objects);
		}
	}

	@Override
	protected void clear() {
		if (contentProvider != null) {
			contentProvider.clear();
		}
	}

	@Override
	public void restoreState(IMemento memento) {
		super.restoreState(memento);
		int elementLimit = DEFAULT_ELEMENT_LIMIT;
		try {
			elementLimit = getSettings().getInt(KEY_LIMIT);
		} catch (NumberFormatException e) {
		}
		if (memento != null) {
			Integer value = memento.getInteger(KEY_LIMIT);
			if (value != null) {
				elementLimit = value.intValue();
			}
		}
		setElementLimit(new Integer(elementLimit));
	}

	@Override
	public void saveState(IMemento memento) {
		super.saveState(memento);
		memento.putInteger(KEY_LIMIT, getElementLimit().intValue());
	}

	@SuppressWarnings("unchecked")
	public Object getAdapter(Class adapter) {
		return null;
	}

	@Override
	public String getLabel() {
		String label = super.getLabel();
		StructuredViewer viewer = getViewer();
		if (viewer instanceof TableViewer) {
			TableViewer tv = (TableViewer) viewer;

			AbstractTextSearchResult result = getInput();
			if (result != null) {
				int itemCount = ((IStructuredContentProvider) tv
						.getContentProvider()).getElements(getInput()).length;
				if (showLineMatches()) {
					int matchCount = getInput().getMatchCount();
					if (itemCount < matchCount) {
						return Messages
								.format(
										SearchMessages.FileSearchPage_limited_format_matches,
										new Object[] { label,
												new Integer(itemCount),
												new Integer(matchCount) });
					}
				} else {
					int fileCount = getInput().getElements().length;
					if (itemCount < fileCount) {
						return Messages
								.format(
										SearchMessages.FileSearchPage_limited_format_files,
										new Object[] { label,
												new Integer(itemCount),
												new Integer(fileCount) });
					}
				}
			}
		}
		return label;
	}

	private boolean showLineMatches() {
		AbstractTextSearchResult input = getInput();
		return getLayout() == FLAG_LAYOUT_TREE
				&& input != null
				&& !((MercurialTextSearchQuery) input.getQuery())
						.isFileNameSearch();
	}

}
