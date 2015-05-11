package net.anfoya.tag.javafx.scene.dnd;

import java.util.Optional;

import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.layout.GridPane;
import net.anfoya.javafx.scene.dnd.DropArea;
import net.anfoya.tag.model.SimpleSection;
import net.anfoya.tag.model.SimpleTag;
import net.anfoya.tag.service.TagException;
import net.anfoya.tag.service.TagService;

public class ExtItemDropPane<T extends SimpleTag> extends GridPane {
	private final TagService<? extends SimpleSection, T> tagService;

	public ExtItemDropPane(final TagService<? extends SimpleSection, T> tagService, final DataFormat extItemDataFormat) {
		this.tagService = tagService;

		setVgap(2);
		setHgap(2);
		setAlignment(Pos.BOTTOM_CENTER);
		setOpacity(0.9);

		setMaxHeight(100);

		final DropArea newTagArea = new DropArea("add new tag", extItemDataFormat);
		newTagArea.setOnDragDropped(event -> {
			final Dragboard db = event.getDragboard();
			if (db.hasContent(extItemDataFormat)) {
				final T tag = addTag();
				if (tag == null) {
					event.consume();
					return;
				}

				final ClipboardContent content = new ClipboardContent();
				content.put(extItemDataFormat, db.getContent(extItemDataFormat));
				content.put(DndFormat.TAG_DATA_FORMAT, tag);
				db.setContent(content);
			}
		});

		addRow(0, newTagArea);
	}

	private T addTag() {
		String name = "";
		while(name.isEmpty()) {
			final TextInputDialog inputDialog = new TextInputDialog();
			inputDialog.setTitle("Create new tag");
			inputDialog.setHeaderText("");
			inputDialog.setContentText("Tag name");
			final Optional<String> response = inputDialog.showAndWait();
			if (!response.isPresent()) {
				return null;
			}
			name = response.get();
			if (name.length() < 3) {
				final Alert alertDialog = new Alert(AlertType.ERROR);
				alertDialog.setTitle("Create new tag");
				alertDialog.setHeaderText("Section name is too short: " + name);
				alertDialog.setContentText("Section name should be a least 3 letters long.");
				alertDialog.showAndWait();
				name = "";
			}
		}

		try {
			return tagService.addTag(name);
		} catch (final TagException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
}
