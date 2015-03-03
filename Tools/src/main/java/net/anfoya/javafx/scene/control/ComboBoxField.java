package net.anfoya.javafx.scene.control;

import java.util.concurrent.atomic.AtomicLong;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyEvent;
import net.anfoya.java.util.concurrent.ThreadPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fantigny
 *
 * @param <T>
 */
public class ComboBoxField<T> extends ComboBox<T> {
	private static final Logger LOGGER = LoggerFactory.getLogger(ComboBoxField.class);

	private volatile T currentValue;
	private volatile T progSetValue;

	private final AtomicLong delayedActionTime;

	private EventHandler<ActionEvent> fieldActionHandler;
	private EventHandler<ActionEvent> listRequestHandler;

	private volatile boolean upHideArmed;

	public ComboBoxField() {
		setEditable(true);

		currentValue = null;
		progSetValue = null;
		delayedActionTime = new AtomicLong(0);

		upHideArmed = false;

		// fire <fieldActionHandler> on ENTER key released
		addEventFilter(KeyEvent.KEY_RELEASED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(final KeyEvent event) {
				switch(event.getCode()) {
				case ENTER:
					LOGGER.debug("filter KEY_RELEASED ENTER");
					fireFieldAction(new ActionEvent(event.getSource(), event.getTarget()));
					break;
				default:
				}
			}
		});

		// arm delayed <fieldActionHandler> on ComboBox::onAction event when showing
		// (to catch mouse click on combobox displayed list)
		setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(final ActionEvent event) {
				if (showingProperty().get()) {
					LOGGER.debug("handle onAction and showing");
					fireDelayedFieldAction(event);
				}
			}
		});

		// discard ENTER, ESCAPE, RIGHT, LEFT KEY_RELEASED
		// cancel delayed <fieldActionHandler> on UP or DOWN KEY_RELEASED
		// fire <listRequestHandler> on DOWN KEY_RELEASED (if list is not showing) or other characters
		getEditor().addEventFilter(KeyEvent.KEY_RELEASED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(final KeyEvent event) {
				switch(event.getCode()) {
				case ENTER: case ESCAPE: case RIGHT: case LEFT: case SHIFT: case ALT: case WINDOWS: case COMMAND: case CONTROL: case TAB:
					LOGGER.debug("editor filter KEY_RELEASED ENTER/ESCAPE/RIGHT/LEFTSHIFT/ALT/WINDOWS/COMMAND/CONTROL/TAB");
					break;
				case UP:
					LOGGER.debug("editor filter KEY_RELEASED UP");
					cancelDelayedFieldAction();
					if (showingProperty().get()) {
						if (getSelectionModel().isEmpty()) {
							LOGGER.debug("editor filter KEY_RELEASED UP and showing no selection");
							hide();
						} else {
							if (!upHideArmed) {
								LOGGER.debug("editor filter KEY_RELEASED UP and showing");
							} else {
								LOGGER.debug("editor filter KEY_RELEASED UP and showing again");
								hide();
							}
							upHideArmed = true;
						}
					}
					break;
				case DOWN:
					LOGGER.debug("editor filter KEY_RELEASED DOWN");
					cancelDelayedFieldAction();
					if (!showingProperty().get()) {
						LOGGER.debug("editor filter KEY_RELEASED DOWN and not showing");
						fireListRequest(new ActionEvent(event.getSource(), event.getTarget()));
					}
					break;
				default:
					LOGGER.debug("editor filter other characters");
					fireListRequest(new ActionEvent(event.getSource(), event.getTarget()));
					break;
				}
			}
		});

		// keep track of the latest text input
		getEditor().textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(final ObservableValue<? extends String> ov, final String oldVal, final String newVal) {
				LOGGER.debug("handle text changed from \"{}\" to \"{}\"", oldVal, newVal);
				currentValue = getConverter().fromString(newVal);
				upHideArmed = false;
			}
		});
	}

	public T getFieldValue() {
		final T value = getValue();
		if (currentValue != null && currentValue.equals(value)) {
			currentValue = value;
		}
		LOGGER.debug("get field value ({})", currentValue);
		return currentValue;
	}

	public void setFieldValue(final T value) {
		LOGGER.debug("set field value ({})", value);
		if (!value.equals(this.progSetValue)) {
			this.progSetValue = value;
			setValue(value);
		}
	}

	public void setOnListRequest(final EventHandler<ActionEvent> handler) {
		this.listRequestHandler = handler;
	}

	public void setOnFieldAction(final EventHandler<ActionEvent> handler) {
		this.fieldActionHandler = handler;
	}

	private void fireListRequest(final ActionEvent event) {
		if (listRequestHandler != null) {
			LOGGER.debug("fire list request");
			listRequestHandler.handle(event);
		}
	}

	private void fireDelayedFieldAction(final ActionEvent event) {
		final long delayedActionTime = System.nanoTime();
		this.delayedActionTime.set(delayedActionTime);
		LOGGER.debug("delayed field action   {}", delayedActionTime);
		ThreadPool.getInstance().submit(new Runnable() {
			@Override
			public void run() {
				try { Thread.sleep(500); }
				catch(final Exception e) { return; }
				if (delayedActionTime == ComboBoxField.this.delayedActionTime.get()) {
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							if (delayedActionTime == ComboBoxField.this.delayedActionTime.get()) {
								LOGGER.debug("fire delayed field action {} after {}ms", delayedActionTime, (System.nanoTime()-delayedActionTime)/1000000);
								fireFieldAction(event);
							}
						}
					});
				}
			}
		});
	}

	private void cancelDelayedFieldAction() {
		if (delayedActionTime.get() != 0) {
			final long actionTime = delayedActionTime.getAndSet(0);
			LOGGER.debug("cancelled field action {}", actionTime);
		}
	}

	private void fireFieldAction(final ActionEvent event) {
		LOGGER.debug("fire field action");
		currentValue = getFieldValue();

		if (currentValue != null && currentValue.equals(progSetValue)) {
			LOGGER.debug("cancelled (set by prog)");
			return;
		}
		progSetValue = currentValue;

		if (fieldActionHandler != null) {
			LOGGER.debug("call field action handler");
			fieldActionHandler.handle(event);
		}
	}
}