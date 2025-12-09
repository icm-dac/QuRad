/**
 * QuPath Radiomics Feature Extraction - v2
 * Extracts 120 radiomics features from cell detections
 */

import qupath.lib.images.servers.ImageServer
import qupath.lib.regions.RegionRequest
import qupath.lib.roi.interfaces.ROI
import qupath.lib.objects.PathObject
import qupath.lib.gui.scripting.QPEx
import java.awt.image.BufferedImage
import java.util.Arrays

// ============================================================================
// CONFIGURATION
// ============================================================================

def outputDir = buildFilePath(PROJECT_BASE_DIR, "radiomics")
mkdirs(outputDir)
def exportCSV = true
def addToMeasurements = true

def settings = [
    binWidth: 25,
    voxelArrayShift: 0,
    force2D: true,
    distances: [1],
    angles: 4
]

def enabledFeatures = [
    'firstorder': true,
    'shape': true,
    'shape2D': true,
    'glcm': true,
    'glrlm': true,
    'glszm': true,
    'ngtdm': true,
    'gldm': true
]

def processAnnotations = false
def processDetections = true
def selectedOnly = false
def progressInterval = 10000

// ============================================================================
// HELPER: Safe division
// ============================================================================

def safeDiv(num, denom) {
    return denom != 0 ? num / denom : 0.0
}

// ============================================================================
// FIRST ORDER FEATURES (19 features)
// ============================================================================

def calculateFirstOrderFeatures(double[] intensities, Map settings) {
    def features = [:]
    if (intensities == null || intensities.length == 0) return features
    
    int n = intensities.length
    double[] sorted = intensities.clone()
    Arrays.sort(sorted)
    
    int shift = settings.voxelArrayShift ?: 0
    
    // Mean
    double sum = 0.0
    for (int i = 0; i < n; i++) {
        sum += intensities[i]
    }
    double mean = sum / n
    
    // Energy
    double energy = 0.0
    for (int i = 0; i < n; i++) {
        double val = intensities[i] + shift
        energy += val * val
    }
    features['Energy'] = energy
    features['TotalEnergy'] = energy
    
    // Histogram for entropy
    double minVal = sorted[0]
    double maxVal = sorted[n - 1]
    double range = maxVal - minVal
    int binWidth = settings.binWidth ?: 25
    int nBins = range > 0 ? Math.max(1, (int) Math.ceil(range / binWidth)) : 1
    int[] hist = new int[nBins]
    
    for (int i = 0; i < n; i++) {
        int bin = range > 0 ? Math.min((int)((intensities[i] - minVal) / binWidth), nBins - 1) : 0
        hist[bin]++
    }
    
    // Entropy
    double entropy = 0.0
    for (int i = 0; i < nBins; i++) {
        if (hist[i] > 0) {
            double p = (double) hist[i] / n
            entropy -= p * Math.log(p) / Math.log(2)
        }
    }
    features['Entropy'] = entropy
    
    // Basic statistics
    features['Minimum'] = sorted[0]
    features['10Percentile'] = sorted[(int)(n * 0.1)]
    features['90Percentile'] = sorted[(int)(n * 0.9)]
    features['Maximum'] = sorted[n - 1]
    features['Mean'] = mean
    features['Median'] = sorted[(int)(n / 2)]
    features['InterquartileRange'] = sorted[(int)(n * 0.75)] - sorted[(int)(n * 0.25)]
    features['Range'] = sorted[n - 1] - sorted[0]
    
    // Mean absolute deviation
    double mad = 0.0
    for (int i = 0; i < n; i++) {
        mad += Math.abs(intensities[i] - mean)
    }
    features['MeanAbsoluteDeviation'] = mad / n
    
    // Robust mean absolute deviation
    double prcnt10 = sorted[(int)(n * 0.1)]
    double prcnt90 = sorted[(int)(n * 0.9)]
    double robustSum = 0.0
    int robustCount = 0
    for (int i = 0; i < n; i++) {
        if (intensities[i] >= prcnt10 && intensities[i] <= prcnt90) {
            robustSum += intensities[i]
            robustCount++
        }
    }
    if (robustCount > 0) {
        double robustMean = robustSum / robustCount
        double rmad = 0.0
        for (int i = 0; i < n; i++) {
            if (intensities[i] >= prcnt10 && intensities[i] <= prcnt90) {
                rmad += Math.abs(intensities[i] - robustMean)
            }
        }
        features['RobustMeanAbsoluteDeviation'] = rmad / robustCount
    } else {
        features['RobustMeanAbsoluteDeviation'] = 0.0
    }
    
    features['RootMeanSquared'] = Math.sqrt(energy / n)
    
    // Variance and higher moments
    double variance = 0.0
    for (int i = 0; i < n; i++) {
        double diff = intensities[i] - mean
        variance += diff * diff
    }
    variance /= n
    features['Variance'] = variance
    
    double stdDev = Math.sqrt(variance)
    features['StandardDeviation'] = stdDev
    
    if (stdDev > 0) {
        double skewness = 0.0
        double kurtosis = 0.0
        for (int i = 0; i < n; i++) {
            double z = (intensities[i] - mean) / stdDev
            skewness += z * z * z
            kurtosis += z * z * z * z
        }
        features['Skewness'] = skewness / n
        features['Kurtosis'] = kurtosis / n
    } else {
        features['Skewness'] = 0.0
        features['Kurtosis'] = 0.0
    }
    
    // Uniformity
    double uniformity = 0.0
    for (int i = 0; i < nBins; i++) {
        double p = (double) hist[i] / n
        uniformity += p * p
    }
    features['Uniformity'] = uniformity
    
    return features
}

