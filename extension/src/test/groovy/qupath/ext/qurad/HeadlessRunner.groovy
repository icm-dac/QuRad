package qupath.ext.qurad

import qupath.lib.images.servers.WrappedBufferedImageServer
import qupath.lib.io.PathIO
import qupath.lib.regions.RegionRequest
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType

class HeadlessRunner {

    static void main(String[] args) {
        def opts = [:]
        for (int i = 0; i < args.length - 1; i += 2) opts[args[i].replaceFirst('^--', '')] = args[i + 1]
        if (!opts.image || !opts.objects || !opts.out) {
            println "usage: --image <img> --objects <geojson> --out <csv> [--shape true] [--binWidth 25] [--distance 1] [--classes a,b,c] [--repeat 1] [--timing <csv>] [--json <path>]"
            System.exit(1)
        }

        BufferedImage raw = ImageIO.read(new File(opts.image))
        BufferedImage img = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_RGB)
        def g2d = img.createGraphics()
        g2d.drawImage(raw, 0, 0, null)
        g2d.dispose()
        def server = new WrappedBufferedImageServer(new File(opts.image).getName(), img)
        if (opts.grayOut) {
            def gray = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY)
            int[] rgbs = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth())
            for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                int rgb = rgbs[y * img.getWidth() + x]
                gray.getRaster().setSample(x, y, 0, (299 * ((rgb >> 16) & 0xFF) + 587 * ((rgb >> 8) & 0xFF) + 114 * (rgb & 0xFF)).intdiv(1000))
            }
            ImageIO.write(gray, 'png', new File(opts.grayOut))
        }

        def objects = PathIO.readObjects(new File(opts.objects))
        int repeat = (opts.repeat ?: '1') as int
        def toProcess = []
        for (int r = 0; r < repeat; r++) toProcess.addAll(objects)

        def settings = [
            binWidth       : (opts.binWidth ?: '25') as int,
            voxelArrayShift: 0,
            force2D        : true,
            distances      : [(opts.distance ?: '1') as int],
            angles         : 4
        ]
        def allClasses = ['firstorder', 'shape2D', 'glcm', 'glrlm', 'glszm', 'ngtdm', 'gldm']
        def wanted = opts.classes ? opts.classes.split(',').toList() : allClasses
        def enabledFeatures = [:]
        allClasses.each { enabledFeatures[it] = wanted.contains(it) }
        enabledFeatures['shape'] = (opts.shape ?: 'false') == 'true' || wanted.contains('shape')

        def calc = new RadiomicsCalculator()
        def imageMeta = calc.imageMetadata(server)
        def allResults = []
        def timings = []
        def maskIndex = []
        int skipped = 0
        File maskDir = opts.masks ? new File(opts.masks) : null
        if (maskDir != null) maskDir.mkdirs()

        for (int w = 0; w < 3 && w < toProcess.size(); w++) calc.extractFeatures(server, toProcess[w], settings, enabledFeatures)

        System.gc()
        long t0 = System.nanoTime()
        toProcess.eachWithIndex { obj, idx ->
            long a = System.nanoTime()
            def results = calc.extractFeatures(server, obj, settings, enabledFeatures)
            long b = System.nanoTime()
            if (results.isEmpty()) { skipped++; return }
            results.putAll(imageMeta)
            results['ObjectID'] = obj.getID().toString()
            results['ObjectType'] = obj.isDetection() ? 'Detection' : 'Annotation'
            results['Classification'] = obj.getPathClass()?.toString() ?: 'Unclassified'
            def roi = obj.getROI()
            results['Centroid_X'] = roi.getCentroidX()
            results['Centroid_Y'] = roi.getCentroidY()
            allResults.add(results)
            if (opts.timing) timings.add([idx, results['NumPixels'], (b - a) / 1.0e6])
            if (maskDir != null) {
                def request = RegionRequest.createInstance(server.getPath(), 1.0, roi)
                def region = server.readRegion(request)
                def (vals, matrix, mask) = calc.extractPixelsWithMask(region, roi, request, server.getWidth(), server.getHeight())
                int mh = mask.length, mw = mask[0].length
                int offX = -1, offY = -1
                int rw = Math.min(region.getWidth(), server.getWidth() - request.getX())
                int rh = Math.min(region.getHeight(), server.getHeight() - request.getY())
                def full = calc.rasterizeRoi(roi, request, rw, rh)
                int sx0 = Math.max(0, -request.getX()), sy0 = Math.max(0, -request.getY())
                int minY = Integer.MAX_VALUE
                int minX = Integer.MAX_VALUE
                for (int yy = sy0; yy < rh; yy++) for (int xx = sx0; xx < rw; xx++) if (full.getRaster().getSample(xx, yy, 0) != 0) { if (xx < minX) minX = xx; if (yy < minY) minY = yy }
                def out = new BufferedImage(mw, mh, BufferedImage.TYPE_BYTE_GRAY)
                for (int yy = 0; yy < mh; yy++) for (int xx = 0; xx < mw; xx++) out.getRaster().setSample(xx, yy, 0, mask[yy][xx] ? 255 : 0)
                def f = new File(maskDir, "${idx}.png")
                ImageIO.write(out, 'png', f)
                maskIndex.add([idx, results['ObjectID'], request.getX() + minX, request.getY() + minY, mw, mh, f.getName()])
            }
        }
        long t1 = System.nanoTime()
        double extractSeconds = (t1 - t0) / 1.0e9

        long t2 = System.nanoTime()
        calc.writeCsv(allResults, new File(opts.out))
        long t3 = System.nanoTime()
        double csvSeconds = (t3 - t2) / 1.0e9

        long peakHeap = 0
        ManagementFactory.getMemoryPoolMXBeans().each { pool ->
            if (pool.getType() == MemoryType.HEAP) peakHeap += pool.getPeakUsage().getUsed()
        }
        def record = calc.buildSettingsRecord(server, settings, enabledFeatures, allResults.size())
        record['headless'] = true
        record['processed'] = allResults.size()
        record['skipped'] = skipped
        record['extractSeconds'] = extractSeconds
        record['csvWriteSeconds'] = csvSeconds
        record['objectsPerSecond'] = allResults.size() / extractSeconds
        record['peakHeapMB'] = peakHeap / (1024.0 * 1024.0)
        record['javaVersion'] = System.getProperty('java.version')
        record['availableProcessors'] = Runtime.getRuntime().availableProcessors()
        record['threads'] = 1
        calc.writeSettingsJson(new File(opts.json ?: (opts.out.replaceAll(/\.csv$/, '') + '_settings.json')), record)

        if (maskDir != null) {
            new File(maskDir, 'index.csv').withWriter { w ->
                w.writeLine('index,ObjectID,x0,y0,width,height,file')
                maskIndex.each { w.writeLine(it.join(',')) }
            }
        }
        if (opts.timing) {
            new File(opts.timing).withWriter { w ->
                w.writeLine('index,NumPixels,ms')
                timings.each { w.writeLine(it.join(',')) }
            }
        }
        println "QuRad headless: processed=${allResults.size()} skipped=${skipped} extract=${String.format('%.2f', extractSeconds)}s (${String.format('%.1f', allResults.size() / extractSeconds)} obj/s) csv=${String.format('%.2f', csvSeconds)}s peakHeap=${String.format('%.0f', peakHeap / 1048576.0)}MB"
    }
}
