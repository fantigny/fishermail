package net.anfoya.downloads.javafx.allocine;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import javafx.util.StringConverter;
import net.anfoya.java.util.concurrent.ThreadPool;
import net.anfoya.javafx.scene.control.ComboBoxField;
import net.anfoya.movie.connector.AllocineConnector;
import net.anfoya.movie.connector.MovieConnector;
import net.anfoya.movie.connector.QuickSearchVo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuickSearchField extends ComboBoxField<QuickSearchVo> {
	private static final Logger LOGGER = LoggerFactory.getLogger(QuickSearchField.class);
	private volatile QuickSearchVo requestedVo;
	private final AtomicLong requestTime;

	private Callback<QuickSearchVo, Void> searchCallback;

	private final MovieConnector provider = new AllocineConnector();

	public QuickSearchField() {
		setPromptText("Key in a text and wait for quick search or type <Enter> for full search");
		setCellFactory(new Callback<ListView<QuickSearchVo>, ListCell<QuickSearchVo>>() {
			@Override
			public ListCell<QuickSearchVo> call(final ListView<QuickSearchVo> listView) {
				return new QuickSearchListCell();
			}
		});

		requestedVo = null;
		requestTime = new AtomicLong(0);

		setOnFieldAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(final ActionEvent event) {
				submitSearch();
			}
		});

		setOnListRequest(new EventHandler<ActionEvent>() {
			@Override
			public void handle(final ActionEvent event) {
				submitQuickSearch();
			}
		});

		setConverter(new StringConverter<QuickSearchVo>() {
			@Override
			public String toString(final QuickSearchVo qsVo) {
				LOGGER.debug("from value object ({})", qsVo);
				return qsVo == null? null: qsVo.toString();
			}
			@Override
			public QuickSearchVo fromString(final String s) {
				LOGGER.debug("from string \"{}\"", s);
				return s == null? null: new QuickSearchVo(s);
			}
		});
	}

	public void setOnSearch(final Callback<QuickSearchVo, Void> callback) {
		searchCallback = callback;
	}

	public void setSearchedText(final String search) {
		setFieldValue(new QuickSearchVo(search));
	}

	private synchronized void submitSearch() {
		cancelListRequest();
		final QuickSearchVo qs = getFieldValue();
		if (qs == null || qs.toString().isEmpty()) {
			return;
		}

		if (searchCallback != null) {
			searchCallback.call(qs);
		}
	}

	private synchronized void submitQuickSearch() {
		final QuickSearchVo qsVo = getFieldValue();
		LOGGER.debug("request list ({})", qsVo);

		if (qsVo == null) {
			// nothing to display
			LOGGER.debug("cancelled empty request ({})", qsVo);
			cancelListRequest();
			return;
		}

		if (qsVo.equals(requestedVo)) {
			// quick search already done
			LOGGER.debug("cancelled request already ran or running ({})", qsVo);
			if (!isShowing()) {
				show();
			}
			return;
		}
		requestedVo = qsVo;

		cancelListRequest();

		final long requestTime = System.nanoTime();
		this.requestTime.set(requestTime);
		ThreadPool.getInstance().submit(new Runnable() {
			@Override
			public void run() {
				try {
					quickSearch(requestTime, qsVo.toString());
				} catch (final InterruptedException e) {
					return;
				} catch (final Exception e) {
					LOGGER.error("loading \"{}\"", qsVo.toString(), e);
					return;
				}
			}
		});
	}

	private void quickSearch(final long requestId, final String pattern) throws InterruptedException, MalformedURLException, IOException {
		LOGGER.debug("request list ({}) \"{}\"", requestId, pattern);

		// allow user to type more characters
		Thread.sleep(500);
		if (requestId != this.requestTime.get()) {
			LOGGER.debug("request list cancelled ({})", requestId);
			return;
		}

		// search
		final List<QuickSearchVo> qsResults = provider.find(pattern);
		if (requestId != this.requestTime.get()) {
			LOGGER.debug("request list cancelled ({})", requestId);
			return;
		}

		// launch GUI update
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				if (requestId != QuickSearchField.this.requestTime.get()) {
					LOGGER.debug("request list cancelled ({})", requestId);
					return;
				}
				getItems().setAll(qsResults);
				if (!getItems().isEmpty()) {
					show();
				}
				LOGGER.info("request list displayed ({}) \"{}\"", requestId, pattern);
			}
		});
	}

	private void cancelListRequest() {
		LOGGER.debug("cancel quick search");
		requestTime.set(0);
	}
}