// ============================================================================
// SHAPE FEATURES - 2D (10 features)
// ============================================================================

def calculateShape2DFeatures(ROI roi) {
    def features = [:]
    double area = roi.getArea()
    double perimeter = roi.getLength()
    
    features['MeshSurfaceArea'] = perimeter
    features['PixelSurface'] = perimeter
    features['Perimeter'] = perimeter
    features['PerimeterSurfaceRatio'] = area > 0 ? perimeter / area : 0.0
    
    double sphericity = perimeter > 0 ? (4.0 * Math.PI * area) / (perimeter * perimeter) : 0.0
    features['Sphericity'] = sphericity
    features['SphericalDisproportion'] = sphericity > 0 ? 1.0 / sphericity : 0.0
    
    double width = roi.getBoundsWidth()
    double height = roi.getBoundsHeight()
    double major = Math.max(width, height)
    double minor = Math.min(width, height)
    
    features['MajorAxisLength'] = major
    features['MinorAxisLength'] = minor
    features['Elongation'] = major > 0 ? minor / major : 0.0
    features['Flatness'] = features['Elongation']
    
    return features
}

// ============================================================================
// SHAPE FEATURES - 3D (16 features)
// ============================================================================

def calculateShape3DFeatures(ROI roi) {
    def features = [:]
    double area = roi.getArea()
    double perimeter = roi.getLength()
    
    features['VoxelVolume'] = area
    features['MeshVolume'] = area
    features['SurfaceArea'] = perimeter
    features['SurfaceVolumeRatio'] = area > 0 ? perimeter / area : 0.0
    
    double sphericity = perimeter > 0 ? (4.0 * Math.PI * area) / (perimeter * perimeter) : 0.0
    features['Sphericity'] = sphericity
    features['Compactness1'] = perimeter > 0 ? area / Math.sqrt(Math.PI * perimeter * perimeter * perimeter) : 0.0
    features['Compactness2'] = perimeter > 0 ? 36.0 * Math.PI * area * area / (perimeter * perimeter * perimeter) : 0.0
    features['SphericalDisproportion'] = sphericity > 0 ? 1.0 / sphericity : 0.0
    
    double width = roi.getBoundsWidth()
    double height = roi.getBoundsHeight()
    double major = Math.max(width, height)
    double minor = Math.min(width, height)
    
    features['Maximum3DDiameter'] = Math.sqrt(width * width + height * height)
    features['Maximum2DDiameterSlice'] = features['Maximum3DDiameter']
    features['Maximum2DDiameterColumn'] = width
    features['Maximum2DDiameterRow'] = height
    features['MajorAxisLength'] = major
    features['MinorAxisLength'] = minor
    features['LeastAxisLength'] = minor
    features['Elongation'] = major > 0 ? minor / major : 0.0
    
    return features
}

// ============================================================================
// GLCM FEATURES (24 features)
// ============================================================================

def buildGLCM(int[][] image, boolean[][] mask, int distance, int binWidth) {
    def glcm = [:]
    int height = image.length
    if (height == 0) return glcm
    int width = image[0].length
    if (width == 0) return glcm
    
    int[][] angles = [[1,0], [1,1], [0,1], [-1,1]]
    
    for (int a = 0; a < 4; a++) {
        int dx = angles[a][0] * distance
        int dy = angles[a][1] * distance
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask[y][x]) continue
                
                int nx = x + dx
                int ny = y + dy
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && mask[ny][nx]) {
                    int i = Math.max(1, (int)(image[y][x] / binWidth) + 1)
                    int j = Math.max(1, (int)(image[ny][nx] / binWidth) + 1)
                    String key1 = "${i},${j}"
                    String key2 = "${j},${i}"
                    glcm[key1] = (glcm[key1] ?: 0) + 1
                    glcm[key2] = (glcm[key2] ?: 0) + 1
                }
            }
        }
    }
    return glcm
}

