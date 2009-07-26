/*******************************************************************************
 * Copyright (c) 2005-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VecTrace (Zingo Andersen) - implementation
 *     Software Balm Consulting Inc (Peter Hunnisett <peter_hge at softwarebalm dot com>) - some updates
 *     StefanC                   - large contribution
 *     Jerome Negre              - fixing folders' state
 *     Bastian Doetsch	         - extraction from DecoratorStatus + additional methods
 *******************************************************************************/
package com.vectrace.MercurialEclipse.team.cache;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.Team;
import org.eclipse.team.core.TeamException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.SafeWorkspaceJob;
import com.vectrace.MercurialEclipse.commands.AbstractClient;
import com.vectrace.MercurialEclipse.commands.HgClients;
import com.vectrace.MercurialEclipse.commands.HgResolveClient;
import com.vectrace.MercurialEclipse.commands.HgStatusClient;
import com.vectrace.MercurialEclipse.commands.extensions.HgIMergeClient;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.FlaggedAdaptable;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.operations.InitOperation;
import com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants;
import com.vectrace.MercurialEclipse.team.MercurialTeamProvider;
import com.vectrace.MercurialEclipse.team.MercurialUtilities;
import com.vectrace.MercurialEclipse.team.ResourceProperties;

/**
 * Caches the Mercurial Status of each file and offers methods for retrieving, clearing and refreshing repository state.
 *
 * @author Bastian Doetsch
 *
 */
public class MercurialStatusCache extends AbstractCache implements IResourceChangeListener {

    private static final int STATUS_BATCH_SIZE = 10;
    private static final int NUM_CHANGED_FOR_COMPLETE_STATUS = 50;

    /**
     * @author bastian
     *
     */
    private final class ResourceDeltaVisitor implements IResourceDeltaVisitor {

        private final Map<IProject, Set<IResource>> removed;
        private final Map<IProject, Set<IResource>> changed;
        private final Map<IProject, Set<IResource>> added;
        private final boolean completeStatus;
        private final boolean autoShare;

        /**
         * @param removed
         * @param changed
         * @param added
         */
        private ResourceDeltaVisitor(Map<IProject, Set<IResource>> removed, Map<IProject, Set<IResource>> changed,
                Map<IProject, Set<IResource>> added) {
            this.removed = removed;
            this.changed = changed;
            this.added = added;

            completeStatus = Boolean
            .valueOf(
                    HgClients.getPreference(MercurialPreferenceConstants.RESOURCE_DECORATOR_COMPLETE_STATUS,
                    "false")).booleanValue(); //$NON-NLS-1$
            autoShare = Boolean.valueOf(
                    HgClients.getPreference(MercurialPreferenceConstants.PREF_AUTO_SHARE_PROJECTS, "false"))
                    .booleanValue();
        }

        private IResource getResource(IResource res) {
            IResource myRes = res;
            if (completeStatus) {
                myRes = res.getProject();
            }
            return myRes;
        }

