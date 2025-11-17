/**
 * QuPath Radiomics Feature Extraction
 * Extracts 48 radiomics features from cell detections
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

def outputDir = "/output"
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
// FIRST ORDER FEATURES
// ============================================================================

class FirstOrderFeatures {
    def calculateFeatures(double[] intensities, Map settings) {
        def features = [:]
        if (intensities.length == 0) return features
        
        def n = intensities.length
        def sorted = intensities.clone()
        Arrays.sort(sorted)
        
        def shift = settings.voxelArrayShift ?: 0
        
        // Calculate mean
        double sum = 0
        for (int i = 0; i < n; i++) {
            sum += intensities[i]
        }
        def mean = sum / n
        
        // Calculate energy
        double energy = 0
        for (int i = 0; i < n; i++) {
            energy += (intensities[i] + shift) ** 2
        }
        features['Energy'] = energy
        features['TotalEnergy'] = energy
        
        // Calculate entropy from histogram
        def hist = buildHistogram(intensities, settings.binWidth)
        double entropy = 0
        for (int i = 0; i < hist.length; i++) {
            if (hist[i] > 0) {
                def p = hist[i] / (double)n
                entropy -= p * Math.log(p) / Math.log(2)
            }
        }
        features['Entropy'] = entropy
        
        // Basic statistics
        features['Minimum'] = sorted[0]
        features['10Percentile'] = sorted[(int)(n * 0.1)]
        features['90Percentile'] = sorted[(int)(n * 0.9)]
        features['Maximum'] = sorted[n-1]
        features['Mean'] = mean
        features['Median'] = sorted[n.intdiv(2)]
        features['InterquartileRange'] = sorted[(int)(n * 0.75)] - sorted[(int)(n * 0.25)]
        features['Range'] = sorted[n-1] - sorted[0]
        
        // Mean absolute deviation
        double mad = 0
        for (int i = 0; i < n; i++) {
            mad += Math.abs(intensities[i] - mean)
        }
        features['MeanAbsoluteDeviation'] = mad / n
        
        // Robust mean absolute deviation
        def prcnt10 = features['10Percentile']
        def prcnt90 = features['90Percentile']
        double robustSum = 0
        int robustCount = 0
        for (int i = 0; i < n; i++) {
            if (intensities[i] >= prcnt10 && intensities[i] <= prcnt90) {
                robustSum += intensities[i]
                robustCount++
            }
        }
        if (robustCount > 0) {
            def robustMean = robustSum / robustCount
            double rmad = 0
            for (int i = 0; i < n; i++) {
                if (intensities[i] >= prcnt10 && intensities[i] <= prcnt90) {
                    rmad += Math.abs(intensities[i] - robustMean)
                }
            }
            features['RobustMeanAbsoluteDeviation'] = rmad / robustCount
        }
        
        features['RootMeanSquared'] = Math.sqrt(energy / n)
        
        // Variance and higher moments
        if (n > 1) {
            double variance = 0
            for (int i = 0; i < n; i++) {
                variance += (intensities[i] - mean) ** 2
            }
            variance /= n
            features['Variance'] = variance
            features['StandardDeviation'] = Math.sqrt(variance)
            
            def stdDev = features['StandardDeviation']
            if (stdDev > 0) {
                double skewness = 0
                double kurtosis = 0
                for (int i = 0; i < n; i++) {
                    def z = (intensities[i] - mean) / stdDev
                    skewness += z ** 3
                    kurtosis += z ** 4
                }
                features['Skewness'] = skewness / n
                features['Kurtosis'] = kurtosis / n
            }
        }
        
        // Uniformity
        double uniformity = 0
        for (int i = 0; i < hist.length; i++) {
            def p = hist[i] / (double)n
            uniformity += p ** 2
        }
        features['Uniformity'] = uniformity
        
        return features
    }
    
    private int[] buildHistogram(double[] values, int binWidth) {
        if (values.length == 0) return new int[0]
        
        double min = values[0]
        double max = values[0]
        for (int i = 1; i < values.length; i++) {
            if (values[i] < min) min = values[i]
            if (values[i] > max) max = values[i]
        }
        
        def range = max - min
        if (range == 0) return [values.length] as int[]
        
        def nBins = Math.max(1, (int)Math.ceil(range / binWidth))
        def hist = new int[nBins]
        
        for (int i = 0; i < values.length; i++) {
            int bin = Math.min((int)((values[i] - min) / binWidth), nBins - 1)
            hist[bin]++
        }
        
        return hist
    }
}

// ============================================================================
// SHAPE FEATURES
// ============================================================================

class Shape2DFeatures {
    def calculateFeatures(ROI roi) {
        def features = [:]
        def area = roi.getArea()
        def perimeter = roi.getLength()
        
        features['MeshSurfaceArea'] = perimeter
        features['PixelSurface'] = perimeter
        features['Perimeter'] = perimeter
        if (area > 0) features['PerimeterSurfaceRatio'] = perimeter / area
        if (perimeter > 0) features['Sphericity'] = (4.0 * Math.PI * area) / (perimeter ** 2)
        if (features['Sphericity']) features['SphericalDisproportion'] = 1.0 / features['Sphericity']
        
        def width = roi.getBoundsWidth()
        def height = roi.getBoundsHeight()
        def major = Math.max(width, height)
        def minor = Math.min(width, height)
        
        features['MajorAxisLength'] = major
        features['MinorAxisLength'] = minor
        if (major > 0) features['Elongation'] = minor / major
        features['Flatness'] = features['Elongation']
        
        return features
    }
}

class Shape3DFeatures {
    def calculateFeatures(ROI roi) {
        def features = [:]
        def area = roi.getArea()
        def perimeter = roi.getLength()
        
        features['VoxelVolume'] = area
        features['MeshVolume'] = area
        features['SurfaceArea'] = perimeter
        if (area > 0) features['SurfaceVolumeRatio'] = perimeter / area
        if (perimeter > 0) {
            features['Sphericity'] = (4.0 * Math.PI * area) / (perimeter ** 2)
            features['Compactness1'] = area / Math.sqrt(Math.PI * perimeter ** 3)
            features['Compactness2'] = 36.0 * Math.PI * area ** 2 / perimeter ** 3
        }
        if (features['Sphericity'] && features['Sphericity'] > 0) {
            features['SphericalDisproportion'] = 1.0 / features['Sphericity']
        }
        
        def width = roi.getBoundsWidth()
        def height = roi.getBoundsHeight()
        def major = Math.max(width, height)
        def minor = Math.min(width, height)
        
        features['Maximum3DDiameter'] = Math.sqrt(width ** 2 + height ** 2)
        features['Maximum2DDiameterSlice'] = features['Maximum3DDiameter']
        features['Maximum2DDiameterColumn'] = width
        features['Maximum2DDiameterRow'] = height
        features['MajorAxisLength'] = major
        features['MinorAxisLength'] = minor
        features['LeastAxisLength'] = minor
        if (major > 0) features['Elongation'] = minor / major
        
        return features
    }
}

// ============================================================================
// TEXTURE FEATURES - GLCM
// ============================================================================

class GLCMFeatures {
    def calculateFeatures(int[][] image, Map settings) {
        def features = [:]
        def glcm = buildGLCM(image, settings.distances[0], settings.binWidth)
        if (!glcm || glcm.isEmpty()) return features
        
        def total = glcm.values().sum()
        if (total == 0) return features
        
        // Normalize GLCM
        def p = [:]
        glcm.each { key, val -> p[key] = val / total }
        
        // Marginal probabilities
        def px = [:], py = [:]
        p.each { key, prob ->
            def (i, j) = key.tokenize(',').collect { it.toInteger() }
            px[i] = (px[i] ?: 0) + prob
            py[j] = (py[j] ?: 0) + prob
        }
        
        def ux = px.sum { i, pi -> i * pi } ?: 0
        def uy = py.sum { j, pj -> j * pj } ?: 0
        def sx = Math.sqrt(px.sum { i, pi -> (i - ux) ** 2 * pi } ?: 0.001)
        def sy = Math.sqrt(py.sum { j, pj -> (j - uy) ** 2 * pj } ?: 0.001)
        
        features['Autocorrelation'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); i*j*prob } ?: 0
        features['JointAverage'] = ux
        features['ClusterProminence'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); ((i+j-ux-uy)**4)*prob } ?: 0
        features['ClusterShade'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); ((i+j-ux-uy)**3)*prob } ?: 0
        features['ClusterTendency'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); ((i+j-ux-uy)**2)*prob } ?: 0
        features['Contrast'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); ((i-j)**2)*prob } ?: 0
        features['Correlation'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); (i-ux)*(j-uy)*prob/(sx*sy) } ?: 0
        
        def pxMinusY = [:]
        p.each { key, prob ->
            def (i, j) = key.tokenize(',')*.toInteger()
            pxMinusY[Math.abs(i-j)] = (pxMinusY[Math.abs(i-j)] ?: 0) + prob
        }
        features['DifferenceAverage'] = pxMinusY.sum { k, pk -> k*pk } ?: 0
        features['DifferenceEntropy'] = -pxMinusY.sum { k, pk -> pk>0 ? pk*Math.log(pk)/Math.log(2) : 0 }
        features['DifferenceVariance'] = pxMinusY.sum { k, pk -> (k-features['DifferenceAverage'])**2*pk } ?: 0
        
        features['JointEnergy'] = p.sum { k, prob -> prob**2 } ?: 0
        features['JointEntropy'] = -p.sum { k, prob -> prob>0 ? prob*Math.log(prob)/Math.log(2) : 0 }
        
        def hx = -px.sum { i, pi -> pi>0 ? pi*Math.log(pi)/Math.log(2) : 0 }
        def hy = -py.sum { j, pj -> pj>0 ? pj*Math.log(pj)/Math.log(2) : 0 }
        def hxy1 = -p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); (prob>0&&px[i]>0&&py[j]>0) ? prob*Math.log(px[i]*py[j])/Math.log(2) : 0 }
        features['Imc1'] = (hx>0 && hy>0) ? (features['JointEntropy']-hxy1)/Math.max(hx,hy) : 0
        features['Imc2'] = 0
        
        def ng = Math.max(px.keySet().max()?:1, py.keySet().max()?:1)
        features['Idm'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); prob/(1+(i-j)**2) } ?: 0
        features['Idmn'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); prob/(1+((i-j)**2)/(ng**2)) } ?: 0
        features['Id'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); prob/(1+Math.abs(i-j)) } ?: 0
        features['Idn'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); prob/(1+Math.abs(i-j)/ng) } ?: 0
        features['InverseVariance'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); i!=j ? prob/((i-j)**2) : 0 } ?: 0
        
        features['MaximumProbability'] = p.values().max() ?: 0
        
        def pxPlusY = [:]
        p.each { key, prob ->
            def (i, j) = key.tokenize(',')*.toInteger()
            pxPlusY[i+j] = (pxPlusY[i+j] ?: 0) + prob
        }
        features['SumAverage'] = pxPlusY.sum { k, pk -> k*pk } ?: 0
        features['SumEntropy'] = -pxPlusY.sum { k, pk -> pk>0 ? pk*Math.log(pk)/Math.log(2) : 0 }
        features['SumSquares'] = p.sum { k, prob -> def (i,j) = k.tokenize(',')*.toInteger(); ((i-ux)**2)*prob } ?: 0
        
        return features
    }
    
    private buildGLCM(int[][] image, int distance, int binWidth) {
        def glcm = [:]
        def height = image.length
        if (height == 0) return null
        def width = image[0].length
        if (width == 0) return null
        
        // Build co-occurrence matrix in 4 directions
        [[1,0], [1,1], [0,1], [-1,1]].each { angle ->
            def dx = angle[0] * distance
            def dy = angle[1] * distance
            
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (image[y][x] == 0) continue
                    
                    def nx = x + dx
                    def ny = y + dy
                    if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                        if (image[ny][nx] == 0) continue
                        
                        def i = Math.max(1, (image[y][x] / binWidth).intValue())
                        def j = Math.max(1, (image[ny][nx] / binWidth).intValue())
                        glcm["${i},${j}"] = (glcm["${i},${j}"] ?: 0) + 1
                        glcm["${j},${i}"] = (glcm["${j},${i}"] ?: 0) + 1
                    }
                }
            }
        }
        
        return glcm
    }
}

// ============================================================================
// TEXTURE FEATURES - GLRLM
// ============================================================================

class GLRLMFeatures {
    def calculateFeatures(int[][] image, Map settings) {
        def features = [:]
        def glrlm = buildGLRLM(image, settings.binWidth)
        if (glrlm.isEmpty()) return features
        
        def totalRuns = glrlm.values().sum()
        if (totalRuns == 0) return features
        
        def np = image.length * image[0].length
        
        features['ShortRunEmphasis'] = glrlm.sum { k, c -> c/(k.split(',')[1].toInteger()**2) } / totalRuns
        features['LongRunEmphasis'] = glrlm.sum { k, c -> c*(k.split(',')[1].toInteger()**2) } / totalRuns
        
        def glSum = glrlm.groupBy { k, c -> k.split(',')[0] }.collect { gl, m -> m.values().sum()**2 }.sum()
        features['GrayLevelNonUniformity'] = glSum / totalRuns
        features['GrayLevelNonUniformityNormalized'] = glSum / (totalRuns**2)
        
        def rlSum = glrlm.groupBy { k, c -> k.split(',')[1] }.collect { rl, m -> m.values().sum()**2 }.sum()
        features['RunLengthNonUniformity'] = rlSum / totalRuns
        features['RunLengthNonUniformityNormalized'] = rlSum / (totalRuns**2)
        
        features['RunPercentage'] = totalRuns / np
        
        def meanGL = glrlm.sum { k, c -> (k.split(',')[0].toInteger()+1)*c } / totalRuns
        features['GrayLevelVariance'] = glrlm.sum { k, c -> ((k.split(',')[0].toInteger()+1)-meanGL)**2*c } / totalRuns
        def meanRL = glrlm.sum { k, c -> k.split(',')[1].toInteger()*c } / totalRuns
        features['RunVariance'] = glrlm.sum { k, c -> (k.split(',')[1].toInteger()-meanRL)**2*c } / totalRuns
        features['RunEntropy'] = -glrlm.sum { k, c -> def p=c/totalRuns; p>0 ? p*Math.log(p)/Math.log(2) : 0 }
        
        features['LowGrayLevelRunEmphasis'] = glrlm.sum { k, c -> c/((k.split(',')[0].toInteger()+1)**2) } / totalRuns
        features['HighGrayLevelRunEmphasis'] = glrlm.sum { k, c -> c*((k.split(',')[0].toInteger()+1)**2) } / totalRuns
        features['ShortRunLowGrayLevelEmphasis'] = glrlm.sum { k, c -> c/((k.split(',')[0].toInteger()+1)**2*(k.split(',')[1].toInteger()**2)) } / totalRuns
        features['ShortRunHighGrayLevelEmphasis'] = glrlm.sum { k, c -> c*((k.split(',')[0].toInteger()+1)**2)/(k.split(',')[1].toInteger()**2) } / totalRuns
        features['LongRunLowGrayLevelEmphasis'] = glrlm.sum { k, c -> c*(k.split(',')[1].toInteger()**2)/((k.split(',')[0].toInteger()+1)**2) } / totalRuns
        features['LongRunHighGrayLevelEmphasis'] = glrlm.sum { k, c -> c*((k.split(',')[0].toInteger()+1)**2)*(k.split(',')[1].toInteger()**2) } / totalRuns
        
        return features
    }
    
    private buildGLRLM(int[][] image, int binWidth) {
        def glrlm = [:]
        def height = image.length
        if (height == 0) return glrlm
        def width = image[0].length
        
        // Horizontal runs
        for (int y = 0; y < height; y++) {
            int gl = -1, len = 0
            for (int x = 0; x < width; x++) {
                if (image[y][x] == 0) {
                    if (len > 0) {
                        glrlm["${gl},${len}"] = (glrlm["${gl},${len}"]?:0)+1
                        len = 0
                    }
                    gl = -1
                    continue
                }
                
                int g = Math.max(0, (image[y][x]/binWidth).intValue())
                if (g == gl) {
                    len++
                } else {
                    if (len > 0) glrlm["${gl},${len}"] = (glrlm["${gl},${len}"]?:0)+1
                    gl = g
                    len = 1
                }
            }
            if (len > 0) glrlm["${gl},${len}"] = (glrlm["${gl},${len}"]?:0)+1
        }
        
        // Vertical runs
        for (int x = 0; x < width; x++) {
            int gl = -1, len = 0
            for (int y = 0; y < height; y++) {
                if (image[y][x] == 0) {
                    if (len > 0) {
                        glrlm["${gl},${len}"] = (glrlm["${gl},${len}"]?:0)+1
                        len = 0
                    }
                    gl = -1
                    continue
                }
                
                int g = Math.max(0, (image[y][x]/binWidth).intValue())
                if (g == gl) {
                    len++
                } else {
                    if (len > 0) glrlm["${gl},${len}"] = (glrlm["${gl},${len}"]?:0)+1
                    gl = g
                    len = 1
                }
            }
            if (len > 0) glrlm["${gl},${len}"] = (glrlm["${gl},${len}"]?:0)+1
        }
        
        return glrlm
    }
}

// ============================================================================
// TEXTURE FEATURES - GLSZM
// ============================================================================

class GLSZMFeatures {
    def calculateFeatures(int[][] image, Map settings) {
        def features = [:]
        def glszm = buildGLSZM(image, settings.binWidth)
        if (glszm.isEmpty()) return features
        
        def totalZones = glszm.values().sum()
        if (totalZones == 0) return features
        
        def np = image.length * image[0].length
        
        features['SmallAreaEmphasis'] = glszm.sum { k, c -> c/(k.split(',')[1].toInteger()**2) } / totalZones
        features['LargeAreaEmphasis'] = glszm.sum { k, c -> c*(k.split(',')[1].toInteger()**2) } / totalZones
        
        def glSum = glszm.groupBy { k, c -> k.split(',')[0] }.collect { gl, m -> m.values().sum()**2 }.sum()
        features['GrayLevelNonUniformity'] = glSum / totalZones
        features['GrayLevelNonUniformityNormalized'] = glSum / (totalZones**2)
        
        def szSum = glszm.groupBy { k, c -> k.split(',')[1] }.collect { sz, m -> m.values().sum()**2 }.sum()
        features['SizeZoneNonUniformity'] = szSum / totalZones
        features['SizeZoneNonUniformityNormalized'] = szSum / (totalZones**2)
        
        features['ZonePercentage'] = totalZones / np
        
        def meanGL = glszm.sum { k, c -> (k.split(',')[0].toInteger()+1)*c } / totalZones
        features['GrayLevelVariance'] = glszm.sum { k, c -> ((k.split(',')[0].toInteger()+1)-meanGL)**2*c } / totalZones
        def meanSZ = glszm.sum { k, c -> k.split(',')[1].toInteger()*c } / totalZones
        features['ZoneVariance'] = glszm.sum { k, c -> (k.split(',')[1].toInteger()-meanSZ)**2*c } / totalZones
        features['ZoneEntropy'] = -glszm.sum { k, c -> def p=c/totalZones; p>0 ? p*Math.log(p)/Math.log(2) : 0 }
        
        features['LowGrayLevelZoneEmphasis'] = glszm.sum { k, c -> c/((k.split(',')[0].toInteger()+1)**2) } / totalZones
        features['HighGrayLevelZoneEmphasis'] = glszm.sum { k, c -> c*((k.split(',')[0].toInteger()+1)**2) } / totalZones
        features['SmallAreaLowGrayLevelEmphasis'] = glszm.sum { k, c -> c/((k.split(',')[0].toInteger()+1)**2*(k.split(',')[1].toInteger()**2)) } / totalZones
        features['SmallAreaHighGrayLevelEmphasis'] = glszm.sum { k, c -> c*((k.split(',')[0].toInteger()+1)**2)/(k.split(',')[1].toInteger()**2) } / totalZones
        features['LargeAreaLowGrayLevelEmphasis'] = glszm.sum { k, c -> c*(k.split(',')[1].toInteger()**2)/((k.split(',')[0].toInteger()+1)**2) } / totalZones
        features['LargeAreaHighGrayLevelEmphasis'] = glszm.sum { k, c -> c*((k.split(',')[0].toInteger()+1)**2)*(k.split(',')[1].toInteger()**2) } / totalZones
        
        return features
    }
    
    private buildGLSZM(int[][] image, int binWidth) {
        def glszm = [:]
        def h = image.length
        if (h == 0) return glszm
        def w = image[0].length
        
        def quantized = new int[h][w]
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                quantized[y][x] = image[y][x] > 0 ? Math.max(0, (image[y][x]/binWidth).intValue()) : -1
            }
        }
        
        def visited = new boolean[h][w]
        
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!visited[y][x] && quantized[y][x] >= 0) {
                    def gl = quantized[y][x]
                    def size = floodFill(quantized, visited, x, y, gl)
                    if (size > 0) glszm["${gl},${size}"] = (glszm["${gl},${size}"]?:0)+1
                }
            }
        }
        
        return glszm
    }
    
    private int floodFill(int[][] image, boolean[][] visited, int x, int y, int targetGL) {
        def h = image.length
        def w = image[0].length
        def stack = [[x, y]]
        def size = 0
        
        while (!stack.isEmpty()) {
            def (cx, cy) = stack.pop()
            
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
}

// ============================================================================
// TEXTURE FEATURES - NGTDM
// ============================================================================

class NGTDMFeatures {
    def calculateFeatures(int[][] image, Map settings) {
        def features = [:]
        def ngtdm = buildNGTDM(image, settings.binWidth)
        if (ngtdm.isEmpty()) return features
        
        def n = ngtdm.values().sum { it['n'] }
        if (n == 0) return features
        
        def Ng = ngtdm.size()
        def p = [:]
        ngtdm.each { gl, data -> p[gl] = data['n'] / n }
        
        features['Coarseness'] = ngtdm.sum { gl, data -> p[gl]*data['s'] } > 0 ? 1.0/(ngtdm.sum { gl, data -> p[gl]*data['s'] }) : 0
        
        features['Contrast'] = (Ng>1 && n>0) ? (1.0/(Ng*(Ng-1))) * ngtdm.collectMany { gl_i, data_i ->
            ngtdm.collect { gl_j, data_j -> p[gl_i]*p[gl_j]*((gl_i-gl_j)**2) }
        }.sum() * ngtdm.sum { gl, data -> data['s'] } / n : 0
        
        def busySum = ngtdm.collectMany { gl_i, data_i ->
            ngtdm.findAll { gl_j, data_j -> gl_i != gl_j }.collect { gl_j, data_j ->
                Math.abs(gl_i*p[gl_i] - gl_j*p[gl_j])
            }
        }.sum()
        features['Busyness'] = busySum > 0 ? ngtdm.sum { gl, data -> p[gl]*data['s'] } / busySum : 0
        
        features['Complexity'] = ngtdm.collectMany { gl_i, data_i ->
            ngtdm.collect { gl_j, data_j ->
                (p[gl_i]+p[gl_j] > 0) ? (Math.abs(gl_i-gl_j) * (p[gl_i]*data_i['s'] + p[gl_j]*data_j['s'])) / (p[gl_i]+p[gl_j]) : 0
            }
        }.sum() / n
        
        def strengthDenom = ngtdm.sum { gl, data -> data['s'] }
        features['Strength'] = strengthDenom > 0 ? ngtdm.collectMany { gl_i, data_i ->
            ngtdm.collect { gl_j, data_j -> (p[gl_i]+p[gl_j])*((gl_i-gl_j)**2) }
        }.sum() / strengthDenom : 0
        
        return features
    }
    
    private buildNGTDM(int[][] image, int binWidth) {
        def ngtdm = [:]
        def h = image.length
        if (h == 0) return ngtdm
        def w = image[0].length
        
        def quantized = new int[h][w]
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                quantized[y][x] = image[y][x] > 0 ? Math.max(0, (image[y][x]/binWidth).intValue()) : -1
            }
        }
        
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (quantized[y][x] < 0) continue
                
                def gl = quantized[y][x]
                def neighbors = []
                
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue
                        def ny = y + dy
                        def nx = x + dx
                        if (ny >= 0 && ny < h && nx >= 0 && nx < w && quantized[ny][nx] >= 0) {
                            neighbors.add(quantized[ny][nx])
                        }
                    }
                }
                
                if (neighbors.size() > 0) {
                    def avgNeighbor = neighbors.sum() / neighbors.size()
                    def s = Math.abs(gl - avgNeighbor)
                    
                    if (!ngtdm[gl]) ngtdm[gl] = ['n': 0, 's': 0.0]
                    ngtdm[gl]['n']++
                    ngtdm[gl]['s'] += s
                }
            }
        }
        
        return ngtdm
    }
}

// ============================================================================
// TEXTURE FEATURES - GLDM
// ============================================================================

class GLDMFeatures {
    def calculateFeatures(int[][] image, Map settings) {
        def features = [:]
        def gldm = buildGLDM(image, settings.binWidth)
        if (gldm.isEmpty()) return features
        
        def totalDep = gldm.values().sum()
        if (totalDep == 0) return features
        
        features['SmallDependenceEmphasis'] = gldm.sum { k, c -> c/(k.split(',')[1].toInteger()**2) } / totalDep
        features['LargeDependenceEmphasis'] = gldm.sum { k, c -> c*(k.split(',')[1].toInteger()**2) } / totalDep
        
        def glSum = gldm.groupBy { k, c -> k.split(',')[0] }.collect { gl, m -> m.values().sum()**2 }.sum()
        features['GrayLevelNonUniformity'] = glSum / totalDep
        
        def depSum = gldm.groupBy { k, c -> k.split(',')[1] }.collect { dep, m -> m.values().sum()**2 }.sum()
        features['DependenceNonUniformity'] = depSum / totalDep
        features['DependenceNonUniformityNormalized'] = depSum / (totalDep**2)
        
        def meanGL = gldm.sum { k, c -> (k.split(',')[0].toInteger()+1)*c } / totalDep
        features['GrayLevelVariance'] = gldm.sum { k, c -> ((k.split(',')[0].toInteger()+1)-meanGL)**2*c } / totalDep
        def meanDep = gldm.sum { k, c -> k.split(',')[1].toInteger()*c } / totalDep
        features['DependenceVariance'] = gldm.sum { k, c -> (k.split(',')[1].toInteger()-meanDep)**2*c } / totalDep
        features['DependenceEntropy'] = -gldm.sum { k, c -> def p=c/totalDep; p>0 ? p*Math.log(p)/Math.log(2) : 0 }
        
        features['LowGrayLevelEmphasis'] = gldm.sum { k, c -> c/((k.split(',')[0].toInteger()+1)**2) } / totalDep
        features['HighGrayLevelEmphasis'] = gldm.sum { k, c -> c*((k.split(',')[0].toInteger()+1)**2) } / totalDep
        features['SmallDependenceLowGrayLevelEmphasis'] = gldm.sum { k, c -> c/((k.split(',')[0].toInteger()+1)**2*(k.split(',')[1].toInteger()**2)) } / totalDep
        features['SmallDependenceHighGrayLevelEmphasis'] = gldm.sum { k, c -> c*((k.split(',')[0].toInteger()+1)**2)/(k.split(',')[1].toInteger()**2) } / totalDep
        features['LargeDependenceLowGrayLevelEmphasis'] = gldm.sum { k, c -> c*(k.split(',')[1].toInteger()**2)/((k.split(',')[0].toInteger()+1)**2) } / totalDep
        features['LargeDependenceHighGrayLevelEmphasis'] = gldm.sum { k, c -> c*((k.split(',')[0].toInteger()+1)**2)*(k.split(',')[1].toInteger()**2) } / totalDep
        
        return features
    }
    
    private buildGLDM(int[][] image, int binWidth) {
        def gldm = [:]
        def h = image.length
        if (h == 0) return gldm
        def w = image[0].length
        
        def quantized = new int[h][w]
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                quantized[y][x] = image[y][x] > 0 ? Math.max(0, (image[y][x]/binWidth).intValue()) : -1
            }
        }
        
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (quantized[y][x] < 0) continue
                
                def gl = quantized[y][x]
                def dep = 0
                
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue
                        def ny = y + dy
                        def nx = x + dx
                        if (ny >= 0 && ny < h && nx >= 0 && nx < w) {
                            if (quantized[ny][nx] == gl) dep++
                        }
                    }
                }
                if (dep > 0) gldm["${gl},${dep}"] = (gldm["${gl},${dep}"]?:0)+1
            }
        }
        
        return gldm
    }
}

// ============================================================================
// MAIN EXTRACTOR
// ============================================================================

class RadiomicsExtractor {
    def settings, enabledFeatures
    
    RadiomicsExtractor(Map s, Map e) { 
        settings = s
        enabledFeatures = e 
    }
    
    def extractFeatures(ImageServer server, PathObject pathObject) {
        def results = [:]
        
        try {
            ROI roi = pathObject.getROI()
            if (!roi) return results
            
            // Extract shape features
            if (enabledFeatures['shape2D']) {
                new Shape2DFeatures().calculateFeatures(roi).each { k, v -> 
                    results["shape2D_${k}"] = v 
                }
            }
            if (enabledFeatures['shape']) {
                new Shape3DFeatures().calculateFeatures(roi).each { k, v -> 
                    results["shape_${k}"] = v 
                }
            }
            
            // Check if intensity features are needed
            def needsIntensity = enabledFeatures['firstorder'] || enabledFeatures['glcm'] || 
                               enabledFeatures['glrlm'] || enabledFeatures['glszm'] || 
                               enabledFeatures['ngtdm'] || enabledFeatures['gldm']
            
            if (!needsIntensity) return results
            
            // Get image data
            def request = RegionRequest.createInstance(server.getPath(), 1.0, roi)
            def img = server.readRegion(request)
            def (intensities, imageMatrix) = extractPixels(img, roi, request)
            
            if (intensities.length == 0) return results
            
            // First order features
            if (enabledFeatures['firstorder']) {
                new FirstOrderFeatures().calculateFeatures(intensities, settings).each { k, v -> 
                    results["firstorder_${k}"] = v 
                }
            }
            
            // Texture features
            if (imageMatrix && imageMatrix.length > 0 && imageMatrix[0].length > 0) {
                if (enabledFeatures['glcm']) {
                    new GLCMFeatures().calculateFeatures(imageMatrix, settings).each { k, v -> 
                        results["glcm_${k}"] = v 
                    }
                }
                if (enabledFeatures['glrlm']) {
                    new GLRLMFeatures().calculateFeatures(imageMatrix, settings).each { k, v -> 
                        results["glrlm_${k}"] = v 
                    }
                }
                if (enabledFeatures['glszm']) {
                    new GLSZMFeatures().calculateFeatures(imageMatrix, settings).each { k, v -> 
                        results["glszm_${k}"] = v 
                    }
                }
                if (enabledFeatures['ngtdm']) {
                    new NGTDMFeatures().calculateFeatures(imageMatrix, settings).each { k, v -> 
                        results["ngtdm_${k}"] = v 
                    }
                }
                if (enabledFeatures['gldm']) {
                    new GLDMFeatures().calculateFeatures(imageMatrix, settings).each { k, v -> 
                        results["gldm_${k}"] = v 
                    }
                }
            }
            
        } catch (Exception e) {
            // Skip this object if error occurs
        }
        
        return results
    }
    
    private extractPixels(BufferedImage img, ROI roi, RegionRequest request) {
        def values = []
        def width = img.getWidth()
        def height = img.getHeight()
        
        if (width == 0 || height == 0) return [new double[0], new int[0][0]]
        
        // Transform ROI to image coordinates
        def shape = roi.getShape()
        def at = java.awt.geom.AffineTransform.getScaleInstance(
            1.0 / request.getDownsample(),
            1.0 / request.getDownsample()
        )
        at.translate(-roi.getBoundsX(), -roi.getBoundsY())
        def transformedShape = at.createTransformedShape(shape)
        
        // Collect pixels inside ROI
        def roiPixels = []
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (transformedShape.contains(x, y)) {
                    def rgb = img.getRGB(x, y)
                    def r = (rgb >> 16) & 0xFF
                    def g = (rgb >> 8) & 0xFF
                    def b = rgb & 0xFF
                    def gray = (int)(r * 0.299 + g * 0.587 + b * 0.114)
                    values.add((double)gray)
                    roiPixels.add([x: x, y: y, value: gray])
                }
            }
        }
        
        if (roiPixels.isEmpty()) return [new double[0], new int[0][0]]
        
        // Create compact matrix
        def minX = roiPixels.collect { it.x }.min()
        def maxX = roiPixels.collect { it.x }.max()
        def minY = roiPixels.collect { it.y }.min()
        def maxY = roiPixels.collect { it.y }.max()
        
        def compactWidth = maxX - minX + 1
        def compactHeight = maxY - minY + 1
        def imageMatrix = new int[compactHeight][compactWidth]
        
        roiPixels.each { pixel ->
            def localX = pixel.x - minX
            def localY = pixel.y - minY
            imageMatrix[localY][localX] = pixel.value
        }
        
        return [values as double[], imageMatrix]
    }
}

// ============================================================================
// MAIN EXECUTION
// ============================================================================

println "=" * 80
println "QuPath Radiomics Extraction"
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

def extractor = new RadiomicsExtractor(settings, enabledFeatures)
def allResults = []
def processedCount = 0
def skippedCount = 0
def startTime = System.currentTimeMillis()
def firstObjectLogged = false

objectsToProcess.eachWithIndex { pathObject, index ->
    
    // Progress update
    if ((index + 1) % progressInterval == 0) {
        def elapsed = (System.currentTimeMillis() - startTime) / 1000.0
        def rate = (index + 1) / elapsed
        println "Processed ${index + 1}/${objectsToProcess.size()} (${String.format('%.1f', rate)} objects/sec)"
    }
    
    try {
        def results = extractor.extractFeatures(server, pathObject)
        if (results.isEmpty()) { 
            skippedCount++
            return 
        }
        
        // Log first object
        if (!firstObjectLogged) {
            println "\nFirst object features: ${results.size()}"
            results.groupBy { k, v -> k.split('_')[0] }.each { category, features ->
                println "  ${category}: ${features.size()}"
            }
            println ""
            firstObjectLogged = true
        }
        
        // Add metadata
        results['ObjectID'] = pathObject.getID().toString()
        results['ObjectType'] = pathObject.isDetection() ? 'Detection' : 'Annotation'
        results['Classification'] = pathObject.getPathClass()?.toString() ?: 'Unclassified'
        
        // Add to measurements
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

// Export to CSV
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
println "\nExpected features: ~123 (120 radiomics + 3 metadata)"
println "Actual features: ${allResults[0].size()}"