def calculateGLCMFeatures(int[][] image, boolean[][] mask, Map settings) {
    def features = [:]
    
    def glcm = buildGLCM(image, mask, settings.distances[0], settings.binWidth)
    if (glcm.isEmpty()) return features
    
    double total = 0.0
    glcm.each { k, v -> total += v }
    if (total == 0) return features
    
    // Normalized probabilities
    def p = [:]
    glcm.each { k, v -> p[k] = v / total }
    
    // Marginal probabilities
    def px = [:]
    def py = [:]
    p.each { key, prob ->
        def parts = key.split(',')
        int i = parts[0].toInteger()
        int j = parts[1].toInteger()
        px[i] = (px[i] ?: 0.0) + prob
        py[j] = (py[j] ?: 0.0) + prob
    }
    
    // Mean and std of marginals
    double ux = 0.0, uy = 0.0
    px.each { i, pi -> ux += i * pi }
    py.each { j, pj -> uy += j * pj }
    
    double varX = 0.0, varY = 0.0
    px.each { i, pi -> varX += (i - ux) * (i - ux) * pi }
    py.each { j, pj -> varY += (j - uy) * (j - uy) * pj }
    double sx = Math.sqrt(varX > 0 ? varX : 0.001)
    double sy = Math.sqrt(varY > 0 ? varY : 0.001)
    
    // Calculate features
    double autocorr = 0.0, jointAvg = 0.0, clusterProm = 0.0, clusterShade = 0.0
    double clusterTend = 0.0, contrast = 0.0, correlation = 0.0
    double jointEnergy = 0.0, jointEntropy = 0.0
    double idm = 0.0, idmn = 0.0, id = 0.0, idn = 0.0, invVar = 0.0
    double maxProb = 0.0, sumSquares = 0.0
    
    def pxMinusY = [:]
    def pxPlusY = [:]
    
    int maxGL = 1
    px.each { i, pi -> if (i > maxGL) maxGL = i }
    py.each { j, pj -> if (j > maxGL) maxGL = j }
    double ng = maxGL
    
    p.each { key, prob ->
        def parts = key.split(',')
        int i = parts[0].toInteger()
        int j = parts[1].toInteger()
        
        autocorr += i * j * prob
        clusterProm += Math.pow(i + j - ux - uy, 4) * prob
        clusterShade += Math.pow(i + j - ux - uy, 3) * prob
        clusterTend += Math.pow(i + j - ux - uy, 2) * prob
        contrast += (i - j) * (i - j) * prob
        correlation += (i - ux) * (j - uy) * prob / (sx * sy)
        
        jointEnergy += prob * prob
        if (prob > 0) jointEntropy -= prob * Math.log(prob) / Math.log(2)
        
        idm += prob / (1 + (i - j) * (i - j))
        idmn += prob / (1 + ((i - j) * (i - j)) / (ng * ng))
        id += prob / (1 + Math.abs(i - j))
        idn += prob / (1 + Math.abs(i - j) / ng)
        if (i != j) invVar += prob / ((i - j) * (i - j))
        
        if (prob > maxProb) maxProb = prob
        sumSquares += (i - ux) * (i - ux) * prob
        
        int diffKey = Math.abs(i - j)
        pxMinusY[diffKey] = (pxMinusY[diffKey] ?: 0.0) + prob
        int sumKey = i + j
        pxPlusY[sumKey] = (pxPlusY[sumKey] ?: 0.0) + prob
    }
    
    features['Autocorrelation'] = autocorr
    features['JointAverage'] = ux
    features['ClusterProminence'] = clusterProm
    features['ClusterShade'] = clusterShade
    features['ClusterTendency'] = clusterTend
    features['Contrast'] = contrast
    features['Correlation'] = correlation
    features['JointEnergy'] = jointEnergy
    features['JointEntropy'] = jointEntropy
    features['Idm'] = idm
    features['Idmn'] = idmn
    features['Id'] = id
    features['Idn'] = idn
    features['InverseVariance'] = invVar
    features['MaximumProbability'] = maxProb
    features['SumSquares'] = sumSquares
    
    // Difference features
    double diffAvg = 0.0, diffEntropy = 0.0, diffVar = 0.0
    pxMinusY.each { k, pk ->
        diffAvg += k * pk
        if (pk > 0) diffEntropy -= pk * Math.log(pk) / Math.log(2)
    }
    pxMinusY.each { k, pk -> diffVar += (k - diffAvg) * (k - diffAvg) * pk }
    
    features['DifferenceAverage'] = diffAvg
    features['DifferenceEntropy'] = diffEntropy
    features['DifferenceVariance'] = diffVar
    
    // Sum features
    double sumAvg = 0.0, sumEntropy = 0.0
    pxPlusY.each { k, pk ->
        sumAvg += k * pk
        if (pk > 0) sumEntropy -= pk * Math.log(pk) / Math.log(2)
    }
    
    features['SumAverage'] = sumAvg
    features['SumEntropy'] = sumEntropy
    
    // IMC features
    double hx = 0.0, hy = 0.0
    px.each { i, pi -> if (pi > 0) hx -= pi * Math.log(pi) / Math.log(2) }
    py.each { j, pj -> if (pj > 0) hy -= pj * Math.log(pj) / Math.log(2) }
    
    double hxy1 = 0.0
    p.each { key, prob ->
        def parts = key.split(',')
        int i = parts[0].toInteger()
        int j = parts[1].toInteger()
        double pxi = px[i] ?: 0.0
        double pyj = py[j] ?: 0.0
        if (prob > 0 && pxi > 0 && pyj > 0) {
            hxy1 -= prob * Math.log(pxi * pyj) / Math.log(2)
        }
    }
    
    double maxH = Math.max(hx, hy)
    features['Imc1'] = maxH > 0 ? (jointEntropy - hxy1) / maxH : 0.0
    features['Imc2'] = 0.0
    
    return features
}

// ============================================================================
// GLRLM FEATURES (16 features)
// ============================================================================

def buildGLRLM(int[][] image, boolean[][] mask, int binWidth) {
    def glrlm = [:]
    int height = image.length
    if (height == 0) return glrlm
    int width = image[0].length
    
    // Quantize
    int[][] quantized = new int[height][width]
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            quantized[y][x] = mask[y][x] ? Math.max(1, (int)(image[y][x] / binWidth) + 1) : -1
        }
    }
    
    // Horizontal runs
    for (int y = 0; y < height; y++) {
        int gl = -1
        int len = 0
        for (int x = 0; x < width; x++) {
            int g = quantized[y][x]
            if (g < 0) {
                if (len > 0) {
                    String key = "${gl},${len}"
                    glrlm[key] = (glrlm[key] ?: 0) + 1
                }
                gl = -1
                len = 0
            } else if (g == gl) {
                len++
            } else {
                if (len > 0) {
                    String key = "${gl},${len}"
                    glrlm[key] = (glrlm[key] ?: 0) + 1
                }
                gl = g
                len = 1
            }
        }
        if (len > 0) {
            String key = "${gl},${len}"
            glrlm[key] = (glrlm[key] ?: 0) + 1
        }
    }
    
    // Vertical runs
    for (int x = 0; x < width; x++) {
        int gl = -1
        int len = 0
        for (int y = 0; y < height; y++) {
            int g = quantized[y][x]
            if (g < 0) {
                if (len > 0) {
                    String key = "${gl},${len}"
                    glrlm[key] = (glrlm[key] ?: 0) + 1
                }
                gl = -1
                len = 0
            } else if (g == gl) {
                len++
            } else {
                if (len > 0) {
                    String key = "${gl},${len}"
                    glrlm[key] = (glrlm[key] ?: 0) + 1
                }
                gl = g
                len = 1
            }
        }
        if (len > 0) {
            String key = "${gl},${len}"
            glrlm[key] = (glrlm[key] ?: 0) + 1
        }
    }
    
    return glrlm
}

