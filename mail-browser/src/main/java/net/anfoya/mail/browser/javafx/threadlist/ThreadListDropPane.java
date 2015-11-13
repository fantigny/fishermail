package net.anfoya.mail.browser.javafx.threadlist;

import static net.anfoya.mail.browser.javafx.threadlist.ThreadListPane.DND_THREADS_DATA_FORMAT;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.Callback;
import net.anfoya.javafx.scene.dnd.DropArea;
import net.anfoya.mail.service.MailException;

public class ThreadListDropPane extends GridPane {
	private final DropArea archiveArea;
	private final DropArea replyArea;
	private final DropArea replyAllArea;
	private final DropArea forwardArea;

	public ThreadListDropPane() throws MailException {
		setMaxHeight(200);
		getStyleClass().add("droparea-grid");

		archiveArea = new DropArea("archive", DND_THREADS_DATA_FORMAT);
		replyArea = new DropArea("reply", DND_THREADS_DATA_FORMAT);
		replyAllArea = new DropArea("reply all", DND_THREADS_DATA_FORMAT);
		forwardArea = new DropArea("forward", DND_THREADS_DATA_FORMAT);

		int i=0;
		addRow(i++, replyArea);
		addRow(i++, replyAllArea, forwardArea);
		addRow(i++, archiveArea);

		setColumnSpan(replyArea, 2);
		setHgrow(replyArea, Priority.ALWAYS);
		setVgrow(replyArea, Priority.ALWAYS);

		setHgrow(replyAllArea, Priority.ALWAYS);
		setVgrow(replyAllArea, Priority.ALWAYS);
		setHgrow(forwardArea, Priority.ALWAYS);
		setVgrow(forwardArea, Priority.ALWAYS);

		setColumnSpan(archiveArea, 2);
		setHgrow(archiveArea, Priority.ALWAYS);
		setVgrow(archiveArea, Priority.ALWAYS);
	}

	public void setOnReply(Callback<Boolean, Void> callback) {
		replyArea.setDropHandler(e -> callback.call(false));
		replyAllArea.setDropHandler(e -> callback.call(true));
	}

	public void setOnForward(EventHandler<ActionEvent> handler) {
		forwardArea.setDropHandler(handler);
	}

	public void setOnArchive(final EventHandler<ActionEvent> handler) {
		archiveArea.setDropHandler(handler);
	}
}
