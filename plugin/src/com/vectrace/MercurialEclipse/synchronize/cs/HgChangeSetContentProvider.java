/*******************************************************************************
 * Copyright (c) 2006, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	   IBM Corporation - initial API and implementation
 *     Andrei Loskutov - adopting to hg
 *     Soren Mathiasen - implemented multiple repositories
 *     Martin Olsen    - implemented multiple repositories
 *******************************************************************************/
package com.vectrace.MercurialEclipse.synchronize.cs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Observer;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.mapping.ResourceTraversal;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.team.core.diff.IDiffChangeEvent;
import org.eclipse.team.core.mapping.ISynchronizationContext;
import org.eclipse.team.internal.core.subscribers.BatchingChangeSetManager;
import org.eclipse.team.internal.core.subscribers.BatchingChangeSetManager.CollectorChangeEvent;
import org.eclipse.team.internal.core.subscribers.IChangeSetChangeListener;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.internal.ui.synchronize.ChangeSetCapability;
import org.eclipse.team.internal.ui.synchronize.IChangeSetProvider;
import org.eclipse.team.ui.mapping.SynchronizationContentProvider;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.ISynchronizeParticipant;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;
import org.eclipse.ui.navigator.INavigatorContentExtension;
import org.eclipse.ui.navigator.INavigatorContentService;
import org.eclipse.ui.navigator.INavigatorSorterService;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.model.ChangeSet.Direction;
import com.vectrace.MercurialEclipse.model.FileFromChangeSet;
import com.vectrace.MercurialEclipse.model.IHgRepositoryLocation;
import com.vectrace.MercurialEclipse.model.PathFromChangeSet;
import com.vectrace.MercurialEclipse.model.UncommittedChangeSet;
import com.vectrace.MercurialEclipse.model.WorkingChangeSet;
import com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants;
import com.vectrace.MercurialEclipse.synchronize.HgDragAdapterAssistant;
import com.vectrace.MercurialEclipse.synchronize.HgSubscriberMergeContext;
import com.vectrace.MercurialEclipse.synchronize.MercurialSynchronizeParticipant;
import com.vectrace.MercurialEclipse.synchronize.MercurialSynchronizeSubscriber;
import com.vectrace.MercurialEclipse.synchronize.PresentationMode;
import com.vectrace.MercurialEclipse.synchronize.RepositorySynchronizationScope.RepositoryLocationMap;
import com.vectrace.MercurialEclipse.team.cache.MercurialStatusCache;
import com.vectrace.MercurialEclipse.utils.ResourceUtils;

@SuppressWarnings("restriction")
public class HgChangeSetContentProvider extends SynchronizationContentProvider {

	public static final String ID = "com.vectrace.MercurialEclipse.changeSetContent";

	private static final MercurialStatusCache STATUS_CACHE = MercurialStatusCache.getInstance();

	private final class UcommittedSetListener implements IPropertyChangeListener {
		public void propertyChange(PropertyChangeEvent event) {
			Object input = getTreeViewer().getInput();
			if (input instanceof HgChangeSetModelProvider) {
				Utils.asyncExec(new Runnable() {
					public void run() {
						TreeViewer treeViewer = getTreeViewer();
						treeViewer.getTree().setRedraw(false);
						treeViewer.refresh(uncommitted, true);

						if (uncommitted instanceof ChangesetGroup) {
							for (ChangeSet cs : ((ChangesetGroup) uncommitted).getChangesets()) {
								treeViewer.refresh(cs, true);
							}
						}
						treeViewer.getTree().setRedraw(true);
					}
				}, getTreeViewer());
			}
		}
	}

	private final class PreferenceListener implements IPropertyChangeListener {
		public void propertyChange(PropertyChangeEvent event) {
			Object input = getTreeViewer().getInput();
			String property = event.getProperty();

			final boolean bLocal = property
					.equals(MercurialPreferenceConstants.PREF_SYNC_ENABLE_LOCAL_CHANGESETS);

			if ((property.equals(PresentationMode.PREFERENCE_KEY) || bLocal
					||	property.equals(MercurialPreferenceConstants.PREF_SYNC_SHOW_EMPTY_GROUPS))
					&& input instanceof HgChangeSetModelProvider) {
				Utils.asyncExec(new Runnable() {
					public void run() {
						TreeViewer treeViewer = getTreeViewer();

						if (bLocal) {
							recreateUncommittedEntry(treeViewer);
						} else {
							treeViewer.getTree().setRedraw(false);
							treeViewer.refresh(uncommitted, true);
							for(RepositoryChangesetGroup sg : projectGroup) {
								treeViewer.refresh(sg.getOutgoing(), true);
								treeViewer.refresh(sg.getIncoming(), true);
							}
							treeViewer.getTree().setRedraw(true);
							treeViewer.refresh(); // TODO: this is unnecessary?
						}
					}
				}, getTreeViewer());
			}
		}
	}

