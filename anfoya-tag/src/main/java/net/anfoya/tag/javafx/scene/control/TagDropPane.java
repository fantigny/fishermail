package net.anfoya.tag.javafx.scene.control;

import java.util.Optional;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import net.anfoya.tag.model.SimpleSection;
import net.anfoya.tag.model.SimpleTag;
import net.anfoya.tag.service.TagService;
import net.anfoya.tag.service.TagServiceException;

public class TagDropPane<T extends SimpleTag> extends GridPane {
	protected static final DataFormat DND_TAG_DATA_FORMAT = new DataFormat(SimpleTag.class.getCanonicalName());

	private final TagService<? extends SimpleSection, T> tagService;

	public TagDropPane(final TagService<? extends SimpleSection, T> tagService) {
		this.tagService = tagService;

		setPadding(new Insets(5));
		setVgap(2);
		setHgap(2);
		setAlignment(Pos.BOTTOM_CENTER);
		setOpacity(0.9);

		setMaxHeight(60);

		final HBox removeBox = new HBox(0, new Label("remove"));
		removeBox.setAlignment(Pos.CENTER);
		removeBox.setStyle("-fx-background-color: #DDDDDD; -fx-border-color: black");
		removeBox.setPrefWidth(200);
		removeBox.setPrefHeight(50);
		removeBox.setOnDragEntered(event -> {
			removeBox.setStyle("-fx-background-color: #DDDDDD; -fx-border-color: red");
			event.consume();
		});
		removeBox.setOnDragExited(event -> {
			removeBox.setStyle("-fx-background-color: #DDDDDD; -fx-border-color: black");
			event.consume();
		});
		removeBox.setOnDragOver(event -> {
			if (event.getDragboard().hasContent(DND_TAG_DATA_FORMAT)) {
				event.acceptTransferModes(TransferMode.ANY);
				event.consume();
			}
		});
		removeBox.setOnDragDropped(event -> {
			if (event.getDragboard().hasContent(DND_TAG_DATA_FORMAT)) {
				@SuppressWarnings("unchecked")
				final T tag = (T) event.getDragboard().getContent(DND_TAG_DATA_FORMAT);
				remove(tag);
				event.setDropCompleted(true);
				event.consume();
			}
		});

		final HBox renameBox = new HBox(0, new Label("rename"));
		renameBox.setAlignment(Pos.CENTER);
		renameBox.setStyle("-fx-background-color: #DDDDDD; -fx-border-color: black");
		renameBox.setPrefWidth(200);
		renameBox.setPrefHeight(50);
		renameBox.setOnDragEntered(event -> {
			renameBox.setStyle("-fx-background-color: #DDDDDD; -fx-border-color: red");
			event.consume();
		});
		renameBox.setOnDragExited(event -> {
			renameBox.setStyle("-fx-background-color: #DDDDDD; -fx-border-color: black");
			event.consume();
		});
		renameBox.setOnDragOver(event -> {
			if (event.getDragboard().hasContent(DND_TAG_DATA_FORMAT)) {
				event.acceptTransferModes(TransferMode.ANY);
				event.consume();
			}
		});
		renameBox.setOnDragDropped(event -> {
			if (event.getDragboard().hasContent(DND_TAG_DATA_FORMAT)) {
				@SuppressWarnings("unchecked")
				final T tag = (T) event.getDragboard().getContent(DND_TAG_DATA_FORMAT);
				rename(tag);
				event.setDropCompleted(true);
				event.consume();
			}
		});

		addRow(0, renameBox, removeBox);
	}

	private void rename(final T tag) {
		String name = "";
		while(name.isEmpty()) {
			final TextInputDialog inputDialog = new TextInputDialog();
			inputDialog.setTitle("Create new tag");
			inputDialog.setHeaderText("");
			inputDialog.setContentText("Tag name");
			final Optional<String> response = inputDialog.showAndWait();
			if (!response.isPresent()) {
				return;
			}
			name = response.get();
			if (name.length() < 3) {
				final Alert alertDialog = new Alert(AlertType.ERROR);
				alertDialog.setTitle("Create new tag");
				alertDialog.setHeaderText("Tag name is too short: " + name);
				alertDialog.setContentText("Tag name should be a least 3 letters long.");
				alertDialog.showAndWait();
				name = "";
			}
		}

		try {
			tagService.rename(tag, name);
		} catch (final TagServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void remove(final T tag) {
		final Alert alertDialog = new Alert(AlertType.ERROR);
		alertDialog.setTitle("Remove tag");
		alertDialog.setHeaderText("To be implemented");
		alertDialog.setContentText("");
		alertDialog.showAndWait();
		try {
			tagService.remove(tag);
		} catch (final Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