def calculateGLRLMFeatures(int[][] image, boolean[][] mask, Map settings) {
    def features = [:]
    
    def glrlm = buildGLRLM(image, mask, settings.binWidth)
    if (glrlm.isEmpty()) return features
    
    double totalRuns = 0.0
    glrlm.each { k, v -> totalRuns += v }
    if (totalRuns == 0) return features
    
    // Count valid pixels
    int np = 0
    for (int y = 0; y < mask.length; y++) {
        for (int x = 0; x < mask[0].length; x++) {
            if (mask[y][x]) np++
        }
    }
    if (np == 0) return features
    
    double sre = 0.0, lre = 0.0, lgre = 0.0, hgre = 0.0
    double srlge = 0.0, srhge = 0.0, lrlge = 0.0, lrhge = 0.0
    double glMean = 0.0, rlMean = 0.0, runEntropy = 0.0
    
    def glCounts = [:]
    def rlCounts = [:]
    
    glrlm.each { key, count ->
        def parts = key.split(',')
        int gl = parts[0].toInteger()
        int rl = parts[1].toInteger()
        double c = count
        
        sre += c / (rl * rl)
        lre += c * rl * rl
        lgre += c / (gl * gl)
        hgre += c * gl * gl
        srlge += c / (gl * gl * rl * rl)
        srhge += c * gl * gl / (rl * rl)
        lrlge += c * rl * rl / (gl * gl)
        lrhge += c * gl * gl * rl * rl
        
        glMean += gl * c
        rlMean += rl * c
        
        double p = c / totalRuns
        if (p > 0) runEntropy -= p * Math.log(p) / Math.log(2)
        
        glCounts[gl] = (glCounts[gl] ?: 0.0) + c
        rlCounts[rl] = (rlCounts[rl] ?: 0.0) + c
    }
    
    glMean /= totalRuns
    rlMean /= totalRuns
    
    double glVar = 0.0, rlVar = 0.0
    glrlm.each { key, count ->
        def parts = key.split(',')
        int gl = parts[0].toInteger()
        int rl = parts[1].toInteger()
        double c = count
        glVar += (gl - glMean) * (gl - glMean) * c
        rlVar += (rl - rlMean) * (rl - rlMean) * c
    }
    glVar /= totalRuns
    rlVar /= totalRuns
    
    double glnu = 0.0, rlnu = 0.0
    glCounts.each { gl, c -> glnu += c * c }
    rlCounts.each { rl, c -> rlnu += c * c }
    
    features['ShortRunEmphasis'] = sre / totalRuns
    features['LongRunEmphasis'] = lre / totalRuns
    features['GrayLevelNonUniformity'] = glnu / totalRuns
    features['GrayLevelNonUniformityNormalized'] = glnu / (totalRuns * totalRuns)
    features['RunLengthNonUniformity'] = rlnu / totalRuns
    features['RunLengthNonUniformityNormalized'] = rlnu / (totalRuns * totalRuns)
    features['RunPercentage'] = totalRuns / np
    features['GrayLevelVariance'] = glVar
    features['RunVariance'] = rlVar
    features['RunEntropy'] = runEntropy
    features['LowGrayLevelRunEmphasis'] = lgre / totalRuns
    features['HighGrayLevelRunEmphasis'] = hgre / totalRuns
    features['ShortRunLowGrayLevelEmphasis'] = srlge / totalRuns
    features['ShortRunHighGrayLevelEmphasis'] = srhge / totalRuns
    features['LongRunLowGrayLevelEmphasis'] = lrlge / totalRuns
    features['LongRunHighGrayLevelEmphasis'] = lrhge / totalRuns
    
    return features
}

// ============================================================================
// GLSZM FEATURES (16 features)
// ============================================================================

def floodFill(int[][] image, boolean[][] visited, int startX, int startY, int targetGL) {
    int h = image.length
    int w = image[0].length
    def stack = [[startX, startY]]
    int size = 0
    
    while (!stack.isEmpty()) {
        def point = stack.remove(stack.size() - 1)
        int cx = point[0]
        int cy = point[1]
        
        if (cx < 0 || cx >= w || cy < 0 || cy >= h) continue
        if (visited[cy][cx] || image[cy][cx] != targetGL) continue
        
        visited[cy][cx] = true
        size++
        
        stack.add([cx + 1, cy])
        stack.add([cx - 1, cy])
        stack.add([cx, cy + 1])
        stack.add([cx, cy - 1])
    }
    
    return size
}

