/*******************************************************************************
 * Copyright (c) 2006-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Software Balm Consulting Inc (Peter Hunnisett <peter_hge at softwarebalm dot com>) - implementation
 *     Stefan Groschupf          - logError
 *     Jerome Negre              - storing in plain text instead of serializing Java Objects
 *     Bastian Doetsch           - support for project specific repository locations
 *     Adam Berkes (Intland)     - bug fixes
 *     Andrei Loskutov (Intland) - bug fixes
 *******************************************************************************/
package com.vectrace.MercurialEclipse.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.preference.IPreferenceStore;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.commands.HgPathsClient;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.model.IHgRepositoryLocation;
import com.vectrace.MercurialEclipse.repository.IRepositoryListener;
import com.vectrace.MercurialEclipse.team.MercurialTeamProvider;
import com.vectrace.MercurialEclipse.utils.ResourceUtils;
import com.vectrace.MercurialEclipse.utils.StringUtils;

/**
 * A manager for all Mercurial repository locations.
 * <p>
 * Initially, all the data was stored in the file system and was project based. One file per project
 * plus one file for all repositories. This lead to unneeded overhead/complexity for the case where
 * 100 projects under the same root was managed with 100 files with redundant or partly different
 * information.
 * <p>
 * Right now the data stored in the plugin preferences and is hg root based. The repo data is stored
 * twice: once the default repo for each hg root (if any) and secondly as a list of all available
 * repositories.
 * <p>
 * Repositories are considered unique by comparing their URL's (without the login info). Hg roots
 * are considered unique by their absolut hg root paths. Projects are not tracked here anymore, as
 * they always inherit hg root account/repo information.
 * <p>
 * Additionally, we store default commit names for each hg root, which may be different to the hg
 * push/pull user names. The reason is that commit name (like 'Andrei@Loskutov.com') may be same for
 * different repositories, but the 'push' user name must be different due the different account
 * names which may exist for different repositories (like 'hgeclipse' or 'iloveeclipse' for
 * bitbucket or javaforge). See {@link HgCommitMessageManager}.
 * <p>
 *
 */
public class HgRepositoryLocationManager {

	private static final String KEY_REPOS_PREFIX = "repo_"; //$NON-NLS-1$
	private static final String KEY_DEF_REPO_PREFIX = "def_" + KEY_REPOS_PREFIX; //$NON-NLS-1$

	private final Map<HgRoot, SortedSet<IHgRepositoryLocation>> rootRepos;
	private final SortedSet<IHgRepositoryLocation> repoHistory;
	private final HgRepositoryLocationParserDelegator delegator;

	private volatile boolean initialized;
	private final List<IRepositoryListener> repositoryListeners;

	public HgRepositoryLocationManager() {
		super();
		repositoryListeners = new ArrayList<IRepositoryListener>();
		rootRepos = new ConcurrentHashMap<HgRoot, SortedSet<IHgRepositoryLocation>>();
		repoHistory = new TreeSet<IHgRepositoryLocation>();
		delegator = new HgRepositoryLocationParserDelegator();
	}

	/**
	 * Load all saved repository locations from the plug-in's default area.
	 *
	 * @throws HgException
	 */
	public void start() throws HgException {
		getProjectRepos();
	}

	/**
	 * Flush all repository locations out to a file in the plug-in's default
	 * area.
	 */
	public void stop() {
		saveProjectRepos();
		saveRepositoryHistory();
	}

	/**
	 * Return an ordered list of all repository locations that are presently
	 * known.
	 */
	public SortedSet<IHgRepositoryLocation> getAllRepoLocations() {
		SortedSet<IHgRepositoryLocation> allRepos = new TreeSet<IHgRepositoryLocation>();
		for (SortedSet<IHgRepositoryLocation> locations : rootRepos.values()) {
			allRepos.addAll(locations);
		}
		allRepos.addAll(repoHistory);
		return allRepos;
	}

	public SortedSet<IHgRepositoryLocation> getAllRepoLocations(HgRoot hgRoot) {
		if(hgRoot == null){
			return new TreeSet<IHgRepositoryLocation>();
		}
		SortedSet<IHgRepositoryLocation> loc = rootRepos.get(hgRoot);
		if(loc != null) {
			return Collections.unmodifiableSortedSet(loc);
		}
		return new TreeSet<IHgRepositoryLocation>();
	}

