package net.anfoya.downloads.javafx.allocine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;
import javafx.util.StringConverter;
import net.anfoya.java.util.concurrent.ThreadPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class AllocineField extends ComboBox<AllocineQsResult> {
	private static final Logger LOGGER = LoggerFactory.getLogger(AllocineField.class);
	private static final String SEARCH_PATTERN = "http://essearch.allocine.net/fr/autocomplete?geo2=83090&q=%s";

	private volatile AllocineQsResult currentQs;
	private volatile AllocineQsResult requestQs;
	private volatile AllocineQsResult searchedQs;

	private final AtomicLong requestQsId;
	private final AtomicLong submitSearchId;

	private Callback<AllocineQsResult, Void> searchCallback;

	public AllocineField() {
		setEditable(true);
		setPromptText("Key in a text and wait for quick search or type <Enter> for full search");
		setCellFactory(new Callback<ListView<AllocineQsResult>, ListCell<AllocineQsResult>>() {
			@Override
			public ListCell<AllocineQsResult> call(final ListView<AllocineQsResult> listView) {
				return new AllocineQsListCell();
			}
		});

		currentQs = null;
		searchedQs = null;
		requestQs = null;
		requestQsId = new AtomicLong(0);
		submitSearchId = new AtomicLong(0);

		// must be a filter to catch ENTER key
		addEventFilter(KeyEvent.KEY_RELEASED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(final KeyEvent event) {
				switch(event.getCode()) {
				case ENTER:
					LOGGER.debug("KEY_PRESSED ENTER");
					submitSearch();
					break;
				default:
				}
			}
		});

		setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(final ActionEvent event) {
				final long submitSearchId = AllocineField.this.submitSearchId.incrementAndGet();
				ThreadPool.getInstance().submit(new Runnable() {
					@Override
					public void run() {
						LOGGER.debug("submit search waiting ({})", submitSearchId);
						try { Thread.sleep(200); }
						catch(final Exception e) { return; }
						// only submit search if this is not a up/down keyboard action, i.e. it is a mouse click
						if (submitSearchId == AllocineField.this.submitSearchId.get()) {
							LOGGER.debug("submiting search ({})", submitSearchId);
							Platform.runLater(new Runnable() {
								@Override
								public void run() {
									submitSearch();
								}
							});
						}
					}
				});
			}
		});

		getEditor().addEventHandler(KeyEvent.KEY_RELEASED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(final KeyEvent event) {
				switch(event.getCode()) {
				case ENTER: case ESCAPE: case RIGHT: case LEFT:
					break;
				case UP:
					LOGGER.debug("editor KEY_PRESSED UP");
					submitSearchId.incrementAndGet();
					break;
				case DOWN:
					LOGGER.debug("editor KEY_PRESSED DOWN");
					submitSearchId.incrementAndGet();
					if (!isShowing()) {
						LOGGER.debug("editor KEY_PRESSED DOWN and showing");
						showQuickSearch();
					}
					break;
				default:
					LOGGER.debug("editor KEY_PRESSED default");
					showQuickSearch();
				}
			}
		});
		getEditor().textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(final ObservableValue<? extends String> ov, final String oldVal, final String newVal) {
				LOGGER.debug("text changed " + newVal);
				currentQs = new AllocineQsResult(newVal);
			}
		});

		setConverter(new StringConverter<AllocineQsResult>() {
			@Override
			public String toString(final AllocineQsResult qsResult) {
				LOGGER.debug("converting qsResult ({})", qsResult);
				return qsResult == null? null: qsResult.toString();
			}
			@Override
			public AllocineQsResult fromString(final String string) {
				LOGGER.debug("converting string ({})", string);
				return string == null? null: new AllocineQsResult(string);
			}
		});
	}

	private synchronized void submitSearch() {
		cancelQuickSearch();
		if (currentQs == null || currentQs.toString().isEmpty()) {
			return;
		}
		if (currentQs.equals(searchedQs)) {
			return;
		}

		final AllocineQsResult qs = getValue();
		if (currentQs.equals(qs)) {
			// actual value of the combo hold a full ResultQs loaded from json data
			currentQs = qs;
		}
		searchedQs = currentQs;

		if (searchCallback != null) {
			searchCallback.call(searchedQs);
		}
	}

	private void cancelQuickSearch() {
		LOGGER.debug("cancel quick search");
		hide();
		requestQsId.incrementAndGet();
	}

	private synchronized void showQuickSearch() {
		LOGGER.debug("submit search \"{}\"", currentQs);

		if (currentQs == null) {
			// nothing to display
			cancelQuickSearch();
			return;
		}

		if (currentQs.equals(requestQs) && !getItems().isEmpty()) {
			// quick search already done
			show();
			return;
		}

		cancelQuickSearch();

		requestQs = currentQs;
		final long requestId = this.requestQsId.incrementAndGet();
		ThreadPool.getInstance().submit(new Runnable() {
			@Override
			public void run() {
				try {
					requestQuickSearch(requestId, requestQs.toString());
				} catch (final InterruptedException e) {
					return;
				} catch (final Exception e) {
					LOGGER.error("loading {}", requestQs.toString(), e);
					return;
				}
			}
		});
	}

	private void requestQuickSearch(final long requestId, final String title) throws InterruptedException, MalformedURLException, IOException {
		LOGGER.debug("request quick search \"{}\" ({}) ", title, requestId);

		if (title.length() < 3) {
			// need more characters
			return;
		}

		// allow user to type more characters
		Thread.sleep(500);
		if (requestId != this.requestQsId.get()) {
			LOGGER.debug("quick search cancelled ({})", requestId);
			return;
		}

		// get a connection
		final String url = String.format(SEARCH_PATTERN, URLEncoder.encode(title, "UTF8"));
		final List<AllocineQsResult> qsResults = new ArrayList<AllocineQsResult>();
		LOGGER.info("request ({}) \"{}\"", requestId, url);
		final BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(url).openStream()));
		if (requestId != this.requestQsId.get()) {
			LOGGER.debug("quick search cancelled ({})", requestId);
			return;
		}

		// read / parse json data
		final JsonArray jsonQsResults = new JsonParser().parse(reader).getAsJsonArray();
		for (final JsonElement jsonElement : jsonQsResults) {
			qsResults.add(new AllocineQsResult(jsonElement.getAsJsonObject()));
		}

		// launch GUI update
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				if (requestId != AllocineField.this.requestQsId.get()) {
					LOGGER.debug("quick search cancelled ({})", requestId);
					return;
				}
				LOGGER.info("displayed ({}) \"{}\"", requestId, url);
				getItems().clear();
				getItems().addAll(qsResults);
				if (!getItems().isEmpty()) {
					show();
				}
			}
		});
	}

	public void setSearchedText(final String searched) {
		final AllocineQsResult searchedQs = new AllocineQsResult(searched);
		if (!searchedQs.equals(this.searchedQs)) {
			LOGGER.debug("set searched ({})", searchedQs);
			this.searchedQs = searchedQs;
			setValue(searchedQs);
		}
	}

	public void setOnSearch(final Callback<AllocineQsResult, Void> callback) {
		searchCallback = callback;
	}
}
