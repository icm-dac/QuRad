package qupath.ext.qurad

import qupath.lib.images.servers.WrappedBufferedImageServer
import qupath.lib.io.PathIO
import qupath.lib.regions.RegionRequest
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class Profile {
    static void main(String[] args) {
        BufferedImage raw = ImageIO.read(new File(args[0]))
        BufferedImage img = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_RGB)
        img.createGraphics().drawImage(raw, 0, 0, null)
        def server = new WrappedBufferedImageServer("img", img)
        def objects = PathIO.readObjects(new File(args[1]))
        int n = Math.min(objects.size(), (args.length > 2 ? args[2] as int : 200))
        def calc = new RadiomicsCalculator()
        def settings = [binWidth: 25, voxelArrayShift: 0, force2D: true, distances: [1], angles: 4]
        def times = new LinkedHashMap<String, Long>()
        ['readRegion', 'pixels', 'binEdges', 'firstorder', 'shape2D', 'shape', 'glcm', 'glrlm', 'glszm', 'ngtdm', 'gldm'].each { times[it] = 0L }
        for (int rep = 0; rep < 3; rep++) {
            times.keySet().each { times[it] = 0L }
            for (int i = 0; i < n; i++) {
                def obj = objects[i]
                def roi = obj.getROI()
                long t0 = System.nanoTime()
                def request = RegionRequest.createInstance(server.getPath(), 1.0, roi)
                def region = server.readRegion(request)
                long t1 = System.nanoTime(); times['readRegion'] += t1 - t0
                def (intensities, matrix, mask) = calc.extractPixelsWithMask(region, roi, request, server.getWidth(), server.getHeight())
                long t2 = System.nanoTime(); times['pixels'] += t2 - t1
                def edges = calc.calculateBinEdges(intensities, 25)
                long t3 = System.nanoTime(); times['binEdges'] += t3 - t2
                calc.calculateFirstOrderFeatures(intensities, settings, edges)
                long t4 = System.nanoTime(); times['firstorder'] += t4 - t3
                calc.calculateShape2DFeatures(roi, mask)
                long t5 = System.nanoTime(); times['shape2D'] += t5 - t4
                calc.calculateShape3DFeatures(roi, mask)
                long t6 = System.nanoTime(); times['shape'] += t6 - t5
                calc.calculateGLCMFeatures(matrix, mask, settings, edges)
                long t7 = System.nanoTime(); times['glcm'] += t7 - t6
                calc.calculateGLRLMFeatures(matrix, mask, settings, edges)
                long t8 = System.nanoTime(); times['glrlm'] += t8 - t7
                calc.calculateGLSZMFeatures(matrix, mask, settings, edges)
                long t9 = System.nanoTime(); times['glszm'] += t9 - t8
                calc.calculateNGTDMFeatures(matrix, mask, settings, edges)
                long t10 = System.nanoTime(); times['ngtdm'] += t10 - t9
                calc.calculateGLDMFeatures(matrix, mask, settings, edges)
                long t11 = System.nanoTime(); times['gldm'] += t11 - t10
            }
            long total = times.values().sum()
            println "pass ${rep}: ${n} objects, total ${String.format('%.1f', total / 1e6)} ms (${String.format('%.2f', total / 1e6 / n)} ms/object)"
            times.each { k, v -> println "   ${k.padRight(12)} ${String.format('%8.2f', v / 1e6 / n)} ms/object  ${String.format('%5.1f', 100.0 * v / total)}%" }
        }
    }
}