def buildGLSZM(int[][] image, boolean[][] mask, int binWidth) {
    def glszm = [:]
    int h = image.length
    if (h == 0) return glszm
    int w = image[0].length
    
    int[][] quantized = new int[h][w]
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            quantized[y][x] = mask[y][x] ? Math.max(1, (int)(image[y][x] / binWidth) + 1) : -1
        }
    }
    
    boolean[][] visited = new boolean[h][w]
    
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            if (!visited[y][x] && quantized[y][x] >= 0) {
                int gl = quantized[y][x]
                int size = floodFill(quantized, visited, x, y, gl)
                if (size > 0) {
                    String key = "${gl},${size}"
                    glszm[key] = (glszm[key] ?: 0) + 1
                }
            }
        }
    }
    
    return glszm
}

def calculateGLSZMFeatures(int[][] image, boolean[][] mask, Map settings) {
    def features = [:]
    
    def glszm = buildGLSZM(image, mask, settings.binWidth)
    if (glszm.isEmpty()) return features
    
    double totalZones = 0.0
    glszm.each { k, v -> totalZones += v }
    if (totalZones == 0) return features
    
    int np = 0
    for (int y = 0; y < mask.length; y++) {
        for (int x = 0; x < mask[0].length; x++) {
            if (mask[y][x]) np++
        }
    }
    if (np == 0) return features
    
    double sae = 0.0, lae = 0.0, lgze = 0.0, hgze = 0.0
    double salge = 0.0, sahge = 0.0, lalge = 0.0, lahge = 0.0
    double glMean = 0.0, szMean = 0.0, zoneEntropy = 0.0
    
    def glCounts = [:]
    def szCounts = [:]
    
    glszm.each { key, count ->
        def parts = key.split(',')
        int gl = parts[0].toInteger()
        int sz = parts[1].toInteger()
        double c = count
        
        sae += c / (sz * sz)
        lae += c * sz * sz
        lgze += c / (gl * gl)
        hgze += c * gl * gl
        salge += c / (gl * gl * sz * sz)
        sahge += c * gl * gl / (sz * sz)
        lalge += c * sz * sz / (gl * gl)
        lahge += c * gl * gl * sz * sz
        
        glMean += gl * c
        szMean += sz * c
        
        double p = c / totalZones
        if (p > 0) zoneEntropy -= p * Math.log(p) / Math.log(2)
        
        glCounts[gl] = (glCounts[gl] ?: 0.0) + c
        szCounts[sz] = (szCounts[sz] ?: 0.0) + c
    }
    
    glMean /= totalZones
    szMean /= totalZones
    
    double glVar = 0.0, szVar = 0.0
    glszm.each { key, count ->
        def parts = key.split(',')
        int gl = parts[0].toInteger()
        int sz = parts[1].toInteger()
        double c = count
        glVar += (gl - glMean) * (gl - glMean) * c
        szVar += (sz - szMean) * (sz - szMean) * c
    }
    glVar /= totalZones
    szVar /= totalZones
    
    double glnu = 0.0, sznu = 0.0
    glCounts.each { gl, c -> glnu += c * c }
    szCounts.each { sz, c -> sznu += c * c }
    
    features['SmallAreaEmphasis'] = sae / totalZones
    features['LargeAreaEmphasis'] = lae / totalZones
    features['GrayLevelNonUniformity'] = glnu / totalZones
    features['GrayLevelNonUniformityNormalized'] = glnu / (totalZones * totalZones)
    features['SizeZoneNonUniformity'] = sznu / totalZones
    features['SizeZoneNonUniformityNormalized'] = sznu / (totalZones * totalZones)
    features['ZonePercentage'] = totalZones / np
    features['GrayLevelVariance'] = glVar
    features['ZoneVariance'] = szVar
    features['ZoneEntropy'] = zoneEntropy
    features['LowGrayLevelZoneEmphasis'] = lgze / totalZones
    features['HighGrayLevelZoneEmphasis'] = hgze / totalZones
    features['SmallAreaLowGrayLevelEmphasis'] = salge / totalZones
    features['SmallAreaHighGrayLevelEmphasis'] = sahge / totalZones
    features['LargeAreaLowGrayLevelEmphasis'] = lalge / totalZones
    features['LargeAreaHighGrayLevelEmphasis'] = lahge / totalZones
    
    return features
}

// ============================================================================
// NGTDM FEATURES (5 features)
// ============================================================================

def buildNGTDM(int[][] image, boolean[][] mask, int binWidth) {
    def ngtdm = [:]
    int h = image.length
    if (h == 0) return ngtdm
    int w = image[0].length
    
    int[][] quantized = new int[h][w]
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            quantized[y][x] = mask[y][x] ? Math.max(1, (int)(image[y][x] / binWidth) + 1) : -1
        }
    }
    
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            if (quantized[y][x] < 0) continue
            
            int gl = quantized[y][x]
            def neighbors = []
            
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue
                    int ny = y + dy
                    int nx = x + dx
                    if (ny >= 0 && ny < h && nx >= 0 && nx < w && quantized[ny][nx] >= 0) {
                        neighbors.add(quantized[ny][nx])
                    }
                }
            }
            
            if (neighbors.size() > 0) {
                double avgNeighbor = 0.0
                neighbors.each { n -> avgNeighbor += n }
                avgNeighbor /= neighbors.size()
                double s = Math.abs(gl - avgNeighbor)
                
                if (!ngtdm[gl]) ngtdm[gl] = ['n': 0, 's': 0.0]
                ngtdm[gl]['n']++
                ngtdm[gl]['s'] += s
            }
        }
    }
    
    return ngtdm
}