	private final class CollectorListener implements IChangeSetChangeListener, BatchingChangeSetManager.IChangeSetCollectorChangeListener {

		public void setAdded(final org.eclipse.team.internal.core.subscribers.ChangeSet cs) {
			ChangeSet set = (ChangeSet)cs;

			if (isVisibleInMode(set)) {
				final ChangesetGroup toRefresh = findChangeSetInProjects(cs);
				if(toRefresh != null) {
					boolean added = toRefresh.getChangesets().add(set);
					if (added) {
						Utils.asyncExec(new Runnable() {
							public void run() {
								getTreeViewer().refresh(toRefresh, true);
							}
						}, getTreeViewer());
					}
				}
			}
		}

		public void setRemoved(final org.eclipse.team.internal.core.subscribers.ChangeSet cs) {
			ChangeSet set = (ChangeSet)cs;

			if (isVisibleInMode(set)) {
				final ChangesetGroup toRefresh = findChangeSetInProjects(cs);
				boolean removed = toRefresh.getChangesets().remove(set);
				if(removed) {
					Utils.asyncExec(new Runnable() {
						public void run() {
							getTreeViewer().refresh(toRefresh, true);
						}
					}, getTreeViewer());
				}
			}
		}

		private ChangesetGroup findChangeSetInProjects(final org.eclipse.team.internal.core.subscribers.ChangeSet cs) {
			ChangeSet set = (ChangeSet) cs;
			for (RepositoryChangesetGroup sg : projectGroup) {
				if (set.getRepository().equals(sg.getLocation())) {

					if (set.getDirection() == Direction.INCOMING) {
						return sg.getIncoming();
					}
					if (set.getDirection() == Direction.OUTGOING) {
						return sg.getOutgoing();
					}
				}
			}
			return null;
		}

		public void defaultSetChanged(final org.eclipse.team.internal.core.subscribers.ChangeSet previousDefault,
				final org.eclipse.team.internal.core.subscribers.ChangeSet set) {
			// user has requested a manual refresh: simply refresh root elements
			getRootElements();
		}

		public void nameChanged(final org.eclipse.team.internal.core.subscribers.ChangeSet set) {
			// ignored
		}

		public void resourcesChanged(final org.eclipse.team.internal.core.subscribers.ChangeSet set, final IPath[] paths) {
			// ignored
		}

		public void changeSetChanges(final CollectorChangeEvent event, IProgressMonitor monitor) {
			// ignored
		}
	}

	private HgChangesetsCollector csCollector;
	private boolean collectorInitialized;
	private final IChangeSetChangeListener collectorListener;
	private final IPropertyChangeListener uncommittedSetListener;
	private final IPropertyChangeListener preferenceListener;
	private final List<RepositoryChangesetGroup> projectGroup;
	private WorkbenchContentProvider provider;

	private IUncommitted uncommitted;

	public HgChangeSetContentProvider() {
		super();
		projectGroup = new ArrayList<RepositoryChangesetGroup>();
		collectorListener = new CollectorListener();
		uncommittedSetListener = new UcommittedSetListener();
		preferenceListener = new PreferenceListener();
		MercurialEclipsePlugin.getDefault().getPreferenceStore().addPropertyChangeListener(preferenceListener);

		uncommitted = makeUncommittedEntry();
	}

	private static IUncommitted makeUncommittedEntry() {
		if (MercurialEclipsePlugin.getDefault().getPreferenceStore().getBoolean(
				MercurialPreferenceConstants.PREF_SYNC_ENABLE_LOCAL_CHANGESETS)) {
			return new UncommittedChangesetGroup();
		}

		return new UncommittedChangeSet();
	}

