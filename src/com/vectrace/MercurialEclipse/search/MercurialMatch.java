package com.vectrace.MercurialEclipse.search;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.Region;
import org.eclipse.search.ui.text.Match;

import com.vectrace.MercurialEclipse.team.MercurialRevisionStorage;

public class MercurialMatch extends Match {

	private int rev = -1;
	private int lineNumber = 0;
	private String extract;
	private MercurialRevisionStorage mercurialRevisionStorage;
	private Region originalLocation;
	private IFile file;

	/**
	 * @param matchRequestor
	 */
	public MercurialMatch(MercurialTextSearchMatchAccess ma) {

		super(ma.getMercurialRevisionStorage(), ma.getMatchOffset(), ma.getMatchLength());
		this.rev = ma.getRev();
		this.lineNumber = ma.getLineNumber();
		this.extract = ma.getExtract();
		this.mercurialRevisionStorage = ma.getMercurialRevisionStorage();
		this.file = ma.getFile();
	}

	/**
	 * @param file
	 */
	public MercurialMatch(IFile file) {
		super(file, -1, -1);
		this.file = file;
	}

	@Override
	public void setOffset(int offset) {
		if (originalLocation == null) {
			// remember the original location before changing it
			originalLocation = new Region(getOffset(), getLength());
		}
		super.setOffset(offset);
	}

	@Override
	public void setLength(int length) {
		if (originalLocation == null) {
			// remember the original location before changing it
			originalLocation = new Region(getOffset(), getLength());
		}
		super.setLength(length);
	}

	public int getOriginalOffset() {
		if (originalLocation != null) {
			return originalLocation.getOffset();
		}
		return getOffset();
	}

	public int getOriginalLength() {
		if (originalLocation != null) {
			return originalLocation.getLength();
		}
		return getLength();
	}

	public IFile getFile() {
		return file;
	}

	public boolean isFileSearch() {
		return lineNumber == 0;
	}

	public Region getOriginalLocation() {
		return originalLocation;
	}

	public void setOriginalLocation(Region originalLocation) {
		this.originalLocation = originalLocation;
	}

	public int getRev() {
		return rev;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public String getExtract() {
		return extract;
	}

	public MercurialRevisionStorage getMercurialRevisionStorage() {
		return mercurialRevisionStorage;
	}

	public void setFile(IFile file) {
		this.file = file;
	}

	public void setRev(int rev) {
		this.rev = rev;
	}

	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}

	public void setExtract(String extract) {
		this.extract = extract;
	}

	public void setMercurialRevisionStorage(
			MercurialRevisionStorage mercurialRevisionStorage) {
		this.mercurialRevisionStorage = mercurialRevisionStorage;
	}

	@Override
	public int getOffset() {
		// TODO Auto-generated method stub
		return super.getOffset();
	}
}
