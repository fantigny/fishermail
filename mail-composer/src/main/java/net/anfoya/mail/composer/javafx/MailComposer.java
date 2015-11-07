package net.anfoya.mail.composer.javafx;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.anfoya.java.util.concurrent.ThreadPool;
import net.anfoya.mail.browser.javafx.settings.Settings;
import net.anfoya.mail.mime.MessageHelper;
import net.anfoya.mail.service.Contact;
import net.anfoya.mail.service.MailException;
import net.anfoya.mail.service.MailService;
import net.anfoya.mail.service.Message;
import net.anfoya.mail.service.Section;
import net.anfoya.mail.service.Tag;
import net.anfoya.mail.service.Thread;

public class MailComposer<M extends Message, C extends Contact> extends Stage {
	private static final Logger LOGGER = LoggerFactory.getLogger(MailComposer.class);
	private static final int AUTO_SAVE_DELAY = 60; // seconds

	private final MailService<? extends Section, ? extends Tag, ? extends Thread, M, C> mailService;
	private final EventHandler<ActionEvent> updateHandler;
	private final MessageHelper helper;

	private final BorderPane mainPane;

	private final VBox headerBox;
	private final TextField subjectField;

	private final RecipientListPane<C> toListBox;
	private final RecipientListPane<C> ccListBox;
	private final RecipientListPane<C> bccListBox;

	private final MailEditor editor;
	private final BooleanProperty editedProperty;
	private Timer autosaveTimer;

	private final Map<String, C> addressContacts;

	private final Button saveButton;

	private M draft;

	public MailComposer(final MailService<? extends Section, ? extends Tag, ? extends Thread, M, C> mailService, final EventHandler<ActionEvent> updateHandler) {
		super(StageStyle.UNIFIED);
		setTitle("FisherMail");

		editedProperty = new SimpleBooleanProperty(false);
		autosaveTimer = null;

		final Image icon = new Image(getClass().getResourceAsStream("/net/anfoya/mail/image/Mail.png"));
		getIcons().add(icon);

		final Scene scene = new Scene(new BorderPane(), 800, 600);
		scene.getStylesheets().add(getClass().getResource("/net/anfoya/mail/css/Mail.css").toExternalForm());
		scene.getStylesheets().add(getClass().getResource("/net/anfoya/javafx/scene/control/combo_noarrow.css").toExternalForm());
		setScene(scene);

		this.mailService = mailService;
		this.updateHandler = updateHandler;

		// load contacts from server
		addressContacts = new ConcurrentHashMap<String, C>();
		initContacts();

		helper = new MessageHelper();
		mainPane = (BorderPane) getScene().getRoot();
		mainPane.setPadding(new Insets(3));

		toListBox = new RecipientListPane<C>("to: ");
		toListBox.setOnUpdateList(e -> editedProperty.set(true));
		HBox.setHgrow(toListBox, Priority.ALWAYS);

		ccListBox = new RecipientListPane<C>("cc/bcc: ");
		ccListBox.setFocusTraversable(false);
		ccListBox.setOnUpdateList(e -> editedProperty.set(true));
		HBox.setHgrow(ccListBox, Priority.ALWAYS);

		bccListBox = new RecipientListPane<C>("bcc: ");
		bccListBox.setOnUpdateList(e -> editedProperty.set(true));
		HBox.setHgrow(bccListBox, Priority.ALWAYS);

		final Label subject = new Label("subject:");
		subject.setStyle("-fx-text-fill: gray");
		subjectField = new TextField("FisherMail - test");
		subjectField.setStyle("-fx-background-color: transparent");
		subjectField.textProperty().addListener((ov, o, n) -> editedProperty.set(editedProperty.get() || !n.equals(o)));
		final HBox subjectBox = new HBox(0, subject, subjectField);
		subjectBox.setAlignment(Pos.CENTER_LEFT);
		subjectBox.getStyleClass().add("box-underline");
		HBox.setHgrow(subjectField, Priority.ALWAYS);

		headerBox = new VBox(0, toListBox, ccListBox, subjectBox);
		headerBox.setPadding(new Insets(3, 10, 5, 10));
		mainPane.setTop(headerBox);

		ccListBox.focusedProperty().addListener((ov, o, n) -> showBcc());

		editor = new MailEditor();
		editor.editedProperty().addListener((ov, o, n) -> editedProperty.set(editedProperty.get() || n));
		editor.setOnMailtoCallback(p -> {
			try {
				new MailComposer<M, C>(mailService, updateHandler).newMessage(p);
			} catch (final MailException e) {
				LOGGER.error("creating new mail to {}", p, e);
			}
			return null;
		});

		mainPane.setCenter(editor);

		final Button discardButton = new Button("discard");
		discardButton.setOnAction(event -> discardAndClose());

		saveButton = new Button("save");
		saveButton.setOnAction(event -> save());
		saveButton.disableProperty().bind(editedProperty.not());

		final Button sendButton = new Button("send");
		sendButton.setOnAction(e -> sendAndClose());

		final HBox buttonBox = new HBox(10, discardButton, saveButton, sendButton);
		buttonBox.setAlignment(Pos.CENTER_RIGHT);
		buttonBox.setPadding(new Insets(8, 5, 5, 5));
		mainPane.setBottom(buttonBox);

		editedProperty.addListener((ov, o, n) -> {
			saveButton.setText(n? "save": "saved");
			editor.editedProperty().set(n);
			if (n) {
				startAutosave();
			} else {
				stopAutosave();
			}
		});
	}