	/**
	 * @param repo non null repo location
	 * @return a set of projects we know managed at given location, never null
	 */
	public Set<IProject> getAllRepoLocationProjects(IHgRepositoryLocation repo) {
		Set<IProject> projects = new HashSet<IProject>();

		try {
			getProjectRepos();
		} catch (Exception e) {
			MercurialEclipsePlugin.logError(e);
		}
		Set<HgRoot> loc = rootRepos.keySet();

		for (HgRoot hgRoot : loc) {
			SortedSet<IHgRepositoryLocation> set = rootRepos.get(hgRoot);
			if(set != null && set.contains(repo)){
				projects.addAll(ResourceUtils.getProjects(hgRoot));
			}
		}

		return projects;
	}

	/**
	 * @param repo non null repo location
	 * @return a set of projects we know managed at given location, never null
	 */
	public Set<HgRoot> getAllRepoLocationRoots(IHgRepositoryLocation repo) {
		Set<HgRoot> roots = new HashSet<HgRoot>();

		try {
			getProjectRepos();
		} catch (Exception e) {
			MercurialEclipsePlugin.logError(e);
		}
		Set<HgRoot> loc = rootRepos.keySet();

		for (HgRoot hgRoot : loc) {
			SortedSet<IHgRepositoryLocation> set = rootRepos.get(hgRoot);
			if(set != null && set.contains(repo)){
				roots.add(hgRoot);
			}
		}

		return Collections.unmodifiableSet(roots);
	}

	/**
	 * Add a repository location to the database.
	 */
	private boolean addRepoLocation(IHgRepositoryLocation loc) {
		return internalAddRepoLocation((HgRoot) null, loc);
	}

	/**
	 * Add a repository location to the database without to triggering loadRepos again
	 */
	private boolean internalAddRepoLocation(HgRoot hgRoot, IHgRepositoryLocation loc) {
		if (isEmpty(loc)) {
			return false;
		}

		if (hgRoot != null) {
			SortedSet<IHgRepositoryLocation> repoSet = rootRepos.get(hgRoot);
			if (repoSet == null) {
				repoSet = new TreeSet<IHgRepositoryLocation>();
			}
			repoSet.remove(loc);
			repoSet.add(loc);
			rootRepos.put(hgRoot, repoSet);
		}
		synchronized (repoHistory) {
			repoHistory.remove(loc);
			repoHistory.add(loc);
		}
		repositoryAdded(loc);

		return true;
	}

	private static boolean isEmpty(IHgRepositoryLocation loc) {
		return loc == null || (loc.getLocation() == null || loc.getLocation().length() == 0);
	}

	/**
	 * Add a repository location to the database. Associate a repository
	 * location to a particular hg root.
	 *
	 * @throws HgException
	 */
	public boolean addRepoLocation(HgRoot hgRoot, IHgRepositoryLocation loc) throws HgException {
		boolean result = internalAddRepoLocation(hgRoot, loc);
		if(result && hgRoot != null && getDefaultRepoLocation(hgRoot) == null){
			setDefaultRepository(hgRoot, loc);
		}
		return result;
	}

	private void getProjectRepos() throws HgException {
		if (!initialized) {
			initialized = true;
			loadRepos();
			loadRepositoryHistory();
		}
	}

	/**
	 * @return set with ALL projects managed by hg, <b>not only</b> projects for which we know remote repo locations
	 * @throws HgException
	 */
	private Map<HgRoot, List<IResource>> loadRepos() throws HgException {
		rootRepos.clear();
		List<IProject> projects = MercurialTeamProvider.getKnownHgProjects();
		Map<HgRoot, List<IResource>> roots = ResourceUtils.groupByRoot(projects);

		for (Entry<HgRoot, List<IResource>> entry : roots.entrySet()) {
			HgRoot hgRoot = entry.getKey();
			loadRepos(hgRoot);
		}
		return roots;
	}

