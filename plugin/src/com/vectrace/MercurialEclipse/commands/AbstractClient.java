/*******************************************************************************
 * Copyright (c) 2005-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	   Bastian	implementation
 *     Andrei Loskutov           - bug fixes
 *     Adam Berkes (Intland)     - bug fixes
 *     Philip Graf               - proxy support
 *******************************************************************************/
package com.vectrace.MercurialEclipse.commands;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.model.IHgRepositoryLocation;
import com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants;
import com.vectrace.MercurialEclipse.team.cache.MercurialRootCache;
import com.vectrace.MercurialEclipse.utils.ResourceUtils;

/**
 * Base client class
 * @author bastian
 *
 */
public abstract class AbstractClient {

	private static final String HTTP_PATTERN_STRING = "[hH][tT][tT][pP]:.*[@]"; //$NON-NLS-1$
	private static final String HTTPS_PATTERN_STRING = "[hH][tT][tT][pP][sS]:.*[@]"; //$NON-NLS-1$
	private static final String SSH_PATTERN_STRING = "[sS][sS][hH]:.*[@]"; //$NON-NLS-1$
	private static final String SVN_PATTERN_STRING = "[sS][vV][nN]:.*[@]"; //$NON-NLS-1$

	private static final Pattern HTTP_PATTERN = Pattern.compile(HTTP_PATTERN_STRING);
	private static final Pattern HTTPS_PATTERN = Pattern.compile(HTTPS_PATTERN_STRING);
	private static final Pattern SSH_PATTERN = Pattern.compile(SSH_PATTERN_STRING);
	private static final Pattern SVN_PATTERN = Pattern.compile(SVN_PATTERN_STRING);

	protected AbstractClient() {
		super();
	}

	public static String obfuscateLoginData(String line) {
		String myLine = line == null? "" : line;
		myLine = HTTP_PATTERN.matcher(myLine).replaceAll("http://***@"); //$NON-NLS-1$
		if (myLine.equals(line)) {
			myLine = HTTPS_PATTERN.matcher(line).replaceAll("https://***@"); //$NON-NLS-1$
		}
		if (myLine.equals(line)) {
			myLine = SSH_PATTERN.matcher(line).replaceAll("ssh://***@"); //$NON-NLS-1$
		}
		if (myLine.equals(line)) {
			myLine = SVN_PATTERN.matcher(line).replaceAll("svn://***@"); //$NON-NLS-1$
		}
		return myLine;
	}

	/**
	 * @param resource
	 * @return hg root as <b>canonical file</b> (see {@link File#getCanonicalFile()})
	 * @throws HgException
	 */
	static HgRoot getHgRoot(IResource resource) throws HgException {
		Assert.isNotNull(resource);
		return MercurialRootCache.getInstance().getHgRoot(resource);
	}

	/**
	 * Checks if the specified resource is an HgRoot. If it is, the HgRoot is returned, otherwise null is returned.
	 */
	public static HgRoot isHgRoot(IResource res) throws HgException {
		Assert.isNotNull(res);
		if(!(res instanceof IContainer)){
			return null;
		}
		IContainer container = (IContainer)res;
		if(container.findMember(".hg") != null){
			return getHgRoot(container);
		}
		return null;
	}

	protected static List<File> toFiles(List<IResource> files) {
		List<File> toFiles = new ArrayList<File>();
		for (IResource r : files) {
			IPath path = ResourceUtils.getPath(r);
			if(!path.isEmpty()) {
				toFiles.add(path.toFile());
			}
		}
		return toFiles;
	}

	protected static File[] toFileArray(List<IResource> files) {
		List<File> list = toFiles(files);

		return list.toArray(new File[list.size()]);
	}

	/**
	 * Checks whether a command is available in installed Mercurial version by
	 * issuing hg help <commandName>. If Mercurial doesn't answer with
	 * "hg: unknown command", it's available
	 *
	 * @param commandName
	 *            the name of the command, e.g. "rebase"
	 * @param extensionEnabler
	 *            the enablement string for an extension, e.g.
	 *            "hgext.bookmarks="
	 * @return true, if command is available
	 */
	public static boolean isCommandAvailable(String commandName,
			String extensionEnabler) {
		boolean returnValue = false;
		// see bug http://bitbucket.org/mercurialeclipse/main/issue/224/
		// If hg command uses non-null directory, which is NOT under the hg control,
		// MercurialTeamProvider.getAndStoreHgRoot() throws an exception
		AbstractShellCommand command = new RootlessHgCommand("help", "Checking availablility of "
				+ commandName);
		if (extensionEnabler != null && extensionEnabler.length() != 0) {
			command.addOptions("--config", "extensions." + extensionEnabler); //$NON-NLS-1$ //$NON-NLS-2$
		}
		command.addOptions(commandName);
		String result;
		try {
			result = new String(command.executeToBytes(10000, false));
			if (result.startsWith("hg: unknown command")) { //$NON-NLS-1$
				returnValue = false;
			} else {
				returnValue = true;
			}
		} catch (HgException e) {
			returnValue = false;
		}
		return returnValue;
	}

	/**
	 * Get a glob pattern to be passed to status or locate to locate the given resource and it's children in history
	 *
	 * @param root Hg root
	 * @param relPath Root relative path
	 * @param file True if the path is a file
	 * @return A glob pattern
	 * @throws HgException
	 */
	public static String getHgResourceSearchPattern(HgRoot root, IPath relPath, boolean file) throws HgException {

		String pattern = null;

		if (file) {
			String val = relPath.toOSString();

			if (val.contains("[")) {
				// The "[" character begins a character class. This matches any single character within the class.
				// The class ends with a "]" character
				pattern = "glob:" + val.replaceAll("\\[", "[\\\\[]");
			} else {
				pattern = "glob:" + val;
			}
		} else {
			String pathString = relPath.toOSString();
			if (pathString.length() > 0) {
				pattern = "glob:" + pathString + System.getProperty("file.separator") + "**";
			} else {
				pattern = "glob:**";
			}
		}

		return pattern;
	}