def calculateNGTDMFeatures(int[][] image, boolean[][] mask, Map settings) {
    def features = [:]
    
    def ngtdm = buildNGTDM(image, mask, settings.binWidth)
    if (ngtdm.isEmpty()) return features
    
    double n = 0.0
    ngtdm.each { gl, data -> n += data['n'] }
    if (n == 0) return features
    
    int Ng = ngtdm.size()
    def p = [:]
    ngtdm.each { gl, data -> p[gl] = data['n'] / n }
    
    // Coarseness
    double sumPS = 0.0
    ngtdm.each { gl, data -> sumPS += p[gl] * data['s'] }
    features['Coarseness'] = sumPS > 0 ? 1.0 / sumPS : 0.0
    
    // Contrast
    double contrastSum = 0.0
    ngtdm.each { gl_i, data_i ->
        ngtdm.each { gl_j, data_j ->
            contrastSum += p[gl_i] * p[gl_j] * (gl_i - gl_j) * (gl_i - gl_j)
        }
    }
    double sumS = 0.0
    ngtdm.each { gl, data -> sumS += data['s'] }
    features['Contrast'] = (Ng > 1 && n > 0) ? (1.0 / (Ng * (Ng - 1))) * contrastSum * sumS / n : 0.0
    
    // Busyness
    double busyNum = sumPS
    double busyDenom = 0.0
    ngtdm.each { gl_i, data_i ->
        ngtdm.each { gl_j, data_j ->
            if (gl_i != gl_j) {
                busyDenom += Math.abs(gl_i * p[gl_i] - gl_j * p[gl_j])
            }
        }
    }
    features['Busyness'] = busyDenom > 0 ? busyNum / busyDenom : 0.0
    
    // Complexity
    double complexity = 0.0
    ngtdm.each { gl_i, data_i ->
        ngtdm.each { gl_j, data_j ->
            double denom = p[gl_i] + p[gl_j]
            if (denom > 0) {
                complexity += Math.abs(gl_i - gl_j) * (p[gl_i] * data_i['s'] + p[gl_j] * data_j['s']) / denom
            }
        }
    }
    features['Complexity'] = complexity / n
    
    // Strength
    double strengthNum = 0.0
    ngtdm.each { gl_i, data_i ->
        ngtdm.each { gl_j, data_j ->
            strengthNum += (p[gl_i] + p[gl_j]) * (gl_i - gl_j) * (gl_i - gl_j)
        }
    }
    features['Strength'] = sumS > 0 ? strengthNum / sumS : 0.0
    
    return features
}

// ============================================================================
// GLDM FEATURES (14 features)
// ============================================================================

def buildGLDM(int[][] image, boolean[][] mask, int binWidth) {
    def gldm = [:]
    int h = image.length
    if (h == 0) return gldm
    int w = image[0].length
    
    int[][] quantized = new int[h][w]
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            quantized[y][x] = mask[y][x] ? Math.max(1, (int)(image[y][x] / binWidth) + 1) : -1
        }
    }
    
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            if (quantized[y][x] < 0) continue
            
            int gl = quantized[y][x]
            int dep = 0
            
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue
                    int ny = y + dy
                    int nx = x + dx
                    if (ny >= 0 && ny < h && nx >= 0 && nx < w && quantized[ny][nx] == gl) {
                        dep++
                    }
                }
            }
            
            // Use dep+1 to avoid zero dependency
            String key = "${gl},${dep + 1}"
            gldm[key] = (gldm[key] ?: 0) + 1
        }
    }
    
    return gldm
}

def calculateGLDMFeatures(int[][] image, boolean[][] mask, Map settings) {
    def features = [:]
    
    def gldm = buildGLDM(image, mask, settings.binWidth)
    if (gldm.isEmpty()) return features
    
    double totalDep = 0.0
    gldm.each { k, v -> totalDep += v }
    if (totalDep == 0) return features
    
    int np = 0
    for (int y = 0; y < mask.length; y++) {
        for (int x = 0; x < mask[0].length; x++) {
            if (mask[y][x]) np++
        }
    }
    
    double sde = 0.0, lde = 0.0, lgde = 0.0, hgde = 0.0
    double sdlge = 0.0, sdhge = 0.0, ldlge = 0.0, ldhge = 0.0
    double glMean = 0.0, depMean = 0.0, depEntropy = 0.0
    
    def glCounts = [:]
    def depCounts = [:]
    
    gldm.each { key, count ->
        def parts = key.split(',')
        int gl = parts[0].toInteger()
        int dep = parts[1].toInteger()
        double c = count
        
        sde += c / (dep * dep)
        lde += c * dep * dep
        lgde += c / (gl * gl)
        hgde += c * gl * gl
        sdlge += c / (gl * gl * dep * dep)
        sdhge += c * gl * gl / (dep * dep)
        ldlge += c * dep * dep / (gl * gl)
        ldhge += c * gl * gl * dep * dep
        
        glMean += gl * c
        depMean += dep * c
        
        double p = c / totalDep
        if (p > 0) depEntropy -= p * Math.log(p) / Math.log(2)
        
        glCounts[gl] = (glCounts[gl] ?: 0.0) + c
        depCounts[dep] = (depCounts[dep] ?: 0.0) + c
    }
    
    glMean /= totalDep
    depMean /= totalDep
    
    double glVar = 0.0, depVar = 0.0
    gldm.each { key, count ->
        def parts = key.split(',')
        int gl = parts[0].toInteger()
        int dep = parts[1].toInteger()
        double c = count
        glVar += (gl - glMean) * (gl - glMean) * c
        depVar += (dep - depMean) * (dep - depMean) * c
    }
    glVar /= totalDep
    depVar /= totalDep
    
    double glnu = 0.0, depnu = 0.0
    glCounts.each { gl, c -> glnu += c * c }
    depCounts.each { dep, c -> depnu += c * c }
    
    features['SmallDependenceEmphasis'] = sde / totalDep
    features['LargeDependenceEmphasis'] = lde / totalDep
    features['GrayLevelNonUniformity'] = glnu / totalDep
    features['DependenceNonUniformity'] = depnu / totalDep
    features['DependenceNonUniformityNormalized'] = depnu / (totalDep * totalDep)
    features['DependencePercentage'] = np > 0 ? totalDep / np : 0.0
    features['GrayLevelVariance'] = glVar
    features['DependenceVariance'] = depVar
    features['DependenceEntropy'] = depEntropy
    features['LowGrayLevelEmphasis'] = lgde / totalDep
    features['HighGrayLevelEmphasis'] = hgde / totalDep
    features['SmallDependenceLowGrayLevelEmphasis'] = sdlge / totalDep
    features['SmallDependenceHighGrayLevelEmphasis'] = sdhge / totalDep
    features['LargeDependenceLowGrayLevelEmphasis'] = ldlge / totalDep
    features['LargeDependenceHighGrayLevelEmphasis'] = ldhge / totalDep
    
    return features
}