	@Override
	protected String getModelProviderId() {
		return HgChangeSetModelProvider.ID;
	}

	private boolean isOutgoingVisible(){
		return getConfiguration().getMode() != ISynchronizePageConfiguration.INCOMING_MODE;
	}

	private boolean isIncomingVisible(){
		return getConfiguration().getMode() != ISynchronizePageConfiguration.OUTGOING_MODE;
	}

	private boolean isEnabled() {
		final Object input = getViewer().getInput();
		return input instanceof HgChangeSetModelProvider;
	}

	@Override
	public Object[] getChildren(Object parent) {
		return getElements(parent);
	}

	@Override
	public Object[] getElements(Object parent) {
		if (parent instanceof ISynchronizationContext) {
			// Do not show change sets when all models are visible because
			// model providers that override the resource content may cause
			// problems for the change set content provider
			return new Object[0];
		}
		if(isEnabled()){
			if(!((HgChangeSetModelProvider) getViewer().getInput()).isParticipantCreated()){
				initCollector();
				// on startup, do not start to show anything for the first time:
				// show "reminder" page which allows user to choose synchronize or not
				// return new Object[0];
				// TODO right now it doesn't make sense to show "reminder page" as we
				// connect to the remote servers automatically as soon as the sync view
				// shows up.
			}
		}

		if (parent == getModelProvider()) {
			return getRootElements();
		} else if (parent instanceof ChangesetGroup) {
			ChangesetGroup group = (ChangesetGroup) parent;
			Direction direction = group.getDirection();
			if (isOutgoing(direction)) {
				if (isOutgoingVisible()) {
					return group.getChangesets().toArray();
				}
				return new Object[] { new FilteredPlaceholder() };
			}
			if(direction == Direction.INCOMING){
				if (isIncomingVisible()) {
					return group.getChangesets().toArray();
				}
				return new Object[] {new FilteredPlaceholder()};
			}
			if(direction == Direction.LOCAL){
				return group.getChangesets().toArray();
			}
		} else if (parent instanceof RepositoryChangesetGroup) {
			// added groups to view
			boolean showEmpty = MercurialEclipsePlugin.getDefault().getPreferenceStore()
					.getBoolean(MercurialPreferenceConstants.PREF_SYNC_SHOW_EMPTY_GROUPS);

			RepositoryChangesetGroup supergroup = (RepositoryChangesetGroup) parent;
			ArrayList<Object> groups =new ArrayList<Object>();
			if(supergroup.getIncoming().getChangesets().size() > 0) {
				groups.add(supergroup.getIncoming());
			} else if(showEmpty) {
				groups.add(supergroup.getIncoming());
			}
			if(supergroup.getOutgoing().getChangesets().size() > 0) {
				groups.add(supergroup.getOutgoing());
			} else if(showEmpty) {
				groups.add(supergroup.getOutgoing());
			}
			return groups.toArray();
		} else if (parent instanceof ChangeSet) {
			FileFromChangeSet[] files = ((ChangeSet) parent).getChangesetFiles();

			if (files.length != 0) {
				switch (PresentationMode.get()) {
				case FLAT:
					return files;
				case TREE:
					return collectTree(parent, files);
				case COMPRESSED_TREE:
					return collectCompressedTree(parent, files);
				}
			}
		} else if (parent instanceof PathFromChangeSet) {
			return ((PathFromChangeSet) parent).getChildren();
		}

		return new Object[0];
	}

	private synchronized void initProjects(MercurialSynchronizeSubscriber subscriber) {
		RepositoryLocationMap locations = subscriber.getParticipant().getRepositoryLocations();

		for (IHgRepositoryLocation repoLocation : locations.getLocations()) {
			RepositoryChangesetGroup scg = new RepositoryChangesetGroup(
					locations.getRoot(repoLocation), repoLocation);
			scg.setIncoming(new ChangesetGroup("Incoming", Direction.INCOMING));
			scg.setOutgoing(new ChangesetGroup("Outgoing", Direction.OUTGOING));

			scg.setProjects(Arrays.asList(locations.getProjects(repoLocation)));
			projectGroup.add(scg);
		}
	}

