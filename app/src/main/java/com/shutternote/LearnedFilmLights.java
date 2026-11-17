package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;

/** Source-component conditioned, energy-normalized optical response from the saved coefficients. */
final class LearnedFilmLights {
    private final double threshold,kernelWidth,reference;
    private final double[] widths,center,scales,prototypes,gains;
    private final int prototypeCount;
    private final boolean boundaryOnly;
    private static final float[] LINEAR=new float[256];
    static { for(int i=0;i<256;i++)LINEAR[i]=(float)linear(i/255f); }

    LearnedFilmLights(JSONObject p,double reference) throws JSONException {
        if(!p.getString("method").equals("source_light_response_rbf_v4") ||
                !p.getString("kernel_tail").equals("compact_56px_v2"))throw new JSONException("Unsupported light response");
        this.reference=reference;
        threshold=p.getDouble("threshold_linear"); kernelWidth=p.getDouble("kernel_width");
        if(threshold<=0 || threshold>=1 || kernelWidth<=0 || reference<=0)throw new JSONException("Invalid light settings");
        widths=array(p,"sigma_reference_px",4); center=array(p,"descriptor_center",8); scales=array(p,"descriptor_scale",8);
        prototypeCount=p.getJSONArray("prototypes").length();
        if(prototypeCount<1 || prototypeCount>8)throw new JSONException("Invalid light prototypes");
        prototypes=array(p,"prototypes",prototypeCount*8); gains=array(p,"gain_rgb",prototypeCount*18);
        for(double v:scales)if(v<.049999)throw new JSONException("Invalid light scale");
        for(double v:widths)if(v<=0 || v>100)throw new JSONException("Invalid spread width");
        boundaryOnly=p.optString("core_policy").equals("boundary_and_scatter_v2");
    }