        public boolean visit(IResourceDelta delta) throws CoreException {
            IResource res = delta.getResource();
            // System.out.println("[ME-RV] Flags: "
            // + Integer.toHexString(delta.getFlags()));
            // System.out.println("[ME-RV] Kind: "
            // + Integer.toHexString(delta.getKind()));
            // System.out.println("[ME-RV] Resource: " + res.getFullPath());
            if(!res.isAccessible()){
                return false;
            }
            if (res.getType() == IResource.ROOT) {
                return true;
            }
            final IProject project = res.getProject();

            // handle projects that contain a mercurial repository
            if (autoShare && delta.getFlags() == IResourceDelta.OPEN
                    && RepositoryProvider.getProvider(project) == null) {
                HgRoot hgRoot;
                try {
                    hgRoot = MercurialTeamProvider.getHgRoot(project);
                    MercurialEclipsePlugin.logInfo("Autosharing " + project.getName()
                            + ". Detected repository location: " + hgRoot.getAbsolutePath(), null);
                } catch (HgException e) {
                    hgRoot = null;
                    MercurialEclipsePlugin.logInfo("Autosharing: " + e.getLocalizedMessage(), e);
                }
                final HgRoot root = hgRoot;
                if (root != null && root.length() > 0) {
                    final IWorkbenchWindow activeWorkbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

                    try {
                        new SafeWorkspaceJob(Messages.getString("MercurialStatusCache.autoshare.1") + project.getName() //$NON-NLS-1$
                                + Messages.getString("MercurialStatusCache.autoshare.2")) { //$NON-NLS-1$
                            /*
                             * (non-Javadoc)
                             *
                             * @see com.vectrace.MercurialEclipse.SafeWorkspaceJob #runSafe
                             * (org.eclipse.core.runtime.IProgressMonitor)
                             */
                            @Override
                            protected IStatus runSafe(IProgressMonitor monitor) {
                                try {
                                    new InitOperation(activeWorkbenchWindow, project, root, root.getAbsolutePath())
                                    .run(monitor);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                                return super.runSafe(monitor);
                            }
                        }.schedule();
                    } catch (Exception e) {
                        throw new HgException(e.getLocalizedMessage(), e);
                    }
                }
            }

            if (!Team.isIgnoredHint(res) && (RepositoryProvider.getProvider(res.getProject(), MercurialTeamProvider.ID) != null)) {
                if (res.getType() == IResource.FILE && !res.isTeamPrivateMember() && !res.isDerived()) {
                    int flag = delta.getFlags() & INTERESTING_CHANGES;
                    IResource resource = getResource(res);
                    Set<IResource> addSet = added.get(project);
                    if (addSet == null) {
                        addSet = new HashSet<IResource>();
                    }

                    Set<IResource> removeSet = removed.get(project);
                    if (removeSet == null) {
                        removeSet = new HashSet<IResource>();
                    }

                    Set<IResource> changeSet = changed.get(project);
                    if (changeSet == null) {
                        changeSet = new HashSet<IResource>();
                    }
                    // System.out.println("[ME-RV] " + res.getFullPath()
                    // + " interesting? Result: "
                    // + Integer.toHexString(flag));
                    switch (delta.getKind()) {
                    case IResourceDelta.ADDED:
                        addSet.add(resource);
                        added.put(project, addSet);
                        break;
                    case IResourceDelta.CHANGED:
                        if (flag != 0 && isSupervised(res)) {
                            changeSet.add(resource);
                            changed.put(project, changeSet);
                        }
                        break;
                    case IResourceDelta.REMOVED:
                        if (isSupervised(res)) {
                            removeSet.add(resource);
                            removed.put(project, removeSet);
                        }
                        break;
                    }
                }
                // System.out
                // .println("[ME-RV] Descending to next level (returning with true)");
                return true;
            }
            // System.out.println("[ME-RV] Not descending (returning with false)");
            return false;
        }
    }

    private final class MemberStatusVisitor implements IResourceVisitor {

        private final BitSet bitSet;
        private final IResource parent;

        public MemberStatusVisitor(IResource parent, BitSet bitSet) {
            this.bitSet = bitSet;
            this.parent = parent;
        }

        public boolean visit(IResource resource) throws CoreException {
            if (parent.isTeamPrivateMember()) {
                resource.setTeamPrivateMember(true);
                return false;
            }

            if (parent.isDerived()) {
                resource.setDerived(true);
                return false;
            }

            if (!resource.equals(parent)) {
                BitSet memberBitSet = statusMap.get(resource.getLocation());
                if (memberBitSet != null) {
                    bitSet.or(memberBitSet);
                }
            }
            return true;
        }

    }

    /*
     * Initialization On Demand Holder idiom, thread-safe and instance will not be created until getInstance is called
     * in the outer class.
     */
    private static final class MercurialStatusCacheHolder {
        public static final MercurialStatusCache instance = new MercurialStatusCache();
    }

    public final static int BIT_IGNORE = 0;
    public final static int BIT_CLEAN = 1;
    public final static int BIT_DELETED = 2;
    public final static int BIT_REMOVED = 3;
    public final static int BIT_UNKNOWN = 4;
    public final static int BIT_ADDED = 5;
    public final static int BIT_MODIFIED = 6;
    public final static int BIT_IMPOSSIBLE = 7;
    public final static int BIT_CONFLICT = 8;