	private Object[] collectTree(Object parent, FileFromChangeSet[] files) {
		List<Object> out = new ArrayList<Object>(files.length * 2);

		for (FileFromChangeSet file : files) {
			IPath path = file.getPath();
			if(path == null) {
				out.add(Path.EMPTY);
			} else {
				out.add(path.removeLastSegments(1));
			}
			out.add(file);
		}

		return collectTree(parent, out);
	}

	private Object[] collectTree(Object parent, List<Object> files) {
		HashMap<String, List<Object>> map = new HashMap<String, List<Object>>();
		List<Object> out = new ArrayList<Object>();

		for (Iterator<Object> it = files.iterator(); it.hasNext();) {
			IPath path = (IPath) it.next();
			FileFromChangeSet file = (FileFromChangeSet) it.next();

			if (path == null || 0 == path.segmentCount()) {
				out.add(file);
			} else {
				String seg = path.segment(0);
				List<Object> l = map.get(seg);

				if (l == null) {
					map.put(seg, l = new ArrayList<Object>());
				}

				l.add(path.removeFirstSegments(1));
				l.add(file);
			}
		}

		for (String seg : map.keySet()) {
			out.add(new TreePathFromChangeSet(parent, seg, map.get(seg)));
		}

		return out.toArray(new Object[out.size()]);
	}

	private Object[] collectCompressedTree(Object parent, FileFromChangeSet[] files) {
		HashMap<IPath, List<FileFromChangeSet>> map = new HashMap<IPath, List<FileFromChangeSet>>();
		List<Object> out = new ArrayList<Object>();

		for (FileFromChangeSet file : files) {
			IPath path = file.getPath();

			path = (path == null) ? Path.EMPTY : path.removeLastSegments(1);

			List<FileFromChangeSet> l = map.get(path);

			if (l == null) {
				map.put(path, l = new ArrayList<FileFromChangeSet>());
			}

			l.add(file);
		}

		for (IPath path : map.keySet()) {
			final List<FileFromChangeSet> data = map.get(path);
			if (path.isEmpty()) {
				out.addAll(data);
			} else {
				out.add(new CompressedTreePathFromChangeSet(parent, path.toString(), data));
			}
		}

		return out.toArray(new Object[out.size()]);
	}

	private void ensureRootsAdded() {
		TreeViewer viewer = getTreeViewer();
		TreeItem[] children = viewer.getTree().getItems();
		if(children.length == 0) {
			viewer.add(viewer.getInput(), getRootElements());
		}
	}

	// traverse through elements and add them to the correct group
	private Object[] getRootElements() {
		initCollector();
		List<ChangeSet> result = new ArrayList<ChangeSet>();
		Collection<ChangeSet> allSets = getAllSets();
		for (ChangeSet set : allSets) {
			if (hasChildren(set)) {
				result.add(set);
			}
		}
		boolean showOutgoing = isOutgoingVisible();
		boolean showIncoming = isIncomingVisible();
		for (ChangeSet set : result) {
			for (RepositoryChangesetGroup group : projectGroup) {
				if (set.getRepository().equals(group.getLocation())) {
					Direction direction = set.getDirection();
					if (showOutgoing && (isOutgoing(direction))) {
						group.getOutgoing().getChangesets().add(set);
					}
					if (showIncoming && direction == Direction.INCOMING) {
						group.getIncoming().getChangesets().add(set);
					}
				}
			}
		}

		uncommitted.update(STATUS_CACHE, null);

		List<Object> itemsToShow = new ArrayList<Object>();
		itemsToShow.add(uncommitted);

		if (projectGroup.size() == 1) {
			itemsToShow.addAll(Arrays.asList(getElements(projectGroup.get(0))));
		} else {
			itemsToShow.addAll(projectGroup);
		}

		return itemsToShow.toArray();
	}

	private synchronized void initCollector() {
		if (!collectorInitialized) {
			initializeChangeSets(getChangeSetCapability());
			collectorInitialized = true;
		}
	}