	/**
	 * Add the proxy-aware repository location to the command
	 * @param repo not null
	 * @param cmd  not null
	 * @return The remote location to pass to hg
	 */
	protected static String setupForRemote(IHgRepositoryLocation repo, com.aragost.javahg.internals.AbstractCommand cmd) throws HgException {
		URI uri = repo.getUri();
		String location;
		if (uri != null && uri.getHost() != null) {
			location = uri.toASCIIString();
			addProxyToHgCommand(uri, cmd);
		} else {
			location = repo.getLocation();
		}
		return location;
	}

	/**
	 * Add the proxy-aware repository location to the command
	 * @param repo not null
	 * @param cmd  not null
	 * @throws HgException
	 * @see {@link #setupForRemote(IHgRepositoryLocation, com.aragost.javahg.internals.AbstractCommand)}
	 */
	protected static void addRepoToHgCommand(IHgRepositoryLocation repo, AbstractShellCommand cmd) throws HgException {
		URI uri = repo.getUri();
		String location;
		if (uri != null && uri.getHost() != null) {
			location = uri.toASCIIString();
			addProxyToHgCommand(uri, cmd);
		} else {
			location = repo.getLocation();
		}
		cmd.addOptions(location);
	}

	private static IProxyData getProxyData(URI repository) {
		IProxyService proxyService = MercurialEclipsePlugin.getDefault().getProxyService();
		// Host can be null URI is a local path
		final String host = repository.getHost();
		if (proxyService == null || host == null) {
			return null;
		}

		// check if there is an applicable proxy for the location
		IProxyData[] proxyData = proxyService.select(repository);

		for (int i = 0; i < proxyData.length; i++) {
			if (proxyData[i].getHost() != null) {
				return proxyData[i];
			}
		}

		return null;
	}

	/**
	 * If a proxy server is necessary to access the pull or push location, this method will add the
	 * respective command options.
	 *
	 * @param repository
	 *            The URI of the repository, never null
	 * @param command
	 *            not null
	 */
	protected static void addProxyToHgCommand(URI repository, AbstractShellCommand command) {
		IProxyData proxy = getProxyData(repository);

		if (proxy == null) {
			return;
		}

		// set the host incl. port
		command.addOptions("--config", "http_proxy.host=" + getProxyHost(proxy)); //$NON-NLS-1$ //$NON-NLS-2$

		// check if authentication is required
		if (proxy.isRequiresAuthentication()) {

			// set the user name if available
			if (proxy.getUserId() != null) {
				command.addOptions("--config", "http_proxy.user=" + proxy.getUserId()); //$NON-NLS-1$ //$NON-NLS-2$
			}

			// set the password if available
			if (proxy.getPassword() != null) {
				command.addOptions("--config", "http_proxy.passwd=" //$NON-NLS-1$ //$NON-NLS-2$
						+ proxy.getPassword());
			}
		}
	}

	/**
	 * If a proxy server is necessary to access the pull or push location, this method will add the
	 * respective command options.
	 *
	 * @param repository
	 *            The URI of the repository, never null
	 * @param command
	 *            not null
	 */
	protected static void addProxyToHgCommand(URI repository, com.aragost.javahg.internals.AbstractCommand command) {
		IProxyData proxy = getProxyData(repository);

		if (proxy == null) {
			return;
		}

		// set the host incl. port
		command.cmdAppend("--config", "http_proxy.host=" + getProxyHost(proxy)); //$NON-NLS-1$ //$NON-NLS-2$

		// check if authentication is required
		if (proxy.isRequiresAuthentication()) {

			// set the user name if available
			if (proxy.getUserId() != null) {
				command.cmdAppend("--config", "http_proxy.user=" + proxy.getUserId()); //$NON-NLS-1$ //$NON-NLS-2$
			}

			// set the password if available
			if (proxy.getPassword() != null) {
				command.cmdAppend("--config", "http_proxy.passwd=" //$NON-NLS-1$ //$NON-NLS-2$
						+ proxy.getPassword());
			}
		}
	}

	/**
	 * Returns the proxy host parameter for the {@code http_proxy.host} configuration.
	 *
	 * @param proxy
	 *            The proxy data.
	 * @return The proxy host.
	 */
	private static String getProxyHost(IProxyData proxy) {
		StringBuilder host = new StringBuilder(proxy.getHost());
		if (proxy.getPort() >= 0) {
			host.append(':').append(proxy.getPort());
		}
		return host.toString();
	}

	protected static boolean isUseExternalMergeTool() {
		return Boolean.valueOf(
				HgClients.getPreference(MercurialPreferenceConstants.PREF_USE_EXTERNAL_MERGE,
						"false")).booleanValue(); //$NON-NLS-1$
	}

	protected static boolean isInsecure() {
		return !Boolean.parseBoolean(HgClients.getPreference(
				MercurialPreferenceConstants.PREF_VERIFY_SERVER_CERTIFICATE, "true"));
	}

	protected static void addInsecurePreference(AbstractShellCommand command) {
		boolean insecure = isInsecure();

		if (insecure) {
			command.addOptions("--insecure"); //$NON-NLS-1$
		}
	}
}
