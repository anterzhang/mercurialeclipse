/*******************************************************************************
 * Copyright (c) 2005-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Bastian Doetsch	implementation
 * Andrei Loskutov - bugfixes
 * Adam Berkes (Intland) - bugfixes
 *******************************************************************************/
package com.vectrace.MercurialEclipse.synchronize;

import static com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants.SYNC_COMPUTE_FULL_REMOTE_FILE_STATUS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.diff.IDiff;
import org.eclipse.team.core.subscribers.ISubscriberChangeEvent;
import org.eclipse.team.core.subscribers.Subscriber;
import org.eclipse.team.core.subscribers.SubscriberChangeEvent;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.core.variants.IResourceVariant;
import org.eclipse.team.core.variants.IResourceVariantComparator;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.compare.RevisionNode;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.model.HgFile;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.model.IHgRepositoryLocation;
import com.vectrace.MercurialEclipse.model.JHgChangeSet;
import com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants;
import com.vectrace.MercurialEclipse.synchronize.cs.HgChangesetsCollector;
import com.vectrace.MercurialEclipse.team.MercurialTeamProvider;
import com.vectrace.MercurialEclipse.team.cache.IncomingChangesetCache;
import com.vectrace.MercurialEclipse.team.cache.LocalChangesetCache;
import com.vectrace.MercurialEclipse.team.cache.MercurialStatusCache;
import com.vectrace.MercurialEclipse.team.cache.OutgoingChangesetCache;
import com.vectrace.MercurialEclipse.utils.Bits;
import com.vectrace.MercurialEclipse.utils.BranchUtils;
import com.vectrace.MercurialEclipse.utils.ResourceUtils;

public class MercurialSynchronizeSubscriber extends Subscriber /*implements Observer*/ {

	private static final LocalChangesetCache LOCAL_CACHE = LocalChangesetCache.getInstance();

	private static final IncomingChangesetCache INCOMING_CACHE = IncomingChangesetCache.getInstance();

	private static final OutgoingChangesetCache OUTGOING_CACHE = OutgoingChangesetCache.getInstance();

	private static final MercurialStatusCache STATUS_CACHE = MercurialStatusCache.getInstance();

	private static final boolean DEBUG = MercurialEclipsePlugin.getDefault().isDebugging();
	private static final IResourceVariantComparator COMPARATOR = new MercurialResourceVariantComparator();
	private static final Semaphore CACHE_SEMA = new Semaphore(1, true);

	private final RepositorySynchronizationScope scope;

	private ISubscriberChangeEvent[] lastEvents;

	private MercurialSynchronizeParticipant participant;

	private HgChangesetsCollector collector;
	private boolean computeFullState;