	@Override
	protected ResourceTraversal[] getTraversals(
			ISynchronizationContext context, Object object) {
		if (object instanceof ChangeSet) {
			ChangeSet set = (ChangeSet) object;
			IResource[] resources = set.getResources();
			return new ResourceTraversal[] { new ResourceTraversal(resources, IResource.DEPTH_ZERO, IResource.NONE) };
		}
		if(object instanceof IResource){
			IResource[] resources = new IResource[]{(IResource) object};
			return new ResourceTraversal[] { new ResourceTraversal(resources, IResource.DEPTH_ZERO, IResource.NONE) };
		}
		if(object instanceof ChangesetGroup){
			ChangesetGroup group = (ChangesetGroup) object;
			Set<ChangeSet> changesets = group.getChangesets();
			Set<IFile> all = new HashSet<IFile>();
			for (ChangeSet changeSet : changesets) {
				all.addAll(changeSet.getFiles());
			}
			IResource[] resources = all.toArray(new IResource[0]);
			return new ResourceTraversal[] { new ResourceTraversal(resources, IResource.DEPTH_ZERO, IResource.NONE) };
		}
		if(object instanceof RepositoryChangesetGroup){
			RepositoryChangesetGroup supergroup = (RepositoryChangesetGroup) object;
			Set<IFile> all = new HashSet<IFile>();
				Set<ChangeSet> changesets = supergroup.getIncoming().getChangesets();
				for (ChangeSet changeSet : changesets) {
					all.addAll(changeSet.getFiles());
				}
				changesets = supergroup.getOutgoing().getChangesets();
				for (ChangeSet changeSet : changesets) {
					all.addAll(changeSet.getFiles());
				}
			IResource[] resources = all.toArray(new IResource[0]);
			return new ResourceTraversal[] { new ResourceTraversal(resources, IResource.DEPTH_ZERO,	IResource.NONE) };
		}
		return new ResourceTraversal[0];
	}

	/**
	 * @see org.eclipse.team.ui.mapping.SynchronizationContentProvider#hasChildrenInContext(org.eclipse.team.core.mapping.ISynchronizationContext,
	 *      java.lang.Object)
	 */
	@Override
	protected boolean hasChildrenInContext(ISynchronizationContext context, Object element) {
		if (element instanceof ChangeSet) {
			return hasChildren((ChangeSet) element);
		} else if (element instanceof ChangesetGroup) {
			ChangesetGroup group = (ChangesetGroup) element;
			Direction direction = group.getDirection();
			if (isOutgoingVisible()	&& isOutgoing(direction)) {
				return true;
			}
			if(isIncomingVisible() && direction == Direction.INCOMING){
				return true;
			}
		} else if (element instanceof RepositoryChangesetGroup) {
			boolean showEmptyGroups =  MercurialEclipsePlugin.getDefault().getPreferenceStore().getBoolean(MercurialPreferenceConstants.PREF_SYNC_SHOW_EMPTY_GROUPS);
			RepositoryChangesetGroup supergroup = (RepositoryChangesetGroup) element;
			if (isOutgoingVisible() && (showEmptyGroups || supergroup.getOutgoing().getChangesets().size() > 0)) {
				return true;
			}
			if (isIncomingVisible() && (showEmptyGroups || supergroup.getIncoming().getChangesets().size() > 0)) {
				return true;
			}
		} else if (element instanceof PathFromChangeSet) {
			return true;
		}
		return false;
	}

	private boolean isVisibleInMode(ChangeSet cs) {
		int mode = getConfiguration().getMode();
		if (cs != null) {
			switch (mode) {
			case ISynchronizePageConfiguration.BOTH_MODE:
				return true;
			case ISynchronizePageConfiguration.CONFLICTING_MODE:
				return containsConflicts(cs);
			case ISynchronizePageConfiguration.INCOMING_MODE:
				return  cs.getDirection() == Direction.INCOMING;
			case ISynchronizePageConfiguration.OUTGOING_MODE:
				return hasConflicts(cs) || isOutgoing(cs.getDirection());
			default:
				break;
			}
		}
		return true;
	}

	private static boolean isOutgoing(Direction direction) {
		return direction == Direction.OUTGOING || direction == Direction.LOCAL;
	}

	private static boolean hasConflicts(ChangeSet cs) {
		// Conflict mode not meaningful in a DVCS
		return false;
	}

	private static boolean containsConflicts(ChangeSet cs) {
		// Conflict mode not meaningful in a DVCS
		return false;
	}

	private boolean hasChildren(ChangeSet changeset) {
		return isVisibleInMode(changeset)
				&& (!changeset.getFiles().isEmpty() || changeset.getChangesetFiles().length > 0);
	}