    void apply(int[] original,float[] color,int w,int h,BooleanSupplier cancelled) {
        boolean active=false;for(double gain:gains)active|=gain!=0;
        if(!active)return;
        int n=original.length;
        int bright = 0;
        double scene = 0;
        for (int i = 0; i < n; i++) {
            if ((i & 4095) == 0) check(cancelled);
            float value = signal(original[i]);
            if (value > threshold) bright++;
            scene += value;
        }
        if (bright == 0 || bright == n) return;
        int[] positions = new int[bright];
        for (int i = 0, index = 0; i < n; i++) {
            if ((i & 4095) == 0) check(cancelled);
            if (signal(original[i]) > threshold) positions[index++] = i;
        }
        int[] labels = new int[bright];
        int components = label(positions, labels, w, h, cancelled);
        double scale = Math.max(w, h) / reference;
        float[] local = new float[bright];
        localLightLevels(original, positions, local, w, h, gaussian(Math.max(1, 12 * scale)), cancelled);
        float[] energy = new float[bright];
        double[] stats = new double[(components + 1) * 8];
        for (int index = 0; index < bright; index++) {
            if ((index & 4095) == 0) check(cancelled);
            int i = positions[index], id = labels[index];
            int x = i % w, y = i / w, p = original[i], at = id * 8;
            float signal = signal(p);
            double contrast = clamp((signal - local[index]) / .25);
            energy[index] = (float) (Math.max(signal - threshold, 0) / (1 - threshold) * contrast);
            stats[at]++;
            stats[at + 1] += signal;
            stats[at + 2] += LINEAR[(p >>> 16) & 255];
            stats[at + 3] += LINEAR[(p >>> 8) & 255];
            stats[at + 4] += LINEAR[p & 255];
            stats[at + 5] += contrast;
            stats[at + 6] += local[index];
            // Adjacent above-threshold pixels belong to the same component.
            stats[at + 7] += (x == 0 || signal(original[i - 1]) <= threshold ? 1 : 0)
                    + (x == w - 1 || signal(original[i + 1]) <= threshold ? 1 : 0)
                    + (y == 0 || signal(original[i - w]) <= threshold ? 1 : 0)
                    + (y == h - 1 || signal(original[i + w]) <= threshold ? 1 : 0);
        }
        double[] weights=new double[(components+1)*prototypeCount],d=new double[8];
        for(int id=1;id<=components;id++) {
            double count=stats[id*8],r=stats[id*8+2]/count,g=stats[id*8+3]/count,b=stats[id*8+4]/count;
            double total=Math.max(r+g+b,1e-9),perimeter=stats[id*8+7];
            d[0]=stats[id*8+1]/count;d[1]=r/total;d[2]=b/total;
            d[3]=Math.tanh(Math.log(Math.max(Math.sqrt(count/Math.PI)/scale,.25)/6)/2);
            d[4]=clamp(4*Math.PI*count/Math.max(perimeter*perimeter,1));
            d[5]=stats[id*8+5]/count;d[6]=stats[id*8+6]/count;d[7]=scene/n;
            double sum=0,max=0;
            for(int k=0;k<prototypeCount;k++) {
                double distance=0;
                for(int j=0;j<8;j++) { double diff=(d[j]-center[j])/scales[j]-prototypes[k*8+j];distance+=diff*diff/8; }
                double value=Math.exp(-distance/(2*kernelWidth*kernelWidth));
                weights[id*prototypeCount+k]=value;sum+=value;max=Math.max(max,value);
            }
            for(int k=0;k<prototypeCount;k++)weights[id*prototypeCount+k]*=max/Math.max(sum,1e-12);
        }
        readCoreDistances(positions, energy, local, w, h, Math.max(2 * scale, .1), cancelled);
        float[] emission = new float[bright];
        FloatInput field = i -> signal(original[i]) <= threshold ? 0
                : emission[java.util.Arrays.binarySearch(positions, i)];
        FloatRowInput fieldRows = (offset, row) -> {
            java.util.Arrays.fill(row, 0);
            int start = java.util.Arrays.binarySearch(positions, offset);
            if (start < 0) start = -start - 1;
            int end = offset + row.length;
            for (int i = start; i < positions.length && positions[i] < end; i++)
                row[positions[i] - offset] = emission[i];
        };
        for (int i = 0; i < color.length; i++) color[i] = (float) linear(color[i]);
        for (int k = 0; k < prototypeCount; k++) {
            check(cancelled);
            for (int index = 0; index < bright; index++) {
                if ((index & 4095) == 0) check(cancelled);
                int i = positions[index];
                float value = (float) (energy[index] * weights[labels[index] * prototypeCount + k]);
                emission[index] = value;
                float a = boundaryOnly ? 0 : value * (1 - local[index]), b = value * local[index];
                for (int c = 0; c < 3; c++)
                    color[i * 3 + c] += a * (float) gains[k * 18 + c] + b * (float) gains[k * 18 + 3 + c];
            }
            for(int band=0;band<4;band++) {
                double sigma=Math.max(widths[band]*scale,.1),radius=Math.max(.75,Math.min(4*sigma,56*scale/Math.sqrt(2)));
                int edge=(int)Math.ceil(radius);double[] kernel=new double[edge*2+1];double total=0;
                for(int x=-edge;x<=edge;x++) {
                    kernel[x+edge]=Math.exp(-.5*x*x/(sigma*sigma))*Math.pow(Math.max(1-x*x/(radius*radius),0),2);
                    total+=kernel[x+edge];
                }
                for(int x=0;x<kernel.length;x++)kernel[x]/=total;
                int gainOffset = k * 18 + (band + 2) * 3;
                filterWithRowBuffer(fieldRows, w, h, kernel, (i, spread) -> {
                    if (spread <= 0) return;
                    float value = Math.max(spread - field.get(i), 0);
                    for (int c = 0; c < 3; c++) color[i * 3 + c] += value * (float) gains[gainOffset + c];
                }, cancelled);
            }
        }
        for (int i = 0; i < color.length; i++) color[i] = (float) clamp(srgb(clamp(color[i])));
    }

    private static float signal(int pixel) {
        return Math.max(LINEAR[(pixel >>> 16) & 255], Math.max(LINEAR[(pixel >>> 8) & 255], LINEAR[pixel & 255]));
    }