    public static final char CHAR_MODIFIED = 'M';
    public static final char CHAR_ADDED = 'A';
    public static final char CHAR_UNKNOWN = '?';
    public static final char CHAR_CLEAN = 'C';
    public static final char CHAR_IGNORED = 'I';
    public static final char CHAR_REMOVED = 'R';
    public static final char CHAR_DELETED = '!';

    protected int INTERESTING_CHANGES = IResourceDelta.CONTENT | IResourceDelta.MOVED_FROM | IResourceDelta.MOVED_TO
    | IResourceDelta.OPEN | IResourceDelta.REPLACED | IResourceDelta.TYPE;

    private static final Object DUMMY = new Object();

    /** Used to store the last known status of a resource */
    private final Map<IPath, BitSet> statusMap = new ConcurrentHashMap<IPath, BitSet>();

    /** Used to store which projects have already been parsed */
    private final Map<IProject, Object> knownStatus = new ConcurrentHashMap<IProject, Object>();

    /* Access to this map must be protected with a synchronized lock itself */
    private final Map<IProject, ReentrantLock> locks = new HashMap<IProject, ReentrantLock>();

    private final Map<IProject, Set<IResource>> projectResources = new HashMap<IProject, Set<IResource>>();
    private boolean computeDeepStatus;
    private boolean completeStatus;
    private int statusBatchSize;
    private static final ReentrantLock DUMMY_LOCK = new ReentrantLock(){
        @Override
        public void lock() {}
        @Override
        public void unlock() {}
        @Override
        public boolean isLocked() {return false;}
    };