	/**
	 * Return all the change sets (incoming and outgoing). This
	 * list must not include the unassigned set.
	 * @return all the change sets (incoming and outgoing)
	 */
	private Collection<ChangeSet> getAllSets() {
		if (csCollector != null) {
			return csCollector.getChangeSets();
		}
		return new HashSet<ChangeSet>();
	}

	@Override
	public void init(ICommonContentExtensionSite site) {
		super.init(site);
		HgChangeSetSorter sorter = getSorter();
		if (sorter != null) {
			sorter.setConfiguration(getConfiguration());
		}
		MercurialSynchronizeParticipant participant = (MercurialSynchronizeParticipant) getConfiguration().getParticipant();
		getExtensionSite().getService().getDnDService().bindDragAssistant(new HgDragAdapterAssistant());
		uncommitted.setContext((HgSubscriberMergeContext) participant.getContext());
	}

	private HgChangeSetSorter getSorter() {
		INavigatorContentService contentService = getExtensionSite().getService();
		INavigatorSorterService sortingService = contentService.getSorterService();
		INavigatorContentExtension extension = getExtensionSite().getExtension();
		if (extension != null) {
			ViewerSorter sorter = sortingService.findSorter(extension.getDescriptor(),
					getModelProvider(), null, null); // incoming, incoming
					// TODO: sorting
			if (sorter instanceof HgChangeSetSorter) {
				return (HgChangeSetSorter) sorter;
			}
		}
		return null;
	}

	private void initializeChangeSets(ChangeSetCapability csc) {
		if (csc.supportsCheckedInChangeSets()) {
			csCollector = ((HgChangeSetCapability) csc).createSyncInfoSetChangeSetCollector(getConfiguration());
			csCollector.addListener(collectorListener);
			initializeUncommittedEntry(csCollector.getSubscriber().getProjects());
			initProjects(csCollector.getSubscriber());
		}
	}

	private void initializeUncommittedEntry(IProject[] projects) {
		uncommitted.setProjects(projects);
		uncommitted.addListener(uncommittedSetListener);
		STATUS_CACHE.addObserver(uncommitted);
	}

	@Override
	public void dispose() {
		if (csCollector != null) {
			csCollector.removeListener(collectorListener);
			csCollector.dispose();
		}
		MercurialEclipsePlugin.getDefault().getPreferenceStore().removePropertyChangeListener(preferenceListener);
		disposeUncommittedEntry();
		for(RepositoryChangesetGroup sg : projectGroup) {
			sg.getOutgoing().getChangesets().clear();
			sg.getIncoming().getChangesets().clear();
		}
		projectGroup.clear();
		super.dispose();
	}

	private void disposeUncommittedEntry() {
		uncommitted.removeListener(uncommittedSetListener);
		STATUS_CACHE.deleteObserver(uncommitted);
		uncommitted.dispose();
		uncommitted = null;
	}

	protected void recreateUncommittedEntry(TreeViewer tree) {
		IProject[] projects = uncommitted.getProjects();
		HgSubscriberMergeContext ctx = uncommitted.getContext();

		disposeUncommittedEntry();
		uncommitted = makeUncommittedEntry();
		uncommitted.setContext(ctx);

		if (collectorInitialized) {
			initializeUncommittedEntry(projects);
		}

		tree.refresh();
	}

	@Override
	public void diffsChanged(IDiffChangeEvent event, IProgressMonitor monitor) {
		Utils.asyncExec(new Runnable() {
			public void run() {
				ensureRootsAdded();
			}
		}, getTreeViewer());

		if (csCollector != null) {
			csCollector.handleChange(event);
		}

		// no other updates here, as it simply doesn't fit into the changeset concept.
	}

	private ChangeSetCapability getChangeSetCapability() {
		ISynchronizeParticipant participant = getConfiguration().getParticipant();
		if (participant instanceof IChangeSetProvider) {
			IChangeSetProvider csProvider = (IChangeSetProvider) participant;
			return csProvider.getChangeSetCapability();
		}
		return null;
	}

	private TreeViewer getTreeViewer() {
		return (TreeViewer) getViewer();
	}

	@Override
	protected ITreeContentProvider getDelegateContentProvider() {
		if (provider == null) {
			provider = new WorkbenchContentProvider();
		}
		return provider;
	}

