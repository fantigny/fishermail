package net.anfoya.mail.browser.javafx.settings;

import java.util.Map;
import java.util.concurrent.Future;

import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.anfoya.java.util.concurrent.ThreadPool;
import net.anfoya.mail.service.Contact;
import net.anfoya.mail.service.MailService;
import net.anfoya.mail.service.Message;
import net.anfoya.mail.service.Section;
import net.anfoya.mail.service.Tag;
import net.anfoya.mail.service.Thread;

public class SettingsDialog extends Stage {
	private static final String CSS_DATA = "<style> body {"
			+ " margin: 7;"
			+ " padding: 0;"
			+ " font-family: Arial, Helvetica, sans-serif;"
			+ " font-size: 12px;"
			+ "} </style>";

	private final MailService<? extends Section, ? extends Tag, ? extends Thread, ? extends Message, ? extends Contact> mailService;
	private final EventHandler<ActionEvent> logoutHandler;

	private final ListView<String> taskList;
	private final TabPane tabPane;

	public SettingsDialog(final MailService<? extends Section, ? extends Tag, ? extends Thread, ? extends Message, ? extends Contact> mailService, final EventHandler<ActionEvent> logoutHandler) {
		initStyle(StageStyle.UNIFIED);
		setOnCloseRequest(e -> Settings.getSettings().save());

		this.mailService = mailService;
		this.logoutHandler = logoutHandler;

		taskList = new ListView<String>();
		final Tab taskTab = new Tab("Tasks", taskList);
		ThreadPool.getInstance().setOnChange(map -> refreshTasks(map));

		tabPane = new TabPane(buildSettingsTab(), buildAboutTab(), buildHelpTab(), taskTab);
		tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

		setScene(new Scene(tabPane, 600, 400));
	}

	public void showAbout() {
		tabPane.getSelectionModel().select(2);
		show();
	}

	private Tab buildHelpTab() {
		final WebView help = new WebView();
		help.getEngine().loadContent("<html>"
				+ CSS_DATA
				+ "<h4>Drag to drop</h4>"
				+ "<ul>	<li>when looking for something to do, drag the source (tag, mail, etc) and drag to the dedicated areas on the bottom</li>"
				+ "		<li>disable the toolbar in settings for a <b>full DnD experience</b> yay! :p</li>"
				+ "</ul>"
				+ "<h4>Press Tab</h4>"
				+ "<ul>	<li>once to search mails</li>"
				+ "		<li>again to search tags</li>"
				+ "</ul>"
				+ "<h4>Unread me</h4>"
				+ "<ul>	<li>by dragging the thread to <b>Unread</b> tag</li>"
				+ "</ul>"
				+ "<h4>Double click</h4>"
				+ "<ul>	<li>on the threadlist to <b>reply</b> a thread</li>"
				+ "		<li><b>Reply all</b> to be activated in settings</li>"
				+ "</ul>"
				+ "</html>");

		return new Tab("help", help);
	}

	private Void refreshTasks(final Map<? extends Future<?>, ? extends String> futureDesc) {
		Platform.runLater(() -> {
			final ObservableList<String> items = taskList.getItems();
			items.clear();
			for(final String desc: futureDesc.values()) {
				items.add(desc);
			}
			if (items.isEmpty()) {
				items.add("idle");
			}
		});
		return null;
	}

	private Tab buildAboutTab() {
		final ImageView image = new ImageView(new Image(getClass().getResourceAsStream("/net/anfoya/mail/image/Mail.png")));

		final Text text = new Text("FisherMail 1.0\u03B1\rby Frederic Antigny");
		text.setFont(Font.font("Amble Cn", FontWeight.BOLD, 24));
		text.setFill(Color.web("#bbbbbb"));

		final GridPane gridPane = new GridPane();
		gridPane.setStyle("-fx-background-color: #4d4d4d;");

		gridPane.addColumn(0, image);
		GridPane.setMargin(image, new Insets(20));
		GridPane.setVgrow(image, Priority.ALWAYS);
		GridPane.setValignment(image, VPos.CENTER);

		gridPane.addColumn(1, text);
		GridPane.setVgrow(text, Priority.ALWAYS);
		GridPane.setValignment(text, VPos.CENTER);

		return new Tab("about", gridPane);
	}

	private Tab buildSettingsTab() {
		final Button logoutButton = new Button("logout");
		logoutButton.setOnAction(e -> {
			close();
			logoutHandler.handle(null);
		});

		final Button clearCacheButton = new Button("clear cache");
		clearCacheButton.setOnAction(e -> mailService.clearCache());

		final SwitchButton toolButton = new SwitchButton();
		toolButton.setSwitchOn(Settings.getSettings().showToolbar().get());
		toolButton.switchOnProperty().addListener((ov, o, n) -> Settings.getSettings().showToolbar().set(n));

		final SwitchButton showExcButton = new SwitchButton();
		showExcButton.setSwitchOn(Settings.getSettings().showExcludeBox().get());
		showExcButton.switchOnProperty().addListener((ov, o, n) -> Settings.getSettings().showExcludeBox().set(n));

		final SwitchButton replyAllDblClickButton = new SwitchButton();
		replyAllDblClickButton.setSwitchOn(Settings.getSettings().replyAllDblClick().get());
		replyAllDblClickButton.switchOnProperty().addListener((ov, o, n) -> Settings.getSettings().replyAllDblClick().set(n));

		final SwitchButton archOnDropButton = new SwitchButton();
		archOnDropButton.setSwitchOn(Settings.getSettings().archiveOnDrop().get());
		archOnDropButton.switchOnProperty().addListener((ov, o, n) -> Settings.getSettings().archiveOnDrop().set(n));

		final TextField popupLifetimeField = new TextField("" + Settings.getSettings().popupLifetime().get());
		popupLifetimeField.setPrefColumnCount(3);
		popupLifetimeField.textProperty().addListener((ov, o, n) -> {
			try {
				final int delay = Integer.parseInt(n);
				Settings.getSettings().popupLifetime().set(delay);
			} catch (final Exception e) {
				((StringProperty)ov).setValue(o);
			}
		});

		final GridPane gridPane = new GridPane();
		gridPane.setPadding(new Insets(5));
		gridPane.setVgap(5);
		gridPane.setHgap(10);

		int i = 0;
		gridPane.addRow(i++, new Label("logout from this account"), logoutButton);
		gridPane.addRow(i++, new Label("clear cache"), clearCacheButton);
		gridPane.addRow(i++, new Label("show tool bar"), toolButton);
		gridPane.addRow(i++, new Label("show exclude box (restart needed)"), showExcButton);
		gridPane.addRow(i++, new Label("reply all on thread list double click"), replyAllDblClickButton);
		gridPane.addRow(i++, new Label("archive on drop"), archOnDropButton);
		gridPane.addRow(i++, new Label("popup lifetime in seconds (0 for permanent)"), popupLifetimeField);

		return new Tab("settings", gridPane);
	}
}