	public void loadRepos(HgRoot hgRoot) throws HgException {
		// Load .hg/hgrc paths first; plugin settings will override these
		Map<String, String> hgrcRepos = HgPathsClient.getPaths(hgRoot);
		for (Map.Entry<String, String> nameAndUrl : hgrcRepos.entrySet()) {
			String url = nameAndUrl.getValue();
			IHgRepositoryLocation repoLocation = matchRepoLocation(url);
			if(repoLocation == null) {
				// if not existent, add to repository browser
				try {
					String logicalName = nameAndUrl.getKey();
					IHgRepositoryLocation loc = updateRepoLocation(hgRoot, url, logicalName,
							null, null);
					internalAddRepoLocation(hgRoot, loc);
				} catch (HgException e) {
					MercurialEclipsePlugin.logError(e);
				}
			}
		}
		SortedSet<IHgRepositoryLocation> locations = loadRepositories(getRootKey(hgRoot));
		for(IHgRepositoryLocation loc : locations) {
			internalAddRepoLocation(hgRoot, loc);
		}
		IHgRepositoryLocation defRepo = getDefaultRepoLocation(hgRoot);
		if(defRepo == null && !locations.isEmpty()){
			setDefaultRepository(hgRoot, locations.first());
		}
	}

	private void loadRepositoryHistory() {
		Set<IHgRepositoryLocation> locations = loadRepositories(KEY_REPOS_PREFIX);
		for (IHgRepositoryLocation loc : locations) {
			boolean usedByProject = false;
			for (HgRoot hgRoot : rootRepos.keySet()) {
				if (rootRepos.get(hgRoot).contains(loc)) {
					usedByProject = true;
				}
			}
			synchronized (repoHistory) {
				repoHistory.remove(loc);
				repoHistory.add(loc);
			}
			if (!usedByProject) {
				repositoryAdded(loc);
			}
		}
	}

	private SortedSet<IHgRepositoryLocation> loadRepositories(String key) {
		SortedSet<IHgRepositoryLocation> locations = new TreeSet<IHgRepositoryLocation>();
		IPreferenceStore store = MercurialEclipsePlugin.getDefault().getPreferenceStore();
		String allReposLine = store.getString(key);
		if(StringUtils.isEmpty(allReposLine)){
			return locations;
		}
		String[] repoLine = allReposLine.split("\\|");
		for (String line : repoLine) {
			if(line == null || line.length() == 0){
				continue;
			}
			try {
				IHgRepositoryLocation loc = delegator.delegateParse(line);
				if(loc != null) {
					locations.add(loc);
				}
			} catch (Exception e) {
				// log exception, but don't bother the user with it.
				MercurialEclipsePlugin.logError(e);
			}
		}
		return locations;
	}

	/**
	 * Set given location as default (topmost in hg repositories)
	 * @param hgRoot a valid hg root (not null)
	 * @param loc a valid repoository location (not null)
	 */
	public void setDefaultRepository(HgRoot hgRoot,	IHgRepositoryLocation loc) {
		Assert.isNotNull(hgRoot);
		Assert.isNotNull(loc);
		SortedSet<IHgRepositoryLocation> locations = rootRepos.get(hgRoot);
		IPreferenceStore store = MercurialEclipsePlugin.getDefault().getPreferenceStore();
		store.setValue(KEY_DEF_REPO_PREFIX + getRootKey(hgRoot), loc.getLocation());
		if (locations != null && !locations.contains(loc)) {
			locations.add(loc);
		} else {
			internalAddRepoLocation(hgRoot, loc);
		}
	}

	/**
	 * Returns the default repository location for a hg root, if it is set.
	 * @return may return null
	 */
	public IHgRepositoryLocation getDefaultRepoLocation(HgRoot hgRoot) {
		IPreferenceStore store = MercurialEclipsePlugin.getDefault().getPreferenceStore();
		String defLoc = store.getString(KEY_DEF_REPO_PREFIX + getRootKey(hgRoot));
		if(StringUtils.isEmpty(defLoc)){
			return null;
		}
		SortedSet<IHgRepositoryLocation> locations = rootRepos.get(hgRoot);
		if (locations != null && !locations.isEmpty()) {
			for (IHgRepositoryLocation repo : locations) {
				if(repo.getLocation().equals(defLoc)){
					return repo;
				}
			}
		}
		for (IHgRepositoryLocation repo : repoHistory) {
			if(repo.getLocation().equals(defLoc)){
				internalAddRepoLocation(hgRoot, repo);
				return repo;
			}
		}
		return null;
	}

	private String getRootKey(HgRoot root) {
		return KEY_REPOS_PREFIX + root.getAbsolutePath();
	}

