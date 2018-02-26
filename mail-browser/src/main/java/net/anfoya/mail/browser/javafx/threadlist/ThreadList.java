package net.anfoya.mail.browser.javafx.threadlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.emory.mathcs.backport.java.util.concurrent.atomic.AtomicInteger;
import edu.emory.mathcs.backport.java.util.concurrent.atomic.AtomicLong;
import javafx.animation.Animation.Status;
import javafx.collections.ListChangeListener.Change;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import net.anfoya.java.util.VoidCallback;
import net.anfoya.javafx.scene.animation.DelayTimeline;
import net.anfoya.mail.gmail.model.GmailMoreThreads;
import net.anfoya.mail.model.SimpleThread.SortField;
import net.anfoya.mail.service.MailException;
import net.anfoya.mail.service.MailService;
import net.anfoya.mail.service.Tag;
import net.anfoya.mail.service.Thread;
import net.anfoya.tag.model.SpecialTag;

public class ThreadList<T extends Tag, H extends Thread> extends ListView<H> {
	private static final Logger LOGGER = LoggerFactory.getLogger(ThreadList.class);

	private final MailService<?, T, H, ?, ?> mailService;

	private final String draftTagId;
	private final String unreadTagId;

	private final AtomicLong loadTaskId;
	private Task<Set<H>> loadTask;

	private final Set<String> selectedIds;
	private final AtomicInteger selectedIndex;

	private Set<T> includes;
	private Set<T> excludes;
	private SortField sortOrder;
	private String pattern;
	private int page;

	private Runnable loadCallback;
	private Runnable archiveCallback;

	private boolean firstLoad;

	private boolean newFilter;
	private boolean isUnreadList;

	private final Set<H> selectedThreads;

	private VoidCallback<Set<H>> selectCallback;

	private DelayTimeline emptySelectDelay;

	public ThreadList(final MailService<?, T, H, ?, ?> mailService) {
        getStyleClass().add("thread-list");
        setPlaceholder(new Label("empty"));
		this.mailService = mailService;

		draftTagId = mailService.getSpecialTag(SpecialTag.DRAFT).getId();
		unreadTagId = mailService.getSpecialTag(SpecialTag.UNREAD).getId();
		loadTaskId = new AtomicLong();
		selectedThreads = Collections.synchronizedSet(new HashSet<>());

		includes = new LinkedHashSet<>();
		excludes = new LinkedHashSet<>();
		sortOrder = SortField.DATE;
		pattern = "";

		firstLoad = true;

		selectedIds = new HashSet<>();
		selectedIndex = new AtomicInteger(-1);

		setCellFactory(param -> new ThreadListCell<>());

		setOnKeyPressed(e -> handleKey(e));

		getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		getSelectionModel().getSelectedItems().addListener((Change<? extends H> c) -> {
			final Set<H> selectedThreads = c.getList()
					.stream()
					.collect(Collectors.toSet());
			if (emptySelectDelay != null && emptySelectDelay.getStatus() == Status.RUNNING) { //TODO reduce flickering of messages -- to remove
				emptySelectDelay.stop();
			}
			emptySelectDelay = new DelayTimeline(Duration.millis(selectedThreads.isEmpty()? 500: 1), e -> {
				synchronized (selectedThreads) {
					this.selectedThreads.clear();
					this.selectedThreads.addAll(selectedThreads);
				}
				selectCallback.call(getSelectedThreads());
			});
			emptySelectDelay.playFromStart();
		});

		getItems().addListener((final Change<? extends H> c) -> {
			LOGGER.info("updated list size {}", c.getList().size());
			setFocusTraversable(!c.getList().isEmpty());
			restoreSelection();
		});
	}

	private void handleKey(KeyEvent e) {
		if (e.getCode() == KeyCode.BACK_SPACE
				|| e.getCode() == KeyCode.DELETE) {
			archiveCallback.run();
		}
	}

	public void setOnLoad(final Runnable callback) {
		this.loadCallback = callback;
	}

	public void setOnArchive(final Runnable callback) {
		archiveCallback = callback;
	}

	public void sortBy(final SortField order) {
		sortOrder = order;
		load();
	}

	public boolean isDraft() {
		return includes.size() == 1 && includes.iterator().next().getId().equals(draftTagId);
	}

	public void load(final Set<T> includes, final Set<T> excludes, final String pattern) {
		newFilter = !includes.equals(this.includes) || !excludes.equals(this.excludes) || !pattern.equals(this.pattern);
		isUnreadList = includes.size() == 1 && includes.iterator().next().getId().equals(unreadTagId);

		if (newFilter) {
			page = 1;
		}

		this.includes = includes;
		this.excludes = excludes;
		this.pattern = pattern;

		load();
	}