    private void localLightLevels(int[] original, int[] positions, float[] levels, int w, int h,
                                  double[] kernel, BooleanSupplier cancelled) {
        if (positions.length > original.length / 20) {
            filterWithRowBuffer(i -> signal(original[i]), w, h, kernel, (i, value) -> {
                if (signal(original[i]) > threshold)
                    levels[java.util.Arrays.binarySearch(positions, i)] = value;
            }, cancelled);
            return;
        }
        // Local contrast is sampled only at bright pixels. Reuse horizontal taps within each column.
        int[] head = new int[w], next = new int[positions.length];
        java.util.Arrays.fill(head, -1);
        for (int i = positions.length - 1; i >= 0; i--) {
            int x = positions[i] % w;
            next[i] = head[x];
            head[x] = i;
        }
        int radius = kernel.length / 2;
        rows(h, w, cancelled, (first, last, stop) -> {
            float[] horizontal = new float[h];
            int[] column = new int[h];
            int[] taps = new int[kernel.length];
            for (int x = first; x < last; x++) {
                check(stop);
                if (head[x] < 0) continue;
                for (int k = 0; k < kernel.length; k++) taps[k] = reflect(x + k - radius, w);
                for (int i = head[x]; i >= 0; i = next[i]) {
                    int y = positions[i] / w;
                    double sum = 0;
                    for (int j = 0; j < kernel.length; j++) {
                        int row = reflect(y + j - radius, h);
                        if (column[row] != x + 1) {
                            double total = 0;
                            int offset = row * w;
                            for (int k = 0; k < kernel.length; k++)
                                total += signal(original[offset + taps[k]]) * kernel[k];
                            horizontal[row] = (float) total;
                            column[row] = x + 1;
                        }
                        sum += horizontal[row] * kernel[j];
                    }
                    levels[i] = (float) sum;
                }
            }
        });
    }

    private static void readCoreDistances(int[] positions, float[] energy, float[] core, int w, int h,
                                          double innerWidth, BooleanSupplier cancelled) {
        // Nonemitting pixels have zero distance and can stay implicit during both chamfer scans.
        for (int i = 0; i < positions.length; i++) core[i] = energy[i] > 0 ? 1e10f : 0;
        int[][] offsets = {{-1,0},{0,-1},{-1,-1},{1,-1},{-2,-1},{2,-1},{-1,-2},{1,-2}};
        for (int direction : new int[]{1, -1}) {
            for (int step = 0; step < positions.length; step++) {
                if ((step & 4095) == 0) check(cancelled);
                int at = direction == 1 ? step : positions.length - 1 - step;
                if (core[at] == 0) continue;
                int x = positions[at] % w, y = positions[at] / w;
                for (int[] offset : offsets) {
                    int nx = x + direction * offset[0], ny = y + direction * offset[1];
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                    int neighbor = java.util.Arrays.binarySearch(positions, ny * w + nx);
                    int span = Math.abs(offset[0]) + Math.abs(offset[1]);
                    float cost = span == 1 ? 1 : span == 2 ? 1.4f : 2.1969f;
                    core[at] = Math.min(core[at], (neighbor < 0 ? 0 : core[neighbor]) + cost);
                }
            }
        }
        for (int i = 0; i < positions.length; i++)
            if (energy[i] > 0) core[i] = (float) Math.exp(-.5 * Math.pow(core[i] / innerWidth, 2));
    }

    private static int label(int[] positions, int[] labels, int w, int h, BooleanSupplier cancelled) {
        int[] queue = new int[positions.length];
        int count = 0;
        for (int i = 0; i < positions.length; i++) {
            if ((i & 4095) == 0) check(cancelled);
            if (labels[i] != 0) continue;
            labels[i] = ++count;
            int head = 0, tail = 1;
            queue[0] = i;
            while (head < tail) {
                if ((head & 4095) == 0) check(cancelled);
                int p = positions[queue[head++]], x = p % w, y = p / w;
                for (int yy = Math.max(0, y - 1); yy <= Math.min(y + 1, h - 1); yy++) {
                    for (int xx = Math.max(0, x - 1); xx <= Math.min(x + 1, w - 1); xx++) {
                        int next = java.util.Arrays.binarySearch(positions, yy * w + xx);
                        if (next >= 0 && labels[next] == 0) {
                            labels[next] = count;
                            queue[tail++] = next;
                        }
                    }
                }
            }
        }
        return count;
    }

}