	private void saveProjectRepos() {
		List<IProject> projects = MercurialTeamProvider.getKnownHgProjects();

		Map<HgRoot, List<IResource>> byRoot = ResourceUtils.groupByRoot(projects);
		Set<HgRoot> roots = byRoot.keySet();

		for (HgRoot hgRoot : roots) {
			String key = getRootKey(hgRoot);
			SortedSet<IHgRepositoryLocation> repoSet = rootRepos.get(hgRoot);
			saveRepositories(key, repoSet);
		}
	}

	private void saveRepositoryHistory() {
		saveRepositories(KEY_REPOS_PREFIX, repoHistory);
	}

	private void saveRepositories(String key, Set<IHgRepositoryLocation> locations) {
		if (locations == null || locations.isEmpty()) {
			return;
		}
		IPreferenceStore store = MercurialEclipsePlugin.getDefault().getPreferenceStore();
		StringBuilder sb = new StringBuilder();
		for (IHgRepositoryLocation repo : locations) {
			String line = delegator.delegateCreate(repo);
			if(line != null){
				sb.append(line);
				sb.append('|');
			}
		}
		store.setValue(key, sb.toString());
	}

	/**
	 * Get a repo by its URL. If URL is unknown, returns a new location.
	 * @return never returns null
	 */
	public IHgRepositoryLocation getRepoLocation(String url) throws HgException {
		return getRepoLocation(url, null, null);
	}

	/**
	 * Get a repo by its URL. If URL is unknown, returns a new location.
	 * @return never returns null
	 */
	public IHgRepositoryLocation getRepoLocation(String url, String user,
			String pass) throws HgException {
		getProjectRepos();
		IHgRepositoryLocation location = matchRepoLocation(url);
		if (location != null) {
			if (user == null || user.length() == 0 || user.equals(location.getUser())) {
				return location;
			}
		}

		// make a new location if no matches exist or it's a different user
		return HgRepositoryLocationParser.parseLocation(url, user, pass);
	}

	/**
	 * Get a repo specified by properties. If repository for given url is unknown,
	 * returns a new location.
	 * @return never returns null
	 */
	public IHgRepositoryLocation getRepoLocation(Properties props) throws HgException {
		String user = props.getProperty("user"); //$NON-NLS-1$
		if ((user == null) || (user.length() == 0)) {
			user = null;
		}
		String password = props.getProperty("password"); //$NON-NLS-1$
		if (user == null) {
			password = null;
		}
		String url = props.getProperty("url"); //$NON-NLS-1$
		if (url == null) {
			throw new HgException(Messages
					.getString("HgRepositoryLocation.urlMustNotBeNull")); //$NON-NLS-1$
		}

		IHgRepositoryLocation location = matchRepoLocation(url);
		if (location != null) {
			if (user == null || user.length() == 0 || user.equals(location.getUser())) {
				return location;
			}
		}

		// make a new location if no matches exist or it's a different user
		return HgRepositoryLocationParser.parseLocation(url, user, password);
	}

	/**
	 * Simple search on existing repos.
	 * @return may return null
	 */
	private IHgRepositoryLocation matchRepoLocation(String url) {
		url = HgRepositoryLocationParser.trimLocation(url);
		if (StringUtils.isEmpty(url)) {
			return null;
		}
		for (IHgRepositoryLocation loc : getAllRepoLocations()) {
			if (url.equals(loc.getLocation())) {
				return loc;
			}
		}
		return null;
	}

	/**
	 * Gets a repo by its URL. If URL is unknown, returns a new location,
	 * adding it to the global repositories cache. Will update stored
	 * last user and password with the provided values.
	 */
	public IHgRepositoryLocation updateRepoLocation(HgRoot hgRoot, String url,
			String logicalName, String user, String pass) throws HgException {
		IHgRepositoryLocation loc = matchRepoLocation(url);

		if (loc == null) {
			// in some cases url may be a repository database line
			loc = HgRepositoryLocationParser.parseLocation(logicalName, url, user, pass);
			addRepoLocation(loc);
			return loc;
		}

		boolean update = false;

		String myLogicalName = logicalName;
		String myUser = user;
		String myPass = pass;

		if (logicalName != null && logicalName.length() > 0 && !logicalName.equals(loc.getLogicalName())) {
			update = true;
		} else {
			myLogicalName = loc.getLogicalName();
		}
		if (user != null && user.length() > 0 && !user.equals(loc.getUser())) {
			update = true;
		} else {
			myUser = loc.getUser();
		}
		if (pass != null && pass.length() > 0 && !pass.equals(loc.getPassword())) {
			update = true;
		} else {
			myPass = loc.getPassword();
		}

		if (update) {
			IHgRepositoryLocation updated = HgRepositoryLocationParser.parseLocation(myLogicalName,
					loc.getLocation(), myUser, myPass);

			for (SortedSet<IHgRepositoryLocation> locs : rootRepos.values()) {
				if (locs.remove(updated)) {
					locs.add(updated);
				}
			}
			synchronized (repoHistory) {
				if (repoHistory.remove(updated)) {
					repoHistory.add(updated);
				}
			}
			repositoryModified(updated);
			return updated;
		}

		return loc;
	}