	public Set<T> getThreadsTags() {
		// return all tags available from all threads
		final Set<T> tags = new LinkedHashSet<>();
		for (final H thread : getItems()) {
			for (final String id : thread.getTagIds()) {
				try {
					tags.add(mailService.getTag(id));
				} catch (final MailException e) {
					LOGGER.error("load tag", e);
				}
			}
		}

		return Collections.unmodifiableSet(tags);
	}

	public Set<H> getSelectedThreads() {
		return new HashSet<>(selectedThreads);
	}

	public void setOnSelect(final Runnable callback) {
		getSelectionModel().selectedItemProperty().addListener((ov, n, o) -> callback.run());
	}

	public void setAll(final Set<H> threads) {
		// save current selected thread list and selected index in case of GmailMoreThreads
		selectedIndex.set(getSelectionModel().getSelectedIndex());
		selectedIds.clear();
		selectedIds.addAll(getSelectionModel().getSelectedItems()
				.stream()
				.map(t -> t.getId())
				.collect(Collectors.toSet()));

		// get list
		final List<H> sortedThreads = new ArrayList<>(threads);
		// if unread list we add the older items even if they are read now
		if (isUnreadList && !firstLoad && !newFilter) {
			sortedThreads.addAll(getItems()
					.stream()
					.filter(t -> !threads.contains(t))
					.peek(t -> t.getTagIds().remove(unreadTagId))
					.collect(Collectors.toList()));
		}

		// sort
		Collections.sort(sortedThreads, sortOrder.getComparator());

		// display
		getItems().setAll(sortedThreads);

		// request focus on first load
		if (firstLoad) {
			firstLoad  = false;
			if (focusTraversableProperty().get()) {
				requestFocus();
			}
		}

		loadCallback.run();
	}

	private void restoreSelection() {
		LOGGER.debug("previously selected index ({})", selectedIndex);
		LOGGER.debug("previously selected thread ids {}", selectedIds);

		if (getItems().isEmpty()) {
			getSelectionModel().clearSelection();
			return;
		}

		if (!selectedIds.isEmpty() && selectedIds.iterator().next() == GmailMoreThreads.PAGE_TOKEN_ID) {
			// user clicked "more thread", new selection is the starts of the new set
			selectFirstOfAddedSet();
		} else {
			restoreRegularSelection();
		}

		LOGGER.info("restored selection with list of index {}", getSelectionModel().getSelectedIndices());
	}

	private void restoreRegularSelection() {
		final int index = Math.max(0, selectedIndex.get());
		final boolean wasSingleSelection = selectedIds.size() == 1;
		final boolean isMultipleSelection = !wasSingleSelection && getItems()
				.stream()
				.filter(t -> selectedIds.contains(t.getId()))
				.mapToInt(t -> getItems().indexOf(t))
				.filter(i -> i != -1)
				.count() > 1;

		// try to select the same item(s) as before
		int[] indices = getItems()
				.stream()
				.filter(t -> selectedIds.contains(t.getId()) && (!t.isUnread() || isMultipleSelection))
				.mapToInt(t -> getItems().indexOf(t))
				.filter(i -> i != -1)
				.toArray();

		if (indices.length == 0 && !wasSingleSelection && !isUnreadList) {
			// try to find the closest following unread thread
			indices = getItems().subList(index, getItems().size())
					.stream()
					.filter(t -> !t.isUnread())
					.mapToInt(t -> getItems().indexOf(t))
					.filter(i -> i != -1)
					.findFirst()
					.stream()
					.toArray();
		}

		if (indices.length == 0 && !wasSingleSelection && !isUnreadList) {
			// try to find the closest preceding unread thread
			indices = getItems().subList(0, index)
					.stream()
					.filter(t -> !t.isUnread())
					.sorted(Collections.reverseOrder())
					.mapToInt(t -> getItems().indexOf(t))
					.filter(i -> i != -1)
					.findFirst()
					.stream()
					.toArray();
		}

		getSelectionModel().selectIndices(-1, indices);
	}

	private void selectFirstOfAddedSet() {
		getSelectionModel().selectIndices(selectedIndex.get());
		scrollTo(selectedIndex.get());
	}

	public void setOnSelect(VoidCallback<Set<H>> callback) {
		selectCallback = callback;
	}
}