	@Override
	public void close() {
		stopAutosave();
		super.close();
	}

	private void initContacts() {
		final Task<Set<C>> contactTask = new Task<Set<C>>() {
			@Override
			protected Set<C> call() throws Exception {
				return mailService.getContacts();
			}
		};
		contactTask.setOnSucceeded(e -> {
			for(final C c: contactTask.getValue()) {
				addressContacts.put(c.getEmail(), c);
			}
			toListBox.setAddressContacts(addressContacts);
			ccListBox.setAddressContacts(addressContacts);
			bccListBox.setAddressContacts(addressContacts);

			final C contact = mailService.getContact();
			if (contact.getFullname().isEmpty()) {
				setTitle(getTitle() + " - " + contact.getEmail());
			} else {
				setTitle(getTitle() + " - " + contact.getFullname() + " (" + contact.getEmail() + ")");
			}
		});
		contactTask.setOnFailed(e -> LOGGER.error("loading contacts", e.getSource().getException()));
		ThreadPool.getInstance().submitHigh(contactTask, "loading contacts");
	}

	public void newMessage(final String recipient) throws MailException {
		final InternetAddress to;
		if (recipient == null || recipient.isEmpty()) {
			to = null;
		} else {
			InternetAddress address;
			try {
				address = new InternetAddress(recipient);
			} catch (final AddressException e) {
				address = null;
			}
			to = address;
		}

		final Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				final MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
				message.setContent("", "text/html");
				if (to != null) {
					message.addRecipient(RecipientType.TO, to);
				}
				message.saveChanges();
				draft = mailService.createDraft(null);
				draft.setMimeDraft(message);
				return null;
			}
		};
		task.setOnFailed(e -> LOGGER.error("creating draft", e.getSource().getException()));
		task.setOnSucceeded(e -> initComposer(false, true));
		ThreadPool.getInstance().submitHigh(task, "creating draft");
	}

	public void editOrReply(final String id, boolean all) {
		// try to find a draft with this id
		try {
			draft = mailService.getDraft(id);
		} catch (final MailException e) {
			LOGGER.error("loading draft for id {}", id, e);
		}
		if (draft != null) {
			// edit
			initComposer(false, false);
		} else {
			// reply
			try {
				final M message = mailService.getMessage(id);
				reply(message, all);
			} catch (final MailException e) {
				LOGGER.error("loading message for id {}", id, e);
			}
		}
	}

	public void reply(final M message, final boolean all) {
		final Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				final MimeMessage reply = (MimeMessage) message.getMimeMessage().reply(all);
				reply.setContent(message.getMimeMessage().getContent(), message.getMimeMessage().getContentType());
				reply.saveChanges();
				draft = mailService.createDraft(message);
				draft.setMimeDraft(reply);
				return null;
			}
		};
		task.setOnFailed(event -> LOGGER.error("creating reply draft", event.getSource().getException()));
		task.setOnSucceeded(e -> initComposer(true, true));
		ThreadPool.getInstance().submitHigh(task, "creating reply draft");
	}

	public void forward(final M message) {
		final Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				final MimeMessage forward = new MimeMessage(Session.getDefaultInstance(new Properties()));
				forward.setSubject("Fwd: " + message.getMimeMessage().getSubject());
				forward.setContent(message.getMimeMessage().getContent(), message.getMimeMessage().getContentType());
				forward.saveChanges();
				draft = mailService.createDraft(message);
				draft.setMimeDraft(forward);
				return null;
			}
		};
		task.setOnFailed(event -> LOGGER.error("creating forward draft", event.getSource().getException()));
		task.setOnSucceeded(e -> initComposer(true, true));
		ThreadPool.getInstance().submitHigh(task, "creating forward draft");
	}

	private void initComposer(final boolean quote, final boolean signature) {
		final MimeMessage message = draft.getMimeMessage();
		updateHandler.handle(null);

		try {
			for(final String a: MessageHelper.getMailAddresses(message.getRecipients(MimeMessage.RecipientType.TO))) {
				toListBox.addRecipient(a);
			}
		} catch (final MessagingException e) {
			LOGGER.error("reading recipients");
		}

		boolean displayCC = false;
		try {
			for(final String a: MessageHelper.getMailAddresses(message.getRecipients(MimeMessage.RecipientType.CC))) {
				ccListBox.addRecipient(a);
				displayCC = true;
			}
		} catch (final MessagingException e) {
			LOGGER.error("reading cc list");
		}

		try {
			for(final String a: MessageHelper.getMailAddresses(message.getRecipients(MimeMessage.RecipientType.BCC))) {
				bccListBox.addRecipient(a);
				displayCC = true;
			}
		} catch (final MessagingException e) {
			LOGGER.error("reading bcc list");
		}

		if (displayCC) {
			showBcc();
		}

		String subject;
		try {
			subject = MimeUtility.decodeText(message.getSubject());
		} catch (final Exception e) {
			subject = "";
		}
		subjectField.setText(subject);

		String html;
		try {
			html = helper.toHtml(message);
		} catch (IOException | MessagingException e) {
			html = "";
			LOGGER.error("getting html content", e);
		}
		if (!html.isEmpty() && quote) {
			final StringBuffer sb = new StringBuffer("<br><br>");
			sb.append("<blockquote class='gmail_quote' style='margin:0 0 0 .8ex;border-left:1px #ccc solid;padding-left:1ex'>");
			sb.append(html);
			sb.append("</blockquote>");
			html = sb.toString();
		}
		if (signature) {
			html = "<p>" + Settings.getSettings().htmlSignature().get() + "</p>" + html;
		}
		html = "<style>"
				+ "html,body {"
				+ " line-height: 1em !important;"
				+ " font-size: 14px !important;"
				+ " font-family: Lucida Grande !important;"
				+ " color: #222222 !important;"
				+ " background-color: #FDFDFD !important; }"
				+ "p {"
				+ " padding-left:1ex;"
				+ " margin: 2px 0 !important; }"
				+ "</style>"
				+ html;
		editor.setHtmlText(html);

		show();

		Platform.runLater(() -> {
			if (quote) {
				editor.requestFocus();
			} else {
				toListBox.requestFocus();
			}
		});
	}

	private synchronized void startAutosave() {
		if (autosaveTimer == null) {
			LOGGER.info("start auto save ({}s)", AUTO_SAVE_DELAY);
			autosaveTimer = new Timer("autosave-draft-timer", true);
			autosaveTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					save();
				}
			}, AUTO_SAVE_DELAY * 1000);
		}
	}

	private synchronized void stopAutosave() {
		if (autosaveTimer != null) {
			LOGGER.info("stop auto save");
			autosaveTimer.cancel();
			autosaveTimer = null;
		}
	}

	private void save() {
		stopAutosave();
		LOGGER.info("save draft");
		Platform.runLater(() -> saveButton.setText("saving"));

		final Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
			    draft.setMimeDraft(buildMessage());
				mailService.save(draft);
				return null;
			}
		};
		task.setOnFailed(e -> {
			editedProperty.set(true);
			LOGGER.error("saving draft", e.getSource().getException());
		});
		task.setOnSucceeded(e -> {
			editedProperty.set(false);
			saveButton.setText("saved");
		});
		ThreadPool.getInstance().submitHigh(task, "saving draft");
	}

	private MimeMessage buildMessage() throws MessagingException {
		final String html = editor.getHtmlText();
		LOGGER.debug(html);

		final MimeBodyPart bodyPart = new MimeBodyPart();
		bodyPart.setText(html, StandardCharsets.UTF_8.name(), "html");

		final MimeMultipart multipart = new MimeMultipart();
		multipart.addBodyPart(bodyPart);

		editor.getAttachments().forEach(f -> {
			LOGGER.debug("adding attachment {}", f);
			try {
				final MimeBodyPart part = new MimeBodyPart();
				part.attachFile(f);
				part.setFileName(MimeUtility.encodeText(f.getName()));
				multipart.addBodyPart(part);
			} catch (final Exception e) {
				LOGGER.error("adding attachment {}", f);
			}
		});

		final MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
		message.setSubject(subjectField.getText());
		message.setContent(multipart);

		Address from;
		try {
			final C contact = mailService.getContact();
			if (contact.getFullname().isEmpty()) {
				from = new InternetAddress(contact.getEmail());
			} else {
				from = new InternetAddress(contact.getEmail(), contact.getFullname());
			}
			message.setFrom(from);
		} catch (final UnsupportedEncodingException e) {
			LOGGER.error("loading user data", e);
		}

		for(final String address: toListBox.getRecipients()) {
			if (addressContacts.containsKey(address)) {
				try {
					message.addRecipient(RecipientType.TO, new InternetAddress(address, addressContacts.get(address).getFullname()));
				} catch (final UnsupportedEncodingException e) {
					message.addRecipient(RecipientType.TO, new InternetAddress(address));
				}
			} else {
				message.addRecipient(RecipientType.TO, new InternetAddress(address));
			}
		}
		for(final String address: ccListBox.getRecipients()) {
			if (addressContacts.containsKey(address)) {
				try {
					message.addRecipient(RecipientType.CC, new InternetAddress(address, addressContacts.get(address).getFullname()));
				} catch (final UnsupportedEncodingException e) {
					message.addRecipient(RecipientType.CC, new InternetAddress(address));
				}
			} else {
				message.addRecipient(RecipientType.CC, new InternetAddress(address));
			}
		}
		for(final String address: bccListBox.getRecipients()) {
			if (addressContacts.containsKey(address)) {
				try {
					message.addRecipient(RecipientType.BCC, new InternetAddress(address, addressContacts.get(address).getFullname()));
				} catch (final UnsupportedEncodingException e) {
					message.addRecipient(RecipientType.BCC, new InternetAddress(address));
				}
			} else {
				message.addRecipient(RecipientType.BCC, new InternetAddress(address));
			}
		}

		return message;
	}

	private void sendAndClose() {
		final Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
			    draft.setMimeDraft(buildMessage());
				mailService.send(draft);
				return null;
			}
		};
		task.setOnFailed(e -> LOGGER.error("sending message", e.getSource().getException()));
		task.setOnSucceeded(e -> updateHandler.handle(null));
		ThreadPool.getInstance().submitHigh(task, "sending message");

		close();
	}

	private void discardAndClose() {
		LOGGER.debug("discard draft");
		final Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				mailService.remove(draft);
				return null;
			}
		};
		task.setOnFailed(e -> LOGGER.error("deleting draft {}", draft, e.getSource().getException()));
		task.setOnSucceeded(e -> updateHandler.handle(null));
		ThreadPool.getInstance().submitHigh(task, "deleting draft");

		close();
	}

	private void showBcc() {
		final String cc = "cc: ";
		if (!cc.equals(ccListBox.getTitle())) {
			ccListBox.setTitle(cc);
			ccListBox.setFocusTraversable(true);
			headerBox.getChildren().add(2, bccListBox);
		}
	}
}