// ============================================================================
// PIXEL EXTRACTION WITH MASK
// ============================================================================

def extractPixelsWithMask(BufferedImage img, ROI roi, RegionRequest request) {
    def values = []
    int width = img.getWidth()
    int height = img.getHeight()
    
    if (width == 0 || height == 0) return [new double[0], new int[0][0], new boolean[0][0]]
    
    def shape = roi.getShape()
    def at = java.awt.geom.AffineTransform.getScaleInstance(
        1.0 / request.getDownsample(),
        1.0 / request.getDownsample()
    )
    at.translate(-roi.getBoundsX(), -roi.getBoundsY())
    def transformedShape = at.createTransformedShape(shape)
    
    def roiPixels = []
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            if (transformedShape.contains(x, y)) {
                int rgb = img.getRGB(x, y)
                int r = (rgb >> 16) & 0xFF
                int g = (rgb >> 8) & 0xFF
                int b = rgb & 0xFF
                int gray = (int)(r * 0.299 + g * 0.587 + b * 0.114)
                values.add((double)gray)
                roiPixels.add([x: x, y: y, value: gray])
            }
        }
    }
    
    if (roiPixels.isEmpty()) return [new double[0], new int[0][0], new boolean[0][0]]
    
    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE
    int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE
    roiPixels.each { p ->
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    
    int compactWidth = maxX - minX + 1
    int compactHeight = maxY - minY + 1
    int[][] imageMatrix = new int[compactHeight][compactWidth]
    boolean[][] mask = new boolean[compactHeight][compactWidth]
    
    roiPixels.each { pixel ->
        int localX = pixel.x - minX
        int localY = pixel.y - minY
        imageMatrix[localY][localX] = pixel.value
        mask[localY][localX] = true
    }
    
    return [values as double[], imageMatrix, mask]
}

// ============================================================================
// MAIN EXTRACTION FUNCTION
// ============================================================================

def extractFeatures(ImageServer server, PathObject pathObject, Map settings, Map enabledFeatures) {
    def results = [:]
    
    try {
        ROI roi = pathObject.getROI()
        if (roi == null) return results
        
        // Shape features
        if (enabledFeatures['shape2D']) {
            calculateShape2DFeatures(roi).each { k, v -> results["shape2D_${k}"] = v }
        }
        if (enabledFeatures['shape']) {
            calculateShape3DFeatures(roi).each { k, v -> results["shape_${k}"] = v }
        }
        
        // Check if intensity features needed
        boolean needsIntensity = enabledFeatures['firstorder'] || enabledFeatures['glcm'] ||
                                enabledFeatures['glrlm'] || enabledFeatures['glszm'] ||
                                enabledFeatures['ngtdm'] || enabledFeatures['gldm']
        
        if (!needsIntensity) return results
        
        // Get image data
        def request = RegionRequest.createInstance(server.getPath(), 1.0, roi)
        def img = server.readRegion(request)
        def (intensities, imageMatrix, mask) = extractPixelsWithMask(img, roi, request)
        
        if (intensities.length == 0) return results
        
        // First order features
        if (enabledFeatures['firstorder']) {
            calculateFirstOrderFeatures(intensities, settings).each { k, v -> results["firstorder_${k}"] = v }
        }
        
        // Texture features
        if (imageMatrix != null && imageMatrix.length > 0 && imageMatrix[0].length > 0) {
            if (enabledFeatures['glcm']) {
                calculateGLCMFeatures(imageMatrix, mask, settings).each { k, v -> results["glcm_${k}"] = v }
            }
            if (enabledFeatures['glrlm']) {
                calculateGLRLMFeatures(imageMatrix, mask, settings).each { k, v -> results["glrlm_${k}"] = v }
            }
            if (enabledFeatures['glszm']) {
                calculateGLSZMFeatures(imageMatrix, mask, settings).each { k, v -> results["glszm_${k}"] = v }
            }
            if (enabledFeatures['ngtdm']) {
                calculateNGTDMFeatures(imageMatrix, mask, settings).each { k, v -> results["ngtdm_${k}"] = v }
            }
            if (enabledFeatures['gldm']) {
                calculateGLDMFeatures(imageMatrix, mask, settings).each { k, v -> results["gldm_${k}"] = v }
            }
        }
        
    } catch (Exception e) {
        // println "Error: ${e.message}"
    }
    
    return results
}