	/**
	 * Create a repository location instance from the given properties. The
	 * supported properties are: user The username for the connection (optional)
	 * password The password used for the connection (optional) url The url
	 * where the repository resides
	 */
	public IHgRepositoryLocation fromProperties(HgRoot hgRoot, Properties configuration)
			throws HgException {

		String user = configuration.getProperty("user"); //$NON-NLS-1$
		if ((user == null) || (user.length() == 0)) {
			user = null;
		}
		String password = configuration.getProperty("password"); //$NON-NLS-1$
		if (user == null) {
			password = null;
		}
		String url = configuration.getProperty("url"); //$NON-NLS-1$
		if (url == null) {
			throw new HgException(Messages.getString("HgRepositoryLocation.urlMustNotBeNull")); //$NON-NLS-1$
		}
		return updateRepoLocation(hgRoot, url, null, user, password);
	}

	public void refreshRepositories(IProgressMonitor monitor)
			throws HgException {
		stop();
		start();
	}

	/**
	 * Create a repository instance from the given properties. The supported
	 * properties are:
	 *
	 * user The username for the connection (optional) password The password
	 * used for the connection (optional) url The url where the repository
	 * resides
	 */
	public IHgRepositoryLocation createRepository(Properties configuration)
			throws HgException {
		// Create a new repository location
		IHgRepositoryLocation location = fromProperties(null, configuration);
		addRepoLocation(location);
		return location;
	}

	public void disposeRepository(IHgRepositoryLocation hgRepo) {
		Assert.isNotNull(hgRepo);
		for (HgRoot hgRoot : rootRepos.keySet()) {
			SortedSet<IHgRepositoryLocation> pRepos = rootRepos.get(hgRoot);
			if (pRepos != null) {
				for (IHgRepositoryLocation repo : pRepos) {
					if (repo.equals(hgRepo)) {
						pRepos.remove(repo);
						repositoryRemoved(hgRepo);
						break;
					}
				}
			}
		}
		IHgRepositoryLocation removed = null;
		synchronized (repoHistory) {
			for (IHgRepositoryLocation loc : repoHistory) {
				if (loc.equals(hgRepo)) {
					repoHistory.remove(loc);
					removed = loc;
					break;
				}
			}
		}
		if(removed != null) {
			repositoryRemoved(removed);
		}
	}

	/**
	 * Register to receive notification of repository creation and disposal
	 */
	public void addRepositoryListener(IRepositoryListener listener) {
		repositoryListeners.add(listener);
	}

	/**
	 * De-register a listener
	 */
	public void removeRepositoryListener(IRepositoryListener listener) {
		repositoryListeners.remove(listener);
	}

	/**
	 * signals all listener that we have removed a repository
	 */
	private void repositoryRemoved(IHgRepositoryLocation repository) {
		Iterator<IRepositoryListener> it = repositoryListeners.iterator();
		while (it.hasNext()) {
			IRepositoryListener listener = it.next();
			listener.repositoryRemoved(repository);
		}
	}

	/**
	 * signals all listener that we have removed a repository
	 */
	private void repositoryAdded(IHgRepositoryLocation repository) {
		Iterator<IRepositoryListener> it = repositoryListeners.iterator();
		while (it.hasNext()) {
			IRepositoryListener listener = it.next();
			listener.repositoryAdded(repository);
		}
	}

	/**
	 * signals all listener that we have removed a repository
	 */
	private void repositoryModified(IHgRepositoryLocation repository) {
		Iterator<IRepositoryListener> it = repositoryListeners.iterator();
		while (it.hasNext()) {
			IRepositoryListener listener = it.next();
			listener.repositoryModified(repository);
		}
	}


}