    private MercurialStatusCache() {
        initPreferences();
        ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
        MercurialEclipsePlugin.getDefault().getPreferenceStore().addPropertyChangeListener(
                new IPropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent event) {
                        initPreferences();
                    }
                });
        // new RefreshStatusJob("Initializing Mercurial plugin...").schedule();
    }

    public static final MercurialStatusCache getInstance() {
        return MercurialStatusCacheHolder.instance;
    }

    protected void addToProjectResources(IProject project, IResource member) {
        if (member.getType() == IResource.PROJECT) {
            return;
        }
        synchronized (projectResources) {
            Set<IResource> set = projectResources.get(project);
            if (set == null) {
                set = new HashSet<IResource>();
            }
            set.add(member);
            projectResources.put(project, set);
        }
    }

    /**
     * Clears the known status of all resources and projects. and calls for an update of decoration
     */
    public synchronized void clear() {
        /*
         * While this clearing of status is a "naive" implementation, it is simple.
         */
        statusMap.clear();
        knownStatus.clear();
        projectResources.clear();
        synchronized (locks) {
            locks.clear();
        }
        getInstance().setChanged();
        getInstance().notifyObservers(knownStatus);
    }

    /**
     * Sets lock on HgRoot of given resource
     *
     * @param resource
     * @return
     * @throws HgException
     */
    public ReentrantLock getLock(IResource resource) throws HgException {
        IProject project = resource.getProject();
        synchronized (locks) {
            ReentrantLock lock = locks.get(project);
            if (lock == null) {
                if (!resource.isAccessible() || resource.isDerived() || resource.isLinked()
                        || !MercurialUtilities.hgIsTeamProviderFor(resource, false)) {
                    lock = DUMMY_LOCK;
                    // TODO we could put the dummy lock here, but then it would forever
                    // stay in the cache, because NOBODY refresh it today.
                    // so if a previously not managed project would became a hg project
                    // it would NOT have a lock anymore
                } else {
                    lock = new ReentrantLock();
                    locks.put(project, lock);
                }
            }
            return lock;
        }
    }

    /**
     * Checks if status for given project is known.
     *
     * @param project
     *            the project to be checked
     * @return true if known, false if not.
     * @throws HgException
     */
    public boolean isStatusKnown(IProject project) throws HgException {
        ReentrantLock lock = getLock(project);
        try {
            lock.lock();
            return knownStatus.containsKey(project);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the status of the given resource from cache. The returned BitSet contains a BitSet of the status flags set.
     *
     * The flags correspond to the BIT_* constants in this class.
     *
     * @param resource
     *            the resource to get status for.
     * @return the BitSet with status flags.
     * @throws HgException
     */
    public BitSet getStatus(IResource resource) throws HgException {
        ReentrantLock lock = getLock(resource);
        try {
            lock.lock();
            return statusMap.get(resource.getLocation());
        } finally {
            lock.unlock();
        }
    }

    public boolean isSupervised(IResource resource) throws HgException {
        return MercurialUtilities.hgIsTeamProviderFor(resource, false)
        && isSupervised(resource.getProject(), resource.getLocation());
    }

    public boolean isSupervised(IResource resource, IPath path) throws HgException {
        Assert.isNotNull(resource);
        Assert.isNotNull(path);

        // check for Eclipse ignore settings
        if (Team.isIgnoredHint(resource)) {
            return false;
        }

        ReentrantLock lock = getLock(resource);

        IProject project = resource.getProject();
        if (project.isAccessible() && null != RepositoryProvider.getProvider(project, MercurialTeamProvider.ID)) {
            if (path.equals(project.getLocation())) {
                return true;
            }
            try {
                lock.lock();
                BitSet status = statusMap.get(path);
                if (status != null) {
                    switch (status.length() - 1) {
                    case MercurialStatusCache.BIT_IGNORE:
                    case MercurialStatusCache.BIT_UNKNOWN:
                        File fileSystemResource = path.toFile();
                        if (fileSystemResource.isDirectory() && status.length() > 1) {
                            // a directory is still supervised if one of the
                            // following bits is set
                            boolean supervised = status.get(BIT_ADDED) || status.get(BIT_CLEAN)
                            || status.get(BIT_DELETED) || status.get(BIT_MODIFIED) || status.get(BIT_REMOVED);
                            return supervised;
                        }
                        return false;
                    }
                    return true;
                }
            } finally {
                lock.unlock();
            }
        }
        return false;

    }

    /**
     *
     */
    public boolean hasUncommittedChanges(IResource[] resources) throws HgException {
        if (resources != null && resources.length > 0) {
            for (IResource resource : resources) {
                BitSet status = getStatus(resource);
                if (status.length() - 1 > MercurialStatusCache.BIT_CLEAN) {
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    public boolean isAdded(IResource resource, IPath path) throws HgException {
        Assert.isNotNull(resource);
        Assert.isNotNull(path);
        if (null != RepositoryProvider.getProvider(resource.getProject(), MercurialTeamProvider.ID)) {
            // if (path.equals(project.getLocation())) {
            // // FIX ME: This breaks on new projects without changelog
            // return false;
            // }
            ReentrantLock lock = getLock(resource);
            try {
                lock.lock();
                BitSet status = statusMap.get(path);
                if (status != null) {
                    switch (status.length() - 1) {
                    case MercurialStatusCache.BIT_ADDED:
                        File fileSystemResource = path.toFile();
                        if (fileSystemResource.isDirectory() && status.length() > 1) {
                            // a directory is still supervised if one of the
                            // following bits is set
                            boolean supervised = status.get(BIT_CLEAN) || status.get(BIT_DELETED)
                            || status.get(BIT_MODIFIED) || status.get(BIT_REMOVED) || status.get(BIT_CONFLICT)
                            || status.get(BIT_IGNORE);
                            return !supervised;
                        }
                        return true;
                    }
                    return false;
                }
            } finally {
                lock.unlock();
            }
        }
        return false;
    }

    /**
     * Refreshes local repository status. No refresh of changesets.
     *
     * @param project
     * @throws TeamException
     */
    public void refresh(final IProject project) throws TeamException {
        refreshStatus(project, new NullProgressMonitor());
    }

    /**
     * @param project
     * @throws HgException
     */
    public void refreshStatus(final IResource res, IProgressMonitor monitor) throws HgException {
        Assert.isNotNull(res);
        if (monitor != null) {
            monitor.subTask(Messages.getString("MercurialStatusCache.Refreshing") + res.getName()); //$NON-NLS-1$
        }

        if (null != RepositoryProvider.getProvider(res.getProject(), MercurialTeamProvider.ID)
                && res.getProject().isOpen()) {
            Set<IResource> changed;
            if (res.isTeamPrivateMember() || res.isDerived()) {
                return;
            }
            // members should contain folders and project, so we clear
            // status for files, folders and project
            Set<IResource> resources = getLocalMembers(res);
            if (monitor != null) {
                monitor.worked(1);
            }
            ReentrantLock lock = getLock(res);
            try {
                lock.lock();
                for (IResource resource : resources) {
                    statusMap.remove(resource.getLocation());
                }
                if (monitor != null) {
                    monitor.worked(1);
                }
                statusMap.remove(res.getLocation());
                String output = HgStatusClient.getStatusWithoutIgnored(res);
                if (monitor != null) {
                    monitor.worked(1);
                }
                File root = AbstractClient.getHgRoot(res);
                changed = parseStatus(root, res, output);
                if (monitor != null) {
                    monitor.worked(1);
                }
                try {
                    String mergeNode = HgStatusClient.getMergeStatus(res);
                    res.getProject().setPersistentProperty(ResourceProperties.MERGING, mergeNode);
                } catch (CoreException e) {
                    throw new HgException(Messages.getString("MercurialStatusCache.FailedToRefreshMergeStatus"), e); //$NON-NLS-1$
                }
            } finally {
                lock.unlock();
            }
            if (monitor != null) {
                monitor.worked(1);
            }
            notifyChanged(changed);
        }

        if (monitor != null) {
            monitor.worked(1);
        }
    }

    /**
     * @param res
     * @throws HgException
     */
    private void checkForConflict(final IResource res) throws HgException {
        List<FlaggedAdaptable> status;
        if (HgResolveClient.checkAvailable()) {
            status = HgResolveClient.list(res);
        } else {
            status = HgIMergeClient.getMergeStatus(res);
        }
        for (FlaggedAdaptable flaggedAdaptable : status) {
            IFile file = (IFile) flaggedAdaptable.getAdapter(IFile.class);
            if (flaggedAdaptable.getFlag() == 'U') {
                addConflict(file);
            } else {
                removeConflict(file);
            }
        }
    }

    /**
     * @param root
     * @param res
     * @param output
     * @throws HgException
     */
    private Set<IResource> parseStatus(File root, final IResource res, String output) throws HgException {
        IProject project = res.getProject();
        if (res.getType() == IResource.PROJECT) {
            knownStatus.put(project, DUMMY);
        }
        // we need the project for performance reasons - gotta hand it to
        // addToProjectResources
        Set<IResource> changed = new HashSet<IResource>();
        Scanner scanner = new Scanner(output);
        while (scanner.hasNext()) {
            String status = scanner.next();
            String localName = scanner.nextLine().trim();

            IResource member = convertRepoRelPath(root, project, localName);

            // doesn't belong to our project (can happen if root is above
            // project level)
            if (member == null) {
                continue;
            }

            BitSet bitSet = new BitSet();
            boolean ignoredHint = Team.isIgnoredHint(member);
            if (ignoredHint) {
                bitSet.set(BIT_IGNORE);
            } else {
                bitSet.set(getBitIndex(status.charAt(0)));
                changed.add(member);
            }
            statusMap.put(member.getLocation(), bitSet);

            if (!ignoredHint && member.getType() == IResource.FILE && getBitIndex(status.charAt(0)) != BIT_IGNORE) {
                addToProjectResources(project, member);
            }

            changed.addAll(setStatusToAncestors(member, bitSet));
        }
        // add conflict status if merging
        try {
            if (project.getPersistentProperty(ResourceProperties.MERGING) != null) {
                checkForConflict(res);
            }
        } catch (Exception e) {
            MercurialEclipsePlugin.logError(e);
        }
        return changed;
    }

    /**
     * @param resource
     * @param resourceBitSet
     * @return
     */
    private Set<IResource> setStatusToAncestors(IResource resource, BitSet resourceBitSet) {
        // ancestors
        IProject project = resource.getProject();
        Set<IResource> ancestors = new HashSet<IResource>();
        boolean computeDeep = isComputeDeepStatus();
        boolean complete = isCompleteStatus();
        IResource parent = resource.getParent();
        for (; parent != null && parent != project.getParent(); parent = parent.getParent()) {
            IPath location = parent.getLocation();
            BitSet parentBitSet = statusMap.get(location);
            BitSet cloneBitSet = (BitSet) resourceBitSet.clone();
            if (parentBitSet != null) {
                if (!complete && computeDeep && resource.getType() != IResource.PROJECT) {
                    if (parent.isAccessible() && !parent.isTeamPrivateMember() && !parent.isDerived()) {
                        IResourceVisitor visitor = new MemberStatusVisitor(parent, cloneBitSet);
                        try {
                            parent.accept(visitor, IResource.DEPTH_ONE, false);
                        } catch (CoreException e) {
                            MercurialEclipsePlugin.logError(e);
                        }
                    }
                } else {
                    cloneBitSet.or(parentBitSet);
                }
            }
            statusMap.put(location, cloneBitSet);
            ancestors.add(parent);
            addToProjectResources(project, parent);
        }
        return ancestors;
    }

    private boolean isComputeDeepStatus() {
        return computeDeepStatus;
    }

    public boolean isCompleteStatus() {
        return completeStatus;
    }

    public int getBitIndex(char status) {
        switch (status) {
        case '!':
            return BIT_DELETED;
        case 'R':
            return BIT_REMOVED;
        case 'I':
            return BIT_IGNORE;
        case 'C':
            return BIT_CLEAN;
        case '?':
            return BIT_UNKNOWN;
        case 'A':
            return BIT_ADDED;
        case 'M':
            return BIT_MODIFIED;
        default:
            String msg = Messages.getString("MercurialStatusCache.UnknownStatus") + status + "'"; //$NON-NLS-1$ //$NON-NLS-2$
            MercurialEclipsePlugin.logWarning(msg, new HgException(msg));
            return BIT_IMPOSSIBLE;
        }
    }

    /**
     * Converts the given bit index to the status character Mercurial uses.
     *
     * @param bitIndex
     * @return
     */
    public char getStatusChar(int bitIndex) {
        switch (bitIndex) {
        case BIT_DELETED:
            return CHAR_DELETED;
        case BIT_REMOVED:
            return CHAR_REMOVED;
        case BIT_IGNORE:
            return CHAR_IGNORED;
        case BIT_CLEAN:
            return CHAR_CLEAN;
        case BIT_UNKNOWN:
            return CHAR_UNKNOWN;
        case BIT_ADDED:
            return CHAR_ADDED;
        case BIT_MODIFIED:
            return CHAR_MODIFIED;
        default:
            String msg = Messages.getString("MercurialStatusCache.UnknownStatus") + bitIndex + "'"; //$NON-NLS-1$ //$NON-NLS-2$
            MercurialEclipsePlugin.logWarning(msg, new HgException(msg));
            return BIT_IMPOSSIBLE;
        }
    }

    /**
     * Returns the status character used by Mercurial that applies to this resource
     *
     * @param resource
     *            the resource to query the status for
     * @return ! (deleted), R (removed), I (ignored), C (clean), ? (unknown), A (added) or M (modified)
     * @throws HgException
     */
    public char getStatusChar(IResource resource) throws HgException {
        BitSet status = getStatus(resource);
        char statusChar = getStatusChar(status.length() - 1);
        return statusChar;
    }

    /**
     * Refreshes the status for each project in Workspace by questioning Mercurial.
     *
     * @throws TeamException
     *             if status check encountered problems.
     */
    public void refreshStatus(IProgressMonitor monitor) throws TeamException {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : projects) {
            refreshStatus(project, monitor);
        }
    }

    /**
     * Checks whether Status of given resource is known.
     *
     * @param resource
     *            the resource to be checked
     * @return true if known, false if not
     * @throws HgException
     */
    public boolean isStatusKnown(IResource resource) throws HgException {
        return getStatus(resource) != null;
    }

    /**
     * Gets all Projects managed by Mercurial whose status is known.
     *
     * @return an IProject[] of the projects
     */
    public IProject[] getAllManagedProjects() {
        return knownStatus.keySet().toArray(new IProject[knownStatus.size()]);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.core.resources.IResourceChangeListener#resourceChanged(org
     * .eclipse.core.resources.IResourceChangeEvent)
     */
    public void resourceChanged(IResourceChangeEvent event) {
        if (event.getType() == IResourceChangeEvent.POST_CHANGE) {
            try {
                IResourceDelta delta = event.getDelta();

                final Map<IProject, Set<IResource>> changed = new HashMap<IProject, Set<IResource>>();
                final Map<IProject, Set<IResource>> added = new HashMap<IProject, Set<IResource>>();
                final Map<IProject, Set<IResource>> removed = new HashMap<IProject, Set<IResource>>();

                IResourceDeltaVisitor visitor = new ResourceDeltaVisitor(removed, changed, added);

                // walk tree
                delta.accept(visitor);
                final IWorkspace workspace = ResourcesPlugin.getWorkspace();
                final Set<IProject> changedProjects = new HashSet<IProject>(changed.keySet());
                changedProjects.addAll(added.keySet());
                changedProjects.addAll(removed.keySet());
                for (final IProject project : changedProjects) {

                    final IWorkspaceRunnable job = new IWorkspaceRunnable() {

                        public void run(IProgressMonitor monitor) throws CoreException {

                            // now process gathered changes (they are in the
                            // lists)
                            try {
                                Set<IResource> addedSet = added.get(project);
                                Set<IResource> removedSet = removed.get(project);
                                Set<IResource> changedSet = changed.get(project);
                                Set<IResource> resources = new HashSet<IResource>();
                                if (changedSet != null) {
                                    resources.addAll(changedSet);
                                }
                                if (addedSet != null) {
                                    resources.addAll(addedSet);
                                }
                                if (removedSet != null) {
                                    resources.addAll(removedSet);
                                }

                                if (resources.size() > NUM_CHANGED_FOR_COMPLETE_STATUS) {
                                    monitor.beginTask(Messages.getString("MercurialStatusCache.RefreshingProjects"), //$NON-NLS-1$
                                            2);
                                    monitor.subTask(Messages.getString("MercurialStatusCache.RefreshingProject") //$NON-NLS-1$
                                            + project.getName() + Messages.getString("MercurialStatusCache....")); //$NON-NLS-1$
                                    refreshStatus(project, monitor);
                                    monitor.worked(1);
                                } else {
                                    monitor.beginTask(
                                            Messages.getString("MercurialStatusCache.RefreshingResources..."), 4); //$NON-NLS-1$
                                    // changed
                                    monitor.subTask(Messages
                                            .getString("MercurialStatusCache.RefreshingChangedResources...")); //$NON-NLS-1$
                                    if (changedSet != null && changedSet.size() > 0) {
                                        refreshStatus(changedSet);
                                    }
                                    monitor.worked(1);

                                    // added
                                    monitor.subTask(Messages
                                            .getString("MercurialStatusCache.RefreshingAddedResources...")); //$NON-NLS-1$
                                    if (addedSet != null && addedSet.size() > 0) {
                                        refreshStatus(addedSet);
                                    }
                                    monitor.worked(1);

                                    // removed not used right now
                                    // refreshStatus(removed);
                                }
                                // notify observers
                                monitor.subTask(Messages
                                        .getString("MercurialStatusCache.AddingResourcesForDecoratorUpdate...")); //$NON-NLS-1$
                                monitor.worked(1);
                                monitor
                                .subTask(Messages
                                        .getString("MercurialStatusCache.TriggeringDecoratorUpdate...")); //$NON-NLS-1$
                                notifyChanged(resources);
                                monitor.worked(1);
                            } finally {
                                monitor.done();
                            }
                        }
                    };
                    final ISchedulingRule rule = workspace.getRuleFactory().modifyRule(project);
                    SafeWorkspaceJob wsJob = new SafeWorkspaceJob(Messages
                            .getString("MercurialStatusCache.RefreshStatus...")) { //$NON-NLS-1$
                        @Override
                        protected IStatus runSafe(IProgressMonitor monitor) {
                            try {
                                workspace.run(job, rule, IWorkspace.AVOID_UPDATE, monitor);
                            } catch (CoreException e) {
                                MercurialEclipsePlugin.logError(e);
                                return new Status(IStatus.ERROR, MercurialEclipsePlugin.ID, e.getLocalizedMessage(), e);
                            }
                            return super.runSafe(monitor);
                        }
                    };
                    wsJob.setRule(rule);
                    wsJob.schedule(200);
                }
            } catch (CoreException e) {
                MercurialEclipsePlugin.logError(e);
            }
        }
    }

    /**
     * Refreshes Status of resources in batches
     *
     * @param resources
     * @return
     * @throws HgException
     */
    private void refreshStatus(final Set<IResource> resources) throws HgException {
        if (resources == null) {
            return;
        }
        int batchSize = getStatusBatchSize();
        List<IResource> currentBatch = new ArrayList<IResource>();
        for (Iterator<IResource> iterator = resources.iterator(); iterator.hasNext();) {
            IResource resource = iterator.next();

            // project status wanted, no batching needed
            if (resource.getType() == IResource.PROJECT) {
                try {
                    refreshStatus(resource, null);
                } catch (Exception e) {
                    throw new HgException(e.getMessage(), e);
                }
                continue;
            }

            // status for single resource is batched
            if (!resource.isTeamPrivateMember()) {
                currentBatch.add(resource);
            }
            if (currentBatch.size() % batchSize == 0 || !iterator.hasNext()) {
                // call hg with batch
                File root = AbstractClient.getHgRoot(resource);
                String output = HgStatusClient.getStatusWithoutIgnored(resource.getLocation().toFile(), currentBatch);
                parseStatus(root, resource, output);
                currentBatch.clear();
            }
        }
    }

    private int getStatusBatchSize() {
        return statusBatchSize;
    }

    /**
     * Determines Members of given resource without adding itself.
     *
     * @param resource
     * @return never null
     * @throws HgException
     */
    public Set<IResource> getLocalMembers(IResource resource) throws HgException {
        Set<IResource> members = new HashSet<IResource>();
        ReentrantLock lock = getLock(resource);
        try {
            lock.lock();
            switch (resource.getType()) {
            case IResource.FILE:
                break;
            case IResource.PROJECT:
                synchronized (projectResources) {
                    Set<IResource> resources = projectResources.get(resource);
                    if (resources != null) {
                        members.addAll(resources);
                        members.remove(resource);
                    }
                }
                break;
            case IResource.FOLDER:
                for (IPath memberPath : statusMap.keySet()) {
                    if (memberPath.equals(resource.getLocation())) {
                        continue;
                    }

                    IContainer container = (IContainer) resource;
                    IResource foundMember = container.findMember(memberPath, false);
                    if (foundMember != null) {
                        members.add(foundMember);
                    }
                }
            }
            members.remove(resource);
            return members;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param project
     * @throws HgException
     */
    public void clear(IProject project) throws HgException {
        ReentrantLock lock = getLock(project);
        try {
            lock.lock();
            Set<IResource> members = getMembers(project);
            for (IResource resource : members) {
                statusMap.remove(resource.getLocation());
            }
            statusMap.remove(project.getLocation());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets conflict marker on resource status
     *
     * @param local
     * @throws HgException
     */
    public void addConflict(IResource local) throws HgException {
        BitSet status = getStatus(local);
        status.set(BIT_CONFLICT);
        setStatusToAncestors(local, status);
        notifyChanged(local);
    }

    /**
     * Removes conflict marker on resource status
     *
     * @param local
     * @throws HgException
     */
    public void removeConflict(IResource local) throws HgException {
        BitSet status = getStatus(local);
        status.clear(BIT_CONFLICT);
        setStatusToAncestors(local, status);
        notifyChanged(local);
    }

    /**
     * @param resources
     */
    @Override
    public void notifyChanged(Set<IResource> resources) {
        setChanged();
        notifyObservers(resources);
    }

    private void initPreferences(){
        computeDeepStatus = Boolean.valueOf(
                HgClients.getPreference(MercurialPreferenceConstants.RESOURCE_DECORATOR_COMPUTE_DEEP_STATUS, "false"))
                .booleanValue();
        completeStatus = Boolean.valueOf(
                HgClients.getPreference(MercurialPreferenceConstants.RESOURCE_DECORATOR_COMPLETE_STATUS, "false"))
                .booleanValue();
        // TODO: group batches by repo root
        String pref = HgClients.getPreference(MercurialPreferenceConstants.STATUS_BATCH_SIZE, String
                .valueOf(STATUS_BATCH_SIZE));

        statusBatchSize = STATUS_BATCH_SIZE;
        if (pref.length() > 0) {
            try {
                statusBatchSize = Integer.parseInt(pref);
            } catch (NumberFormatException e) {
                MercurialEclipsePlugin.logWarning(Messages
                        .getString("MercurialStatusCache.BatchSizeForStatusCommandNotCorrect."), e); //$NON-NLS-1$
            }
        }
    }

}