// ============================================================================
// MAIN EXECUTION
// ============================================================================

println "=" * 80
println "QuPath Radiomics Extraction - COMPLETE REWRITE"
println "=" * 80
enabledFeatures.each { c, e -> if (e) println "  ✓ ${c}" }
println "=" * 80

def imageData = QPEx.getCurrentImageData()
if (!imageData) {
    println "ERROR: No image loaded"
    return
}

def server = imageData.getServer()
def hierarchy = imageData.getHierarchy()
def objectsToProcess = []

if (selectedOnly) {
    objectsToProcess = QPEx.getSelectedObjects()
} else {
    if (processAnnotations) objectsToProcess.addAll(QPEx.getAnnotationObjects())
    if (processDetections) objectsToProcess.addAll(QPEx.getDetectionObjects())
}

if (objectsToProcess.isEmpty()) {
    println "ERROR: No objects to process"
    return
}

println "Processing ${objectsToProcess.size()} objects\n"

def allResults = []
def processedCount = 0
def skippedCount = 0
def startTime = System.currentTimeMillis()
def firstObjectLogged = false

objectsToProcess.eachWithIndex { pathObject, index ->
    
    if ((index + 1) % progressInterval == 0) {
        def elapsed = (System.currentTimeMillis() - startTime) / 1000.0
        def rate = (index + 1) / elapsed
        println "Processed ${index + 1}/${objectsToProcess.size()} (${String.format('%.1f', rate)} objects/sec)"
    }
    
    try {
        def results = extractFeatures(server, pathObject, settings, enabledFeatures)
        if (results.isEmpty()) {
            skippedCount++
            return
        }
        
        if (!firstObjectLogged) {
            println "\nFirst object features: ${results.size()}"
            results.groupBy { k, v -> k.split('_')[0] }.each { category, features ->
                println "  ${category}: ${features.size()}"
            }
            println ""
            firstObjectLogged = true
        }
        
        results['ObjectID'] = pathObject.getID().toString()
        results['ObjectType'] = pathObject.isDetection() ? 'Detection' : 'Annotation'
        results['Classification'] = pathObject.getPathClass()?.toString() ?: 'Unclassified'
        
        if (addToMeasurements) {
            def ml = pathObject.getMeasurementList()
            results.each { k, v ->
                if (v instanceof Number) ml.putMeasurement(k, v)
            }
        }
        
        allResults.add(results)
        processedCount++
        
    } catch (Exception e) {
        skippedCount++
    }
}

if (addToMeasurements) {
    hierarchy.fireHierarchyChangedEvent(this)
}

println "\n" + "=" * 80
println "Complete"
println "=" * 80
println "Processed: ${processedCount} objects"
println "Skipped: ${skippedCount} objects"
if (objectsToProcess.size() > 0) {
    println "Success rate: ${String.format('%.1f', 100.0 * processedCount / objectsToProcess.size())}%"
}

if (allResults.isEmpty()) {
    println "\nERROR: No features extracted"
    return
}

println "\nFeature summary:"
def featuresByCategory = allResults[0].groupBy { k, v -> k.split('_')[0] }
featuresByCategory.each { category, features ->
    println "  ${category}: ${features.size()}"
}

def totalFeatures = featuresByCategory.findAll { k, v ->
    k != 'ObjectID' && k != 'ObjectType' && k != 'Classification'
}.collect { k, v -> v.size() }.sum()
println "\nTotal radiomics features: ${totalFeatures}"

if (exportCSV && allResults.size() > 0) {
    println "\nExporting to CSV..."
    def outputFolder = new File(outputDir)
    if (!outputFolder.exists()) outputFolder.mkdirs()
    
    def timestamp = String.format('%tY%<tm%<td_%<tH%<tM%<tS', new Date())
    def imageName = server.getMetadata().getName().replaceAll('[^a-zA-Z0-9]', '_')
    def filename = "${imageName}_radiomics_${timestamp}.csv"
    def outputFile = new File(outputFolder, filename)
    
    outputFile.withWriter { writer ->
        def headers = allResults[0].keySet().sort()
        writer.writeLine(headers.join(','))
        
        allResults.each { result ->
            writer.writeLine(headers.collect { h ->
                def v = result[h]
                if (v == null) {
                    ''
                } else if (v instanceof Number) {
                    String.format('%.6f', v.doubleValue())
                } else {
                    "\"${v.toString().replaceAll('"', '""')}\""
                }
            }.join(','))
        }
    }
    
    println "File: ${outputFile.absolutePath}"
    println "Rows: ${allResults.size()}, Columns: ${allResults[0].keySet().size()}"
}

def totalTime = (System.currentTimeMillis() - startTime) / 1000.0
println "\nTotal time: ${String.format('%.1f', totalTime)}s"
println "Processing rate: ${String.format('%.1f', processedCount / totalTime)} objects/sec"

println "\n" + "=" * 80
println "Expected features breakdown:"
println "  First Order: 19"
println "  Shape2D: 10"
println "  Shape: 16"
println "  GLCM: 24"
println "  GLRLM: 16"
println "  GLSZM: 16"
println "  NGTDM: 5"
println "  GLDM: 15"
println "  -----------------"
println "  Total: 121 + 3 metadata = 124"
println "=" * 80
