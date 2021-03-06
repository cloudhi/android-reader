/*
 * Copyright (C) 2007-2013 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.book;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.geometerplus.fbreader.Paths;
import org.geometerplus.fbreader.bookmodel.BookReadingException;
import org.geometerplus.fbreader.formats.FormatPlugin;
import org.geometerplus.fbreader.formats.PluginCollection;
import org.geometerplus.fbreader.sort.TitledEntity;
import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.image.ZLImage;
import org.geometerplus.zlibrary.core.resources.ZLResource;
import org.geometerplus.zlibrary.core.util.MiscUtil;

public class Book extends TitledEntity {
	public static final String FAVORITE_LABEL = "favorite";
	public static final String READ_LABEL = "read";

	public final ZLFile File;

	private volatile long myId;

	private volatile String myEncoding;
	private volatile String myLanguage;
	private volatile List<Author> myAuthors;
	private volatile List<Tag> myTags;
	private volatile List<String> myLabels;
	private volatile SeriesInfo mySeriesInfo;
	private volatile List<UID> myUids;

	public volatile boolean HasBookmark;

	private volatile boolean myIsSaved;

	private static final WeakReference<ZLImage> NULL_IMAGE = new WeakReference<ZLImage>(null);
	private WeakReference<ZLImage> myCover;

	Book(long id, ZLFile file, String title, String encoding, String language) {
		super(title);
		myId = id;
		File = file;
		myEncoding = encoding;
		myLanguage = language;
		myIsSaved = true;
	}

	public Book(ZLFile file) throws BookReadingException {
		super(null);
		myId = -1;
		final FormatPlugin plugin = getPlugin(file);
		File = plugin.realBookFile(file);
		readMetaInfo(plugin);
		myIsSaved = false;
	}

	public void updateFrom(Book book) {
		if (myId != book.myId) {
			return;
		}
		setTitle(book.getTitle());
		myEncoding = book.myEncoding;
		myLanguage = book.myLanguage;
		myAuthors = book.myAuthors != null ? new ArrayList<Author>(book.myAuthors) : null;
		myTags = book.myTags != null ? new ArrayList<Tag>(book.myTags) : null;
		myLabels = book.myLabels != null ? new ArrayList<String>(book.myLabels) : null;
		mySeriesInfo = book.mySeriesInfo;
		HasBookmark = book.HasBookmark;
	}

	public void reloadInfoFromFile() {
		try {
			readMetaInfo();
		} catch (BookReadingException e) {
			// ignore
		}
	}

	private static FormatPlugin getPlugin(ZLFile file) throws BookReadingException {
		final FormatPlugin plugin = PluginCollection.Instance().getPlugin(file);
		if (plugin == null) {
			throw new BookReadingException("pluginNotFound", file);
		}
		return plugin;
	}

	public FormatPlugin getPlugin() throws BookReadingException {
		return getPlugin(File);
	}

	void readMetaInfo() throws BookReadingException {
		readMetaInfo(getPlugin());
	}

	private void readMetaInfo(FormatPlugin plugin) throws BookReadingException {
		myEncoding = null;
		myLanguage = null;
		setTitle(null);
		myAuthors = null;
		myTags = null;
		mySeriesInfo = null;
		myUids = null;

		myIsSaved = false;

		plugin.readMetaInfo(this);
		if (myUids == null || myUids.isEmpty()) {
			plugin.readUids(this);
		}

		if (isTitleEmpty()) {
			final String fileName = File.getShortName();
			final int index = fileName.lastIndexOf('.');
			setTitle(index > 0 ? fileName.substring(0, index) : fileName);
		}
		final String demoPathPrefix = Paths.mainBookDirectory() + "/Demos/";
		if (File.getPath().startsWith(demoPathPrefix)) {
			final String demoTag = ZLResource.resource("library").getResource("demo").getValue();
			setTitle(getTitle() + " (" + demoTag + ")");
			addTag(demoTag);
		}
	}

	void loadLists(BooksDatabase database) {
		myAuthors = database.listAuthors(myId);
		myTags = database.listTags(myId);
		myLabels = database.listLabels(myId);
		mySeriesInfo = database.getSeriesInfo(myId);
		myUids = database.listUids(myId);
		HasBookmark = database.hasVisibleBookmark(myId);
		myIsSaved = true;
		if (myUids == null || myUids.isEmpty()) {
			try {
				final FormatPlugin plugin = getPlugin();
				if (plugin != null) {
					plugin.readUids(this);
					save(database, false);
				}
			} catch (BookReadingException e) {
			}
		}
	}

	public List<Author> authors() {
		return (myAuthors != null) ? Collections.unmodifiableList(myAuthors) : Collections.<Author>emptyList();
	}

	void addAuthorWithNoCheck(Author author) {
		if (myAuthors == null) {
			myAuthors = new ArrayList<Author>();
		}
		myAuthors.add(author);
	}

	public void removeAllAuthors() {
		if (myAuthors != null) {
			myAuthors = null;
			myIsSaved = false;
		}
	}

	public void addAuthor(Author author) {
		if (author == null) {
			return;
		}
		if (myAuthors == null) {
			myAuthors = new ArrayList<Author>();
			myAuthors.add(author);
			myIsSaved = false;
		} else if (!myAuthors.contains(author)) {
			myAuthors.add(author);
			myIsSaved = false;
		}
	}

	public void addAuthor(String name) {
		addAuthor(name, "");
	}

	public void addAuthor(String name, String sortKey) {
		String strippedName = name;
		strippedName.trim();
		if (strippedName.length() == 0) {
			return;
		}

		String strippedKey = sortKey;
		strippedKey.trim();
		if (strippedKey.length() == 0) {
			int index = strippedName.lastIndexOf(' ');
			if (index == -1) {
				strippedKey = strippedName;
			} else {
				strippedKey = strippedName.substring(index + 1);
				while ((index >= 0) && (strippedName.charAt(index) == ' ')) {
					--index;
				}
				strippedName = strippedName.substring(0, index + 1) + ' ' + strippedKey;
			}
		}

		addAuthor(new Author(strippedName, strippedKey));
	}

	public long getId() {
		return myId;
	}

	@Override
	public void setTitle(String title) {
		if (!getTitle().equals(title)) {
			super.setTitle(title);
			myIsSaved = false;
		}
	}

	public SeriesInfo getSeriesInfo() {
		return mySeriesInfo;
	}

	void setSeriesInfoWithNoCheck(String name, String index) {
		mySeriesInfo = SeriesInfo.createSeriesInfo(name, index);
	}

	public void setSeriesInfo(String name, String index) {
		setSeriesInfo(name, SeriesInfo.createIndex(index));
	}

	public void setSeriesInfo(String name, BigDecimal index) {
		if (mySeriesInfo == null) {
			if (name != null) {
				mySeriesInfo = new SeriesInfo(name, index);
				myIsSaved = false;
			}
		} else if (name == null) {
			mySeriesInfo = null;
			myIsSaved = false;
		} else if (!name.equals(mySeriesInfo.Series.getTitle()) || mySeriesInfo.Index != index) {
			mySeriesInfo = new SeriesInfo(name, index);
			myIsSaved = false;
		}
	}

	@Override
	public String getLanguage() {
		return myLanguage;
	}

	public void setLanguage(String language) {
		if (!MiscUtil.equals(myLanguage, language)) {
			myLanguage = language;
			resetSortKey();
			myIsSaved = false;
		}
	}

	public String getEncoding() {
		if (myEncoding == null) {
			try {
				getPlugin().detectLanguageAndEncoding(this);
			} catch (BookReadingException e) {
			}
			if (myEncoding == null) {
				setEncoding("utf-8");
			}
		}
		return myEncoding;
	}

	public String getEncodingNoDetection() {
		return myEncoding;
	}

	public void setEncoding(String encoding) {
		if (!MiscUtil.equals(myEncoding, encoding)) {
			myEncoding = encoding;
			myIsSaved = false;
		}
	}

	public List<Tag> tags() {
		return myTags != null ? Collections.unmodifiableList(myTags) : Collections.<Tag>emptyList();
	}

	void addTagWithNoCheck(Tag tag) {
		if (myTags == null) {
			myTags = new ArrayList<Tag>();
		}
		myTags.add(tag);
	}

	public void removeAllTags() {
		if (myTags != null) {
			myTags = null;
			myIsSaved = false;
		}
	}

	public void addTag(Tag tag) {
		if (tag != null) {
			if (myTags == null) {
				myTags = new ArrayList<Tag>();
			}
			if (!myTags.contains(tag)) {
				myTags.add(tag);
				myIsSaved = false;
			}
		}
	}

	public void addTag(String tagName) {
		addTag(Tag.getTag(null, tagName));
	}

	public List<String> labels() {
		return myLabels != null ? Collections.unmodifiableList(myLabels) : Collections.<String>emptyList();
	}

	void addLabelWithNoCheck(String label) {
		if (myLabels == null) {
			myLabels = new ArrayList<String>();
		}
		myLabels.add(label);
	}

	public void addLabel(String label) {
		if (myLabels == null) {
			myLabels = new ArrayList<String>();
		}
		if (!myLabels.contains(label)) {
			myLabels.add(label);
			myIsSaved = false;
		}
	}

	public void removeLabel(String label) {
		if (myLabels != null && myLabels.remove(label)) {
			myIsSaved = false;
		}
	}

	public List<UID> uids() {
		return myUids != null ? Collections.unmodifiableList(myUids) : Collections.<UID>emptyList();
	}

	public void addUid(String type, String id) {
		addUid(new UID(type, id));
	}

	void addUidWithNoCheck(UID uid) {
		if (uid == null) {
			return;
		}
		if (myUids == null) {
			myUids = new ArrayList<UID>();
		}
		myUids.add(uid);
	}

	public void addUid(UID uid) {
		if (uid == null) {
			return;
		}
		if (myUids == null) {
			myUids = new ArrayList<UID>();
		}
		if (!myUids.contains(uid)) {
			myUids.add(uid);
			myIsSaved = false;
		}
	}

	public boolean matchesUid(UID uid) {
		return myUids.contains(uid);
	}

	public boolean matches(String pattern) {
		if (MiscUtil.matchesIgnoreCase(getTitle(), pattern)) {
			return true;
		}
		if (mySeriesInfo != null && MiscUtil.matchesIgnoreCase(mySeriesInfo.Series.getTitle(), pattern)) {
			return true;
		}
		if (myAuthors != null) {
			for (Author author : myAuthors) {
				if (MiscUtil.matchesIgnoreCase(author.DisplayName, pattern)) {
					return true;
				}
			}
		}
		if (myTags != null) {
			for (Tag tag : myTags) {
				if (MiscUtil.matchesIgnoreCase(tag.Name, pattern)) {
					return true;
				}
			}
		}
		if (MiscUtil.matchesIgnoreCase(File.getLongName(), pattern)) {
			return true;
		}
		return false;
	}

	boolean save(final BooksDatabase database, boolean force) {
		if (!force && myId != -1 && myIsSaved) {
			return false;
		}

		database.executeAsTransaction(new Runnable() {
			public void run() {
				if (myId >= 0) {
					final FileInfoSet fileInfos = new FileInfoSet(database, File);
					database.updateBookInfo(myId, fileInfos.getId(File), myEncoding, myLanguage, getTitle());
				} else {
					myId = database.insertBookInfo(File, myEncoding, myLanguage, getTitle());
					if (myId != -1 && myVisitedHyperlinks != null) {
						for (String linkId : myVisitedHyperlinks) {
							database.addVisitedHyperlink(myId, linkId);
						}
					}
				}

				long index = 0;
				database.deleteAllBookAuthors(myId);
				for (Author author : authors()) {
					database.saveBookAuthorInfo(myId, index++, author);
				}
				database.deleteAllBookTags(myId);
				for (Tag tag : tags()) {
					database.saveBookTagInfo(myId, tag);
				}
				final List<String> labelsInDb = database.listLabels(myId);
				for (String label : labelsInDb) {
					if (myLabels == null || !myLabels.contains(label)) {
						database.removeLabel(myId, label);
					}
				}
				if (myLabels != null) {
					for (String label : myLabels) {
						database.setLabel(myId, label);
					}
				}
				database.saveBookSeriesInfo(myId, mySeriesInfo);
				database.deleteAllBookUids(myId);
				for (UID uid : uids()) {
					database.saveBookUid(myId, uid);
				}
			}
		});

		myIsSaved = true;
		return true;
	}

	private Set<String> myVisitedHyperlinks;
	private void initHyperlinkSet(BooksDatabase database) {
		if (myVisitedHyperlinks == null) {
			myVisitedHyperlinks = new TreeSet<String>();
			if (myId != -1) {
				myVisitedHyperlinks.addAll(database.loadVisitedHyperlinks(myId));
			}
		}
	}

	boolean isHyperlinkVisited(BooksDatabase database, String linkId) {
		initHyperlinkSet(database);
		return myVisitedHyperlinks.contains(linkId);
	}

	void markHyperlinkAsVisited(BooksDatabase database, String linkId) {
		initHyperlinkSet(database);
		if (!myVisitedHyperlinks.contains(linkId)) {
			myVisitedHyperlinks.add(linkId);
			if (myId != -1) {
				database.addVisitedHyperlink(myId, linkId);
			}
		}
	}

	synchronized ZLImage getCover() {
		if (myCover == NULL_IMAGE) {
			return null;
		} else if (myCover != null) {
			final ZLImage image = myCover.get();
			if (image != null) {
				return image;
			}
		}
		ZLImage image = null;
		try {
			image = getPlugin().readCover(File);
		} catch (BookReadingException e) {
			// ignore
		}
		myCover = image != null ? new WeakReference<ZLImage>(image) : NULL_IMAGE;
		return image;
	}

	@Override
	public int hashCode() {
		return (int)myId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Book)) {
			return false;
		}
		return File.equals(((Book)o).File);
	}

	@Override
	public String toString() {
		return new StringBuilder("Book[")
			.append(File.getPath())
			.append(", ")
			.append(myId)
			.append("]")
			.toString();
	}
}
