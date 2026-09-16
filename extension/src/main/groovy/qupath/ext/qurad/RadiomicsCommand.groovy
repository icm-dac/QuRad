package qupath.ext.qurad

import javafx.application.Platform
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.dialogs.Dialogs
import qupath.lib.plugins.parameters.ParameterList

class RadiomicsCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(RadiomicsCommand.class)

    private static final String TITLE = "QuRad - Radiomics feature extraction"

    private final QuPathGUI qupath

    RadiomicsCommand(QuPathGUI qupath) {
        this.qupath = qupath
    }

    @Override
    void run() {
        def imageData = qupath.getImageData()
        if (imageData == null) {
            Dialogs.showNoImageError(TITLE)
            return
        }

        def params = new ParameterList()
                .addTitleParameter("Discretization")
                .addIntParameter("binWidth", "Bin width", 25, "", "Intensity discretization width (PyRadiomics default: 25)")
                .addIntParameter("distance", "GLCM distance (pixels)", 1, "px", "Pixel offset between co-occurring pixels (PyRadiomics default: 1)")
                .addTitleParameter("Objects to process")
                .addBooleanParameter("processDetections", "Process detections (cells)", true, "Extract features from detection objects")
                .addBooleanParameter("processAnnotations", "Process annotations (regions)", false, "Extract features from annotation objects")
                .addBooleanParameter("selectedOnly", "Selected objects only", false, "Restrict to currently selected objects")
                .addTitleParameter("Feature classes")
                .addBooleanParameter("firstorder", "First-order (19)", true)
                .addBooleanParameter("shape2D", "Shape 2D (10)", true)
                .addBooleanParameter("glcm", "GLCM (23)", true)
                .addBooleanParameter("glrlm", "GLRLM (16)", true)
                .addBooleanParameter("glszm", "GLSZM (16)", true)
                .addBooleanParameter("ngtdm", "NGTDM (5)", true)
                .addBooleanParameter("gldm", "GLDM (14)", true)
                .addBooleanParameter("shape", "Legacy 3D-named shape (16) - not recommended for 2D images", false,
                        "2D quantities reported under PyRadiomics' 3D shape names; not equivalent to PyRadiomics 3D shape features")
                .addTitleParameter("Output")
                .addBooleanParameter("addToMeasurements", "Add to measurement list", true, "Insert features into QuPath's measurement table")
                .addBooleanParameter("exportCSV", "Export CSV file", true, "Save features to a timestamped CSV file")

        if (!Dialogs.showParameterDialog(TITLE, params))
            return

        boolean processDetections = params.getBooleanParameterValue("processDetections")
        boolean processAnnotations = params.getBooleanParameterValue("processAnnotations")
        boolean selectedOnly = params.getBooleanParameterValue("selectedOnly")
        boolean addToMeasurements = params.getBooleanParameterValue("addToMeasurements")
        boolean exportCSV = params.getBooleanParameterValue("exportCSV")

        def settings = [
                binWidth       : params.getIntParameterValue("binWidth"),
                voxelArrayShift: 0,
                force2D        : true,
                distances      : [Math.max(1, params.getIntParameterValue("distance"))],
                angles         : 4
        ]
        def enabledFeatures = [
                firstorder: params.getBooleanParameterValue("firstorder"),
                shape     : params.getBooleanParameterValue("shape"),
                shape2D   : params.getBooleanParameterValue("shape2D"),
                glcm      : params.getBooleanParameterValue("glcm"),
                glrlm     : params.getBooleanParameterValue("glrlm"),
                glszm     : params.getBooleanParameterValue("glszm"),
                ngtdm     : params.getBooleanParameterValue("ngtdm"),
                gldm      : params.getBooleanParameterValue("gldm")
        ]

        def hierarchy = imageData.getHierarchy()
        def server = imageData.getServer()

        if (!server.isRGB()) {
            Dialogs.showErrorMessage(TITLE, "QuRad requires an 8-bit RGB (brightfield) image. This image has ${server.nChannels()} channel(s) of type ${server.getPixelType()}.")
            return
        }

        List objectsToProcess = []
        if (selectedOnly) {
            objectsToProcess.addAll(hierarchy.getSelectionModel().getSelectedObjects())
        } else {
            if (processAnnotations) objectsToProcess.addAll(hierarchy.getAnnotationObjects())
            if (processDetections) objectsToProcess.addAll(hierarchy.getDetectionObjects())
        }
        objectsToProcess.removeAll([null])

        if (objectsToProcess.isEmpty()) {
            Dialogs.showWarningNotification(TITLE, "No objects to process. Add detections/annotations (or a selection) and try again.")
            return
        }

        def outputDir = resolveOutputDir()

        def worker = {
            try {
                runExtraction(server, hierarchy, objectsToProcess, settings, enabledFeatures,
                        addToMeasurements, exportCSV, outputDir)
            } catch (Exception e) {
                logger.error("QuRad extraction failed", e)
                Platform.runLater {
                    Dialogs.showErrorMessage(TITLE, "Extraction failed: ${e.getMessage()}")
                }
            }
        }
        def thread = new Thread(worker as Runnable, "QuRad-extraction")
        thread.setDaemon(true)
        thread.start()
    }

    private void runExtraction(server, hierarchy, List objectsToProcess, Map settings, Map enabledFeatures,
                               boolean addToMeasurements, boolean exportCSV, File outputDir) {
        def calc = new RadiomicsCalculator()
        def imageMeta = calc.imageMetadata(server)
        def allResults = []
        int processedCount = 0
        int skippedCount = 0
        long startTime = System.currentTimeMillis()
        int total = objectsToProcess.size()
        int progressInterval = 10000

        logger.info("QuRad: processing {} objects", total)

        objectsToProcess.eachWithIndex { pathObject, index ->
            if ((index + 1) % progressInterval == 0) {
                double elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                double rate = elapsed > 0 ? (index + 1) / elapsed : 0
                logger.info("QuRad: processed {}/{} ({} objects/sec)", index + 1, total, String.format('%.1f', rate))
            }
            try {
                def results = calc.extractFeatures(server, pathObject, settings, enabledFeatures)
                if (results.isEmpty()) {
                    skippedCount++
                    return
                }

                results.putAll(imageMeta)
                results['ObjectID'] = pathObject.getID().toString()
                results['ObjectType'] = pathObject.isDetection() ? 'Detection' : 'Annotation'
                results['Classification'] = pathObject.getPathClass()?.toString() ?: 'Unclassified'

                def roi = pathObject.getROI()
                if (roi != null) {
                    results['Centroid_X'] = roi.getCentroidX()
                    results['Centroid_Y'] = roi.getCentroidY()
                }

                if (addToMeasurements) {
                    results.each { k, v ->
                        if (v instanceof Number && !(k in ['Centroid_X', 'Centroid_Y', 'PixelWidth_um', 'PixelHeight_um']))
                            pathObject.measurements.put(k, v.doubleValue())
                    }
                }

                allResults.add(results)
                processedCount++
            } catch (Exception e) {
                skippedCount++
            }
        }

        if (addToMeasurements) {
            Platform.runLater { hierarchy.fireHierarchyChangedEvent(this) }
        }

        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0
        logger.info("QuRad: complete. processed={}, skipped={}, time={}s", processedCount, skippedCount, String.format('%.1f', totalTime))

        if (allResults.isEmpty()) {
            Platform.runLater {
                Dialogs.showWarningNotification(TITLE, "No features were extracted (all objects skipped).")
            }
            return
        }

        String csvMessage = ""
        if (exportCSV) {
            if (!outputDir.exists())
                outputDir.mkdirs()
            def timestamp = String.format('%tY%<tm%<td_%<tH%<tM%<tS', new Date())
            def imageName = server.getMetadata().getName().replaceAll('[^a-zA-Z0-9]', '_')
            def outputFile = calc.writeCsv(allResults, new File(outputDir, "${imageName}_radiomics_${timestamp}.csv"))
            calc.writeSettingsJson(new File(outputDir, "${imageName}_radiomics_${timestamp}_settings.json"),
                    calc.buildSettingsRecord(server, settings, enabledFeatures, allResults.size()))
            csvMessage = "\nCSV: ${outputFile.absolutePath}"
            logger.info("QuRad: wrote {} rows to {}", allResults.size(), outputFile.absolutePath)
        }

        final String message = "Processed ${processedCount} of ${total} objects in ${String.format('%.1f', totalTime)}s.${csvMessage}"
        Platform.runLater {
            Dialogs.showInfoNotification(TITLE, message)
        }
    }

    private File resolveOutputDir() {
        def project = qupath.getProject()
        if (project != null && project.getPath() != null) {
            def base = project.getPath().getParent()
            if (base != null)
                return new File(base.toFile(), "radiomics")
        }
        return new File(System.getProperty("user.home"), "QuRad")
    }
}