	public MercurialSynchronizeSubscriber(RepositorySynchronizationScope synchronizationScope) {
		Assert.isNotNull(synchronizationScope);
		scope = synchronizationScope;
		synchronizationScope.setSubscriber(this);
		final IPreferenceStore store = MercurialEclipsePlugin.getDefault().getPreferenceStore();
		computeFullState = store.getBoolean(SYNC_COMPUTE_FULL_REMOTE_FILE_STATUS);
		store.addPropertyChangeListener(new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if(SYNC_COMPUTE_FULL_REMOTE_FILE_STATUS.equals(event.getProperty())){
					computeFullState = store.getBoolean(SYNC_COMPUTE_FULL_REMOTE_FILE_STATUS);
				}
			}
		});
	}

	@Override
	public String getName() {
		return Messages.getString("MercurialSynchronizeSubscriber.repoWatcher"); //$NON-NLS-1$
	}

	@Override
	public IResourceVariantComparator getResourceComparator() {
		return COMPARATOR;
	}

	@Override
	public SyncInfo getSyncInfo(final IResource resource) {
		if (!isInteresting(resource)) {
			return null;
		}
		IFile file = (IFile) resource;
		HgRoot root = MercurialTeamProvider.hasHgRoot(file);
		if(root == null){
			return null;
		}
		String syncBranch = getSyncBranch(root);
		IHgRepositoryLocation repo = scope.getRepositoryLocation(resource);

		if (repo == null) {
			MercurialEclipsePlugin.logWarning("Could not find repo location for resource \""
					+ resource + "\". Scope=" + scope.toString(), new NullPointerException());

			return null;
		}

		if(computeFullState) {
			return getSyncInfo(file, root, syncBranch, repo);
		}
		return getFastSyncInfo(file, root, syncBranch, repo);
	}

	static SyncInfo getSyncInfo(IFile file, HgRoot root, String currentBranch, IHgRepositoryLocation repo) {
		JHgChangeSet csOutgoing = getNewestOutgoing(file, currentBranch, repo);
		HgFile outgoingIStorage;
		IResourceVariant outgoing;
		// determine outgoing revision
		boolean hasOutgoingChanges = false;
		boolean hasIncomingChanges = false;
		Integer status = STATUS_CACHE.getStatus(file);
		int sMask = status != null? status.intValue() : 0;
		if (csOutgoing != null) {
			outgoingIStorage = HgFile.make(csOutgoing, file);

			outgoing = new MercurialResourceVariant(new RevisionNode(outgoingIStorage));
			hasOutgoingChanges = true;
		} else {
			boolean exists = file.exists();
			// if outgoing != null it's our base, else we gotta construct one
			if (exists && !Bits.contains(sMask, MercurialStatusCache.BIT_ADDED)
					// XXX Probably we do not need to check for BIT_REMOVED
					// || (!exists && Bits.contains(sMask, MercurialStatusCache.BIT_REMOVED))
					|| !exists) {

				try {
					// try to get from cache (without loading)
					csOutgoing = LOCAL_CACHE.getCurrentChangeSet(root);
				} catch (HgException e) {
					MercurialEclipsePlugin.logError(e);
					return null;
				}

				if(csOutgoing == null || !BranchUtils.same(csOutgoing.getBranch(), currentBranch)){
					return null;
				}
				// construct base revision
				outgoingIStorage = HgFile.make(csOutgoing, file);

				outgoing = new MercurialResourceVariant(new RevisionNode(outgoingIStorage));
			} else {
				// new incoming file - no local available
				outgoingIStorage = null;
				outgoing = null;
			}
		}

		// determine incoming revision get newest incoming changeset
		JHgChangeSet csIncoming = getNewestIncoming(file, currentBranch, repo);
		HgFile incomingIStorage;
		int syncMode = -1;
		if (csIncoming != null) {
			hasIncomingChanges = true;
			boolean fileRemoved = csIncoming.isRemoved(file);
			if(fileRemoved){
				incomingIStorage = null;
			} else {
				incomingIStorage = getIncomingIStorage(file, csIncoming);
			}
		} else {
			if(!hasOutgoingChanges && Bits.contains(sMask, MercurialStatusCache.BIT_CLEAN)){
				return null;
			}
			if(DEBUG) {
				System.out.println("Visiting: " + file);
			}
			// if no incoming revision, incoming = base/outgoing

			// TODO it seems that the line below causes NO DIFF shown if the outgoing
			// change consists from MULTIPLE changes on same file, see issue 10486
			// we have to get the parent of the first outgoing changeset on a given file here
			incomingIStorage = outgoingIStorage;

			// TODO validate if code below fixes the issue 10486
			try {
				SortedSet<JHgChangeSet> sets = OUTGOING_CACHE.hasChangeSets(file, repo, currentBranch);
				int size = sets.size();

				// case where we have one outgoung changeset AND one not committed change
				if(size == 1 && !Bits.contains(sMask, MercurialStatusCache.BIT_CLEAN)){
					size++;
				}
				if(size > 1){
					ChangeSet first = sets.first();
					String[] parents = first.getParents();
					String parentCs = null;
					if(parents.length > 0){
						parentCs = parents[0];
					} else {
						ChangeSet tmpCs = LOCAL_CACHE.get(root, first.getNode());
						if(tmpCs != null && tmpCs.getParents().length > 0){
							parentCs = tmpCs.getParents()[0];
						}
					}
					if(parentCs != null){
						JHgChangeSet baseChangeset = LOCAL_CACHE.get(root, parentCs);
						incomingIStorage = getIncomingIStorage(file, baseChangeset);
						// we change outgoing (base) to the first parent of the first outgoing changeset
						outgoing = new MercurialResourceVariant(new RevisionNode(incomingIStorage));
						syncMode = SyncInfo.OUTGOING | SyncInfo.CHANGE;
					}
				}
			} catch (HgException e) {
				MercurialEclipsePlugin.logError(e);
			}
		}

		if(!hasIncomingChanges && !hasOutgoingChanges && Bits.contains(sMask, MercurialStatusCache.BIT_CLEAN)){
			return null;
		}
		IResourceVariant incoming;
		if (incomingIStorage != null) {
			incoming = new MercurialResourceVariant(new RevisionNode(incomingIStorage));
		} else {
			// neither base nor outgoing nor incoming revision
			incoming = null;
		}

		// now create the sync info object. everything may be null,
		// but resource and comparator
		SyncInfo info = new MercurialSyncInfo(file, outgoing, incoming, COMPARATOR, syncMode);

		try {
			info.init();
			return info;
		} catch (CoreException e) {
			MercurialEclipsePlugin.logError(e);
			return null;
		}
	}

	public static void executeLockedCacheTask(Runnable run) throws InterruptedException {
		if(!CACHE_SEMA.tryAcquire(60 * 10, TimeUnit.SECONDS)){
			// waiting didn't worked for us...
			throw new InterruptedException("Timeout elapsed");
		}
		try {
			run.run();
		} catch (Exception e) {
			MercurialEclipsePlugin.logError(e);
			throw new InterruptedException("Cancelled due the exception: " + e.getMessage());
		} finally {
			CACHE_SEMA.release();
		}
	}

	private static JHgChangeSet getNewestOutgoing(IFile file, String currentBranch,
			IHgRepositoryLocation repo) {
		JHgChangeSet csOutgoing = null;

		SortedSet<JHgChangeSet> changeSets = OUTGOING_CACHE.hasChangeSets(file, repo, currentBranch);
		if (!changeSets.isEmpty()) {
			csOutgoing = changeSets.last();
		}

		return csOutgoing;
	}

	private static JHgChangeSet getNewestIncoming(IFile file, String currentBranch,
			IHgRepositoryLocation repo) {
		JHgChangeSet csIncoming = null;
		SortedSet<JHgChangeSet> changeSets = INCOMING_CACHE.hasChangeSets(file, repo, currentBranch);
		if (!changeSets.isEmpty()) {
			csIncoming = changeSets.last();
		}
		return csIncoming;
	}

	/**
	 * Avoid calculation of added/removed states, as this costs us a HUGE performance
	 * overhead for bigger repositories of many changesets (>1000) at once (see issue #10646).
	 *
	 * The expected performance improvement is about 5 to 10 times for repositories with
	 * more then 50000 files. As a drawback, some diff info is lost in the "tree" view,
	 * and the "compare to" action doesn't always deliver expected results.
	 *
	 * @return a simplified sync info object, which doesn't know if it was added or removed.
	 */
	// TODO calculation of the status flags need to be reviewed. Right now it has a prototype quality
	private static SyncInfo getFastSyncInfo(IFile file, HgRoot root, String currentBranch, IHgRepositoryLocation repo) {
		Integer status = STATUS_CACHE.getStatus(file);
		int sMask = status != null? status.intValue() : 0;
		boolean changedLocal = !Bits.contains(sMask, MercurialStatusCache.BIT_CLEAN);
		SortedSet<JHgChangeSet> changeSets = null;
		if(!changedLocal){
			changeSets = OUTGOING_CACHE.hasChangeSets(file, repo, currentBranch);
			changedLocal = hasChanges(file, changeSets);
		}

		changeSets = INCOMING_CACHE.hasChangeSets(file, repo, currentBranch);

		boolean changedRemote = hasChanges(file, changeSets);
		if(!changedLocal && !changedRemote){
			return null;
		}
		int flags;
		if (changedLocal && changedRemote) {
			flags = SyncInfo.CONFLICTING;
		} else {
			flags = SyncInfo.CHANGE;
			flags |= changedLocal ? SyncInfo.OUTGOING : SyncInfo.INCOMING;
		}

		return new DelayedSyncInfo(file, root, currentBranch, repo, COMPARATOR, flags);
	}

	@Override
	public IDiff getDiff(IResource resource) throws CoreException {
		SyncInfo info = getSyncInfo(resource);
		if (info == null || info.getKind() == SyncInfo.IN_SYNC) {
			return null;
		}
		if(computeFullState) {
			return super.getDiff(resource);
		}
		return ((DelayedSyncInfo) info).getDiff();
	}

	private static boolean hasChanges(IFile file, SortedSet<JHgChangeSet> changeSets) {
		if(changeSets == null || changeSets.isEmpty()){
			return false;
		}
		for (ChangeSet cs : changeSets) {
			if(cs.contains(file)){
				return true;
			}
		}
		return false;
	}

	private boolean isInteresting(IResource resource) {
		return resource instanceof IFile
				&& MercurialTeamProvider.isHgTeamProviderFor(resource.getProject())
				&& (isSupervised(resource) || (!resource.exists()));
	}

	private static HgFile getIncomingIStorage(IFile resource, JHgChangeSet csRemote) {
		return HgFile.make(csRemote, resource);
	}

	@Override
	public boolean isSupervised(IResource resource) {
		boolean result = resource.getType() == IResource.FILE && !resource.isTeamPrivateMember()
			/* && MercurialUtilities.isPossiblySupervised(resource)*/;
		if(!result){
			return false;
		}
		// fix for issue 10153: Resources ignored in .hgignore are still shown in Synchronize view
		if(STATUS_CACHE.isIgnored(resource)){
			return false;
		}
		return true;
	}

	@Override
	public IResource[] members(IResource resource) throws TeamException {
		return new IResource[0];
	}

	/**
	 * @param flag one of {@link HgSubscriberScopeManager} constants, if the value is negative,
	 * otherwise some depth hints from the Team API (which are ignored here).
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public void refresh(IResource[] resources, int flag, IProgressMonitor monitor) throws TeamException {
		if (resources == null) {
			return;
		}

		List<IResource> resources2 = Arrays.asList(resources);
		Map<IProject, List<IResource>> byProject = ResourceUtils.groupByProject(resources2);
		Set<IProject> projects = byProject.keySet();
		if(projects.isEmpty()){
			return;
		}

		Map<HgRoot, List<IResource>> byRoot = ResourceUtils.groupByRoot(new ArrayList<IResource>(byProject.keySet()));

		// we need to send events only if WE trigger status update, not if the refresh
		// is called from the framework (like F5 hit by user)
		Set<IResource> resourcesToRefresh;
		if(flag < 0){
			resourcesToRefresh = new HashSet<IResource>();
		} else {
			// no need
			resourcesToRefresh = null;
		}


		Set<HgRoot> roots = byRoot.keySet();
		try {
			CACHE_SEMA.acquire();
			for (HgRoot hgRoot : roots) {
				LOCAL_CACHE.checkWorkingDirectoryParent(hgRoot, null);
				if (flag == HgSubscriberScopeManager.INCOMING || flag >= 0) {
					if (DEBUG) {
						System.out.println("clear incoming: " + hgRoot + ", depth: " + flag);
					}
					INCOMING_CACHE.clear(hgRoot, false);
				}
				if(flag == HgSubscriberScopeManager.OUTGOING || flag >= 0) {
					if(DEBUG) {
						System.out.println("clear outgoing: " + hgRoot + ", depth: " + flag);
					}
					OUTGOING_CACHE.clear(hgRoot, false);
				}
				if(flag == HgSubscriberScopeManager.LOCAL || flag >= 0) {
					if(DEBUG) {
						System.out.println("clear and refresh local: " + hgRoot + ", depth: " + flag);
					}
					STATUS_CACHE.clear(hgRoot, false);
					STATUS_CACHE.refreshStatus(hgRoot, monitor);
				}
			}
		} catch (InterruptedException e) {
			MercurialEclipsePlugin.logError(e);
		} finally {
			CACHE_SEMA.release();
		}

		for (IProject project : projects) {
			IHgRepositoryLocation repositoryLocation = getScope().getRepositoryLocation(project);

			if (repositoryLocation == null) {
				continue;
			}
			// clear caches in any case, but refresh them only if project exists
			HgRoot hgRoot = MercurialTeamProvider.getHgRoot(project);

			if(repositoryLocation.isLocal() && hgRoot.equals(repositoryLocation)) {
				continue;
			}
			monitor.beginTask(getName(), 2);
			boolean forceRefresh = project.exists();
			String syncBranch = getSyncBranch(hgRoot);

			try {
				CACHE_SEMA.acquire();
				if(DEBUG) {
					System.out.println("going to refresh local/in/out: " + project + ", depth: " + flag);
				}
				if (monitor.isCanceled()) {
					return;
				}
				monitor.subTask(Messages.getString("MercurialSynchronizeSubscriber.refreshingOutgoing")); //$NON-NLS-1$
				refreshOutgoing(flag, resourcesToRefresh, project, repositoryLocation, forceRefresh, syncBranch);
				monitor.worked(1);
				if (monitor.isCanceled()) {
					return;
				}
				monitor.subTask(Messages.getString("MercurialSynchronizeSubscriber.refreshingIncoming")); //$NON-NLS-1$
				refreshIncoming(flag, resourcesToRefresh, project, repositoryLocation, forceRefresh, syncBranch);
				monitor.worked(1);
				if (monitor.isCanceled()) {
					return;
				}
			} catch (HgException e) {
				throw new TeamException(new Status(IStatus.INFO, MercurialEclipsePlugin.ID,
						Messages.getString("MercurialSynchronizeSubscriber.connectError")));
			} catch (InterruptedException e) {
				MercurialEclipsePlugin.logError(e);
			} finally {
				CACHE_SEMA.release();
			}

		// we need to send events only if WE trigger status update, not if the refresh
		// is called from the framework (like F5 hit by user)
		if(resourcesToRefresh != null){
			List<ISubscriberChangeEvent> changeEvents = createEvents(resources, resourcesToRefresh);
			monitor.worked(1);
			if (monitor.isCanceled()) {
				return;
			}

			monitor.subTask(Messages.getString("MercurialSynchronizeSubscriber.triggeringStatusCalc")); //$NON-NLS-1$
			lastEvents = changeEvents.toArray(new ISubscriberChangeEvent[changeEvents.size()]);
			fireTeamResourceChange(lastEvents);
			monitor.worked(1);
		}
		monitor.done();
		}
	}

	private List<ISubscriberChangeEvent> createEvents(IResource[] resources,
			Set<IResource> resourcesToRefresh) {
		for (IResource resource : resources) {
			if(resource.getType() == IResource.FILE) {
				resourcesToRefresh.add(resource);
			} else {
				Set<IResource> localMembers = STATUS_CACHE.getLocalMembers(resource);
				resourcesToRefresh.addAll(localMembers);
			}
		}
		List<ISubscriberChangeEvent> changeEvents = new ArrayList<ISubscriberChangeEvent>();
		for (IResource res : resourcesToRefresh) {
			changeEvents.add(new SubscriberChangeEvent(this, ISubscriberChangeEvent.SYNC_CHANGED, res));
		}
		if(DEBUG) {
			System.out.println("created: " + changeEvents.size() + " change events");
		}
		return changeEvents;
	}

	private static void refreshIncoming(int flag, Set<IResource> resourcesToRefresh, IProject project,
			IHgRepositoryLocation repositoryLocation, boolean forceRefresh, String branch) throws HgException {

		if(forceRefresh && flag != HgSubscriberScopeManager.OUTGOING){
			if(DEBUG) {
				System.out.println("\nget incoming: " + project + ", depth: " + flag);
			}

			// this can trigger a refresh and a call to the remote server...
			if(resourcesToRefresh != null){
				Set<IResource> incomingMembers = INCOMING_CACHE.getMembers(project, repositoryLocation, branch);
				resourcesToRefresh.addAll(incomingMembers);
			} else {
				INCOMING_CACHE.getChangeSets(project, repositoryLocation, branch);
			}
		}
	}

	private static void refreshOutgoing(int flag, Set<IResource> resourcesToRefresh, IProject project,
			IHgRepositoryLocation repositoryLocation, boolean forceRefresh, String branch) throws HgException {

		if(forceRefresh && flag != HgSubscriberScopeManager.INCOMING){
			if(DEBUG) {
				System.out.println("get outgoing: " + project + ", depth: " + flag);
			}
			// this can trigger a refresh and a call to the remote server...
			if(resourcesToRefresh != null){
				Set<IResource> outgoingMembers = OUTGOING_CACHE.getMembers(project, repositoryLocation, branch);
				resourcesToRefresh.addAll(outgoingMembers);
			} else {
				OUTGOING_CACHE.getChangeSets(project, repositoryLocation, branch);
			}
		}
	}

	public RepositorySynchronizationScope getScope() {
		return scope;
	}

	public IProject[] getProjects() {
		return scope.getProjects();
	}

	@Override
	public IResource[] roots() {
		return scope.getRoots();
	}

	public void branchChanged(final HgRoot hgRoot){
		IResource[] roots = roots();
		boolean related = false;
		for (IResource resource : roots) {
			if(hgRoot.equals(MercurialTeamProvider.hasHgRoot(resource))){
				related = true;
				break;
			}
		}
		if(!related){
			return;
		}
		Job job = new Job("Updating branch info for " + hgRoot.getName()){
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				LOCAL_CACHE.checkWorkingDirectoryParent(hgRoot, null);
				if(lastEvents != null) {
					fireTeamResourceChange(lastEvents);
				}
				return Status.OK_STATUS;
			}
		};
		job.schedule(100);
	}

	/**
	 * Overriden to made it accessible from {@link HgSubscriberScopeManager#update(java.util.Observable, Object)}
	 * {@inheritDoc}
	 */
	@Override
	public void fireTeamResourceChange(ISubscriberChangeEvent[] deltas) {
		super.fireTeamResourceChange(deltas);
		if(collector != null) {
			collector.refresh(null);
		}
	}

	public void setParticipant(MercurialSynchronizeParticipant participant){
		this.participant = participant;
	}

	public MercurialSynchronizeParticipant getParticipant() {
		return participant;
	}

	public void setCollector(HgChangesetsCollector collector){
		this.collector = collector;
	}

	public HgChangesetsCollector getCollector() {
		return collector;
	}

	/**
	 * @return The branch name to synchronize, or null to synchronize all branches
	 */
	public static String getSyncBranch(HgRoot hgRoot) {
		boolean syncCurBranch = MercurialEclipsePlugin.getDefault().getPreferenceStore().getBoolean(MercurialPreferenceConstants.PREF_SYNC_ONLY_CURRENT_BRANCH);

		return syncCurBranch ? MercurialTeamProvider.getCurrentBranch(hgRoot) : null;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("MercurialSynchronizeSubscriber [");
		if (collector != null) {
			builder.append("collector=");
			builder.append(collector);
			builder.append(", ");
		}
		if (participant != null) {
			builder.append("participant=");
			builder.append(participant);
			builder.append(", ");
		}
		if (scope != null) {
			builder.append("scope=");
			builder.append(scope);
		}
		builder.append("]");
		return builder.toString();
	}
}
