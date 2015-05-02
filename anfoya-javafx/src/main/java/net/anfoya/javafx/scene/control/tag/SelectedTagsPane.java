package net.anfoya.javafx.scene.control.tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.util.Callback;
import net.anfoya.javafx.scene.control.tag.model.Tag;

public class SelectedTagsPane extends FlowPane {

	protected static final String CROSS = " X";
	private Callback<String, Void> delTagCallBack;

	public SelectedTagsPane() {
		setVgap(3);
		setHgap(3);
		setPrefWidth(0);
	}

	public void refresh(final Set<Tag> tags) {
		final List<Button> buttons = new ArrayList<Button>();
		for(final Tag tag: tags) {
			final Button button = new Button(tag.getName() + CROSS);
			button.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(final ActionEvent event) {
					delTagCallBack.call(tag.getName());
				}
			});
			buttons.add(button);
		}
		getChildren().setAll(buttons);
	}

	public void setDelTagCallBack(final Callback<String, Void> callback) {
		this.delTagCallBack = callback;
	}
}
