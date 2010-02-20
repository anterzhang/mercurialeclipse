/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
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
 *******************************************************************************/
package com.vectrace.MercurialEclipse.search;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.search.internal.ui.text.LineElement;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.Match;

import com.vectrace.MercurialEclipse.team.MercurialRevisionStorage;

public class MercurialTextSearchTreeContentProvider implements
		ITreeContentProvider, IMercurialTextSearchContentProvider {

	private final Object[] EMPTY_ARR = new Object[0];

	private AbstractTextSearchResult fResult;
	private final MercurialTextSearchResultPage fPage;
	private final AbstractTreeViewer fTreeViewer;
	private Map fChildrenMap;

	MercurialTextSearchTreeContentProvider(MercurialTextSearchResultPage page,
			AbstractTreeViewer viewer) {
		fPage = page;
		fTreeViewer = viewer;
	}

	public Object[] getElements(Object inputElement) {
		Object[] children = getChildren(inputElement);
		int elementLimit = getElementLimit();
		if (elementLimit != -1 && elementLimit < children.length) {
			Object[] limitedChildren = new Object[elementLimit];
			System.arraycopy(children, 0, limitedChildren, 0, elementLimit);
			return limitedChildren;
		}
		return children;
	}

	private int getElementLimit() {
		return fPage.getElementLimit().intValue();
	}

	public void dispose() {
		// nothing to do
	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		if (newInput instanceof MercurialTextSearchResult) {
			initialize((MercurialTextSearchResult) newInput);
		}
	}

	private synchronized void initialize(AbstractTextSearchResult result) {
		fResult = result;
		fChildrenMap = new HashMap();
		boolean showLineMatches = !((MercurialTextSearchQuery) fResult
				.getQuery()).isFileNameSearch();

		if (result != null) {
			Object[] elements = result.getElements();
			for (int i = 0; i < elements.length; i++) {
				if (showLineMatches) {
					Match[] matches = result.getMatches(elements[i]);
					for (int j = 0; j < matches.length; j++) {
						MercurialMatch m = ((MercurialMatch) matches[j]);
						String child = "Changeset: " + m.getRev() + ", Line: "
								+ m.getLineNumber() + ", Extract: "
								+ m.getExtract();
						insert(child, false);
					}
				} else {
					insert(elements[i], false);
				}
			}
		}
	}

	private void insert(Object child, boolean refreshViewer) {
		Object parent = getParent(child);
		while (parent != null) {
			if (insertChild(parent, child)) {
				if (refreshViewer) {
					fTreeViewer.add(parent, child);
				}
			} else {
				if (refreshViewer) {
					fTreeViewer.refresh(parent);
				}
				return;
			}
			child = parent;
			parent = getParent(child);
		}
		if (insertChild(fResult, child)) {
			if (refreshViewer) {
				fTreeViewer.add(fResult, child);
			}
		}
	}

	/**
	 * Adds the child to the parent.
	 *
	 * @param parent
	 *            the parent
	 * @param child
	 *            the child
	 * @return <code>true</code> if this set did not already contain the
	 *         specified element
	 */
	private boolean insertChild(Object parent, Object child) {
		Set children = (Set) fChildrenMap.get(parent);
		if (children == null) {
			children = new HashSet();
			fChildrenMap.put(parent, children);
		}
		return children.add(child);
	}

	private boolean hasChild(Object parent, Object child) {
		Set children = (Set) fChildrenMap.get(parent);
		return children != null && children.contains(child);
	}

	private void remove(Object element, boolean refreshViewer) {
		// precondition here: fResult.getMatchCount(child) <= 0

		if (hasChildren(element)) {
			if (refreshViewer) {
				fTreeViewer.refresh(element);
			}
		} else {
			if (!hasMatches(element)) {
				fChildrenMap.remove(element);
				Object parent = getParent(element);
				if (parent != null) {
					removeFromSiblings(element, parent);
					remove(parent, refreshViewer);
				} else {
					removeFromSiblings(element, fResult);
					if (refreshViewer) {
						fTreeViewer.refresh();
					}
				}
			} else {
				if (refreshViewer) {
					fTreeViewer.refresh(element);
				}
			}
		}
	}

	private boolean hasMatches(Object element) {
		if (element instanceof LineElement) {
			LineElement lineElement = (LineElement) element;
			return lineElement.getNumberOfMatches(fResult) > 0;
		}
		return fResult.getMatchCount(element) > 0;
	}

	private void removeFromSiblings(Object element, Object parent) {
		Set siblings = (Set) fChildrenMap.get(parent);
		if (siblings != null) {
			siblings.remove(element);
		}
	}

	public Object[] getChildren(Object parentElement) {
		Set children = (Set) fChildrenMap.get(parentElement);
		if (children == null) {
			return EMPTY_ARR;
		}
		return children.toArray();
	}

	public boolean hasChildren(Object element) {
		return getChildren(element).length > 0;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @seeorg.eclipse.search.internal.ui.text.IFileSearchContentProvider#
	 * elementsChanged(java.lang.Object[])
	 */
	public synchronized void elementsChanged(Object[] updatedElements) {
		for (int i = 0; i < updatedElements.length; i++) {
			if (!(updatedElements[i] instanceof MercurialRevisionStorage)) {
				// change events to elements are reported in file search
				if (fResult.getMatchCount(updatedElements[i]) > 0) {
					insert(updatedElements[i], true);
				} else {
					remove(updatedElements[i], true);
				}
			} else {
				// change events to line elements are reported in text search
				// TODO
				MercurialRevisionStorage mrs = (MercurialRevisionStorage) updatedElements[i];
				insert(mrs, true);
			}
		}
	}

	public void clear() {
		initialize(fResult);
		fTreeViewer.refresh();
	}

	public Object getParent(Object element) {
		// TODO
		if (element instanceof IProject) {
			return null;
		}
		if (element instanceof IResource) {
			IResource resource = (IResource) element;
			return resource.getParent();
		}
		if (element instanceof MercurialRevisionStorage) {
			MercurialRevisionStorage mrs = (MercurialRevisionStorage) element;
			return mrs.getResource().getParent();
		}

		if (element instanceof MercurialMatch) {
			MercurialMatch match = (MercurialMatch) element;
			return match.getMercurialRevisionStorage().getChangeSet();
		}
		return null;
	}
}