	@Override
	protected Object getModelRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	/**
	 *
	 * @param file may be null
	 * @return may return null, if the given file is null, not selected or is not contained
	 * in any selected changesets
	 */
	public ChangeSet getParentOfSelection(FileFromChangeSet file){
		TreeItem[] selection = getTreeViewer().getTree().getSelection();
		for (TreeItem treeItem : selection) {
			if(treeItem.getData() != file){
				continue;
			}
			TreeItem parentItem = treeItem.getParentItem();

			while (parentItem != null && !(parentItem.getData() instanceof ChangeSet)) {
				parentItem = parentItem.getParentItem();
			}

			if (parentItem != null) {
				return (ChangeSet) parentItem.getData();
			}
		}
		return null;
	}

	/**
	 * @param changeset may be null
	 * @return may return null
	 */
	public ChangesetGroup getParentGroup(ChangeSet changeset){
		if(changeset == null || changeset instanceof WorkingChangeSet){
			return null;
		}
		boolean incoming = changeset.getDirection() == Direction.INCOMING;

		for (RepositoryChangesetGroup group : projectGroup) {
			ChangesetGroup g = incoming ? group.getIncoming() : group.getOutgoing();

			if (g.getChangesets().contains(changeset)) {
				return g;
			}
		}

		return null;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("HgChangeSetContentProvider [collectorInitialized=");
		builder.append(collectorInitialized);
		builder.append(", ");
		if (csCollector != null) {
			builder.append("csCollector=");
			builder.append(csCollector);
			builder.append(", ");
		}
		if (provider != null) {
			builder.append("provider=");
			builder.append(provider);
			builder.append(", ");
		}
		builder.append("]");
		return builder.toString();
	}

	public IUncommitted getUncommittedEntry() {
		return uncommitted;
	}

	private class CompressedTreePathFromChangeSet extends PathFromChangeSet {

		protected final List<FileFromChangeSet> data;

		public CompressedTreePathFromChangeSet(Object prnt, String seg, List<FileFromChangeSet> data) {
			super(prnt, seg);
			this.data = data;

			if (data != null && data.size() > 0) {
				IFile file = data.get(0).getFile();
				this.resource = file != null? file.getParent() : null;
			}
		}

		@Override
		public Object[] getChildren() {
			return data.toArray(new FileFromChangeSet[data.size()]);
		}

		@Override
		public Set<FileFromChangeSet> getFiles() {
			return new LinkedHashSet<FileFromChangeSet>(data);
		}
	}

	private class TreePathFromChangeSet extends PathFromChangeSet {

		protected final List<Object> data;

		public TreePathFromChangeSet(Object prnt, String seg, List<Object> data) {
			super(prnt, seg);
			this.data = data;
			this.resource = getResource();
		}

		@Override
		public Object[] getChildren() {
			return collectTree(HgChangeSetContentProvider.this, data);
		}

		private IResource getResource() {
			IResource result = null;
			if (data.size() > 1) {
				// first object must be an IPath
				Object o1 = data.get(0);
				// and second must be FileFromChangeSet
				Object o2 = data.get(1);
				if (o1 instanceof IPath && o2 instanceof FileFromChangeSet) {
					FileFromChangeSet fcs = (FileFromChangeSet) o2;
					IResource childResource = ResourceUtils.getResource(fcs);
					if(childResource != null) {
						IPath folderPath = ResourceUtils.getPath(childResource).removeLastSegments(
								((IPath) o1).segmentCount() + 1);
						return ResourcesPlugin.getWorkspace().getRoot().getContainerForLocation(
								folderPath);
					}
				}
			}
			return result;
		}

		@Override
		public Set<FileFromChangeSet> getFiles() {
			Set<FileFromChangeSet> files = new LinkedHashSet<FileFromChangeSet>();
			for (Object o : data) {
				if(o instanceof FileFromChangeSet) {
					files.add((FileFromChangeSet) o);
				}
			}
			return files;
		}
	}

	protected static class FilteredPlaceholder
	{
		/**
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "<Filtered>";
		}
	}

	public interface IUncommitted extends Observer
	{
		void setProjects(IProject[] projects);

		IProject[] getProjects();

		void dispose();

		void removeListener(IPropertyChangeListener listener);

		void addListener(IPropertyChangeListener listener);

		void setContext(HgSubscriberMergeContext context);

		HgSubscriberMergeContext getContext();
	}
}
