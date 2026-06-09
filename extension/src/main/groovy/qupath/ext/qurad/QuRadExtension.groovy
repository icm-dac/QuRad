package qupath.ext.qurad

import javafx.scene.control.MenuItem
import qupath.lib.common.Version
import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.extensions.QuPathExtension

class QuRadExtension implements QuPathExtension {

    String name = "QuRad - Radiomics feature extraction"
    String description = "Extracts 120 PyRadiomics-compatible radiomics features from cell detections and annotations."
    Version QuPathVersion = Version.parse("v0.6.0")

    private boolean isInstalled = false

    @Override
    void installExtension(QuPathGUI qupath) {
        if (isInstalled)
            return
        isInstalled = true
        def menu = qupath.getMenu("Extensions>QuRad", true)
        def menuItem = new MenuItem("Extract radiomics features...")
        menuItem.setOnAction(e -> new RadiomicsCommand(qupath).run())
        menu.getItems() << menuItem
    }
}
