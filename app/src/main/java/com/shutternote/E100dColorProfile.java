package com.shutternote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Portable schema-4 E100D renderer. No Android classes or training runtime. */
final class E100dColorProfile {
    static final String VERSION = "c90de76b-0cd3-484f-b2a7-e4b5eedeff99";
    private static final double[] Y = {.2126f, .7152f, .0722f};
    private final double[] tone, neutralTone, color, neutral, contextTone, contextNeutralTone,
            contextColor, contextNeutral, referenceY, originalY;
    private final double[] center, scale, axes, projectionScale, trainingScenes;
    private final int rank, grid;
    private final double[] baseTone, baseTint, baseToneGrid, baseColorGrid;

    E100dColorProfile(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.getInt("schema_version") != 4 || !root.getString("film_name").equals("E100D")
                || !root.getString("model_version").equals(VERSION)
                || !root.getString("color_space").equals("sRGB_D65_encoded")
                || !root.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))
            throw new JSONException("Unsupported correction contract");
        JSONObject effects = root.getJSONObject("effects");
        requireZero(effects.getJSONObject("halation").getJSONArray("gain_rgb"));
        requireZero(effects.getJSONObject("grain").getJSONArray("covariance_rgb"));
        JSONObject profile = effects.getJSONObject("separated_color");
        if (!profile.getString("method").equals("linear_context_v3")
                || profile.getDouble("common_limit") != .5 || profile.getDouble("context_limit") != .06)
            throw new JSONException("Unsupported contextual response");
        JSONObject context = profile.getJSONObject("context");
        rank = context.getInt("rank");
        if (rank < 0 || rank > 3) throw new JSONException("Invalid context rank");
        center = array(context, "center", 8); scale = array(context, "scale", 8);
        axes = array(context, "axes", rank * 8); projectionScale = array(context, "projection_scale", rank);
        trainingScenes = array(context, "training_scene_descriptors", context.getInt("scene_groups") * 8);
        if (trainingScenes.length == 0) throw new JSONException("No scene references");
        for (double value : scale) if (value <= 0) throw new JSONException("Invalid scene scale");
        for (double value : projectionScale) if (value <= 0) throw new JSONException("Invalid projection scale");
        tone = array(profile, "tone", 10); neutralTone = array(profile, "neutral_tone", 3);
        color = array(profile, "color", 30); neutral = array(profile, "neutral", 9);
        contextTone = array(profile, "context_tone", rank * 4);
        contextNeutralTone = array(profile, "context_neutral_tone", rank * 3);
        contextColor = array(profile, "context_color", rank * 12);
        contextNeutral = array(profile, "context_neutral", rank * 9);
        referenceY = array(profile, "neutral_reference_y", 257);
        originalY = array(profile, "neutral_reference_original_y", 257);
        JSONObject base = profile.getJSONObject("base_profile");
        if (!base.getString("method").equals("rgb_tone_chroma_v2")
                || base.getDouble("neutral_threshold") != .025 || base.getDouble("chroma_gate") != .06)
            throw new JSONException("Unsupported base response");
        baseTone = array(base, "tone", 33); baseTint = array(base, "neutral_tint", 99);
        grid = base.getJSONArray("tone_rgb").length();
        if (grid < 3 || grid > 33) throw new JSONException("Invalid grid size");
        baseToneGrid = array(base, "tone_rgb", grid * grid * grid);
        baseColorGrid = array(base, "chroma_rgb", grid * grid * grid * 3);
    }

    private static double[] array(JSONObject object, String key, int length) throws JSONException {
        double[] output = new double[length];
        int used = flatten(object.getJSONArray(key), output, 0);
        if (used != length) throw new JSONException("Invalid array length: " + key);
        return output;
    }
    private static int flatten(JSONArray input, double[] output, int at) throws JSONException {
        for (int i = 0; i < input.length(); i++) {
            Object value = input.get(i);
            if (value instanceof JSONArray) at = flatten((JSONArray) value, output, at);
            else {
                if (!(value instanceof Number) || at >= output.length) throw new JSONException("Invalid coefficients");
                double number = ((Number) value).doubleValue();
                if (!Double.isFinite(number)) throw new JSONException("Nonfinite coefficient");
                output[at++] = number;
            }
        }
        return at;
    }
    private static void requireZero(JSONArray array) throws JSONException {
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) requireZero((JSONArray) value);
            else if (!(value instanceof Number) || ((Number) value).doubleValue() != 0)
                throw new JSONException("Nonzero texture requires a different renderer");
        }
    }

    /** Changes this working pixel buffer only; callers keep the source bitmap intact. */
    void render(int[] pixels, int width, int height, BooleanSupplier cancelled) {
        if (width <= 0 || height <= 0 || (long) width * height != pixels.length) throw new IllegalArgumentException("Invalid image");
        double[] scene = embedding(descriptor(pixels, width, height));
        double[] qt = combine(scene, contextTone, 4), qnt = combine(scene, contextNeutralTone, 3);
        double[] qc = combine(scene, contextColor, 12), qnc = combine(scene, contextNeutral, 9);
        double[] gray = new double[257];
        for (int i = 0; i < gray.length; i++) {
            double level = i / 256.0;
            gray[i] = referenceY[i] + .5 * Math.tanh(neutralTone[0] + neutralTone[1] * level + neutralTone[2] * level * level)
                    + .06 * Math.tanh(qnt[0] + qnt[1] * level + qnt[2] * level * level);
        }
        gray = monotone(gray);
        for (int i = 0; i < gray.length; i++) gray[i] = clamp(gray[i]);
        double[] p = new double[10], n = new double[3], q = new double[4], base = new double[3], chroma = new double[3];
        for (int i = 0; i < pixels.length; i++) {
            if ((i & 1023) == 0 && (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())) throw new CancellationException();
            int packed = pixels[i];
            double r = (float) (((packed >>> 16) & 255) / 255.0), g = (float) (((packed >>> 8) & 255) / 255.0), b = (float) ((packed & 255) / 255.0);
            double level = (float) (r * Y[0] + g * Y[1] + b * Y[2]);
            double spread = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
            double gate = Math.pow(clamp((spread - .025) / .035), 2);
            base(r, g, b, level, gate, base);
            for (int c = 0; c < 3; c++) base[c] = linear((float) base[c]);
            double baseY = brightness(base);
            p[0] = 1; p[1] = r; p[2] = g; p[3] = b; p[4] = r*r; p[5] = g*g; p[6] = b*b; p[7] = r*g; p[8] = r*b; p[9] = g*b;
            n[0] = 1; n[1] = level; n[2] = level*level;
            q[0] = 1; q[1] = level; q[2] = level*level; q[3] = spread;
            double nt = dot(n, neutralTone), cnt = dot(n, qnt);
            double rawTone = baseY + .5 * Math.tanh(gate * dot(p, tone) + (1-gate) * nt)
                    + .06 * Math.tanh(gate * dot(q, qt) + (1-gate) * cnt);
            double projected = curve(gray, level, 0, 1);
            double original = curve(originalY, level, 0, 1) + .5 * Math.tanh(nt) + .06 * Math.tanh(cnt);
            rawTone = gate == 0 ? projected : rawTone + (1-gate) * (projected-original);
            for (int c = 0; c < 3; c++) {
                base[c] -= baseY;
                chroma[c] = Math.tanh(gate * dotChannel(p, color, c) + (1-gate) * dotChannel(n, neutral, c));
            }
            double sharedY = brightness(chroma);
            for (int c = 0; c < 3; c++) base[c] += .5 * (chroma[c] - sharedY);
            for (int c = 0; c < 3; c++) chroma[c] = Math.tanh(gate * dotChannel(q, qc, c) + (1-gate) * dotChannel(n, qnc, c));
            double contextualY = brightness(chroma);
            for (int c = 0; c < 3; c++) base[c] += .06 * (chroma[c] - contextualY);
            gamut(clamp(rawTone), base);
            pixels[i] = (packed & 0xff000000) | quantize(srgb(base[0])) << 16 | quantize(srgb(base[1])) << 8 | quantize(srgb(base[2]));
        }
    }

    private void base(double r, double g, double b, double level, double gate, double[] output) {
        double pr = r * (grid-1), pg = g * (grid-1), pb = b * (grid-1);
        int ir = Math.min((int) pr, grid-2), ig = Math.min((int) pg, grid-2), ib = Math.min((int) pb, grid-2);
        double fr = pr-ir, fg = pg-ig, fb = pb-ib, t = 0;
        Arrays.fill(output, 0);
        for (int dr = 0; dr < 2; dr++) for (int dg = 0; dg < 2; dg++) for (int db = 0; db < 2; db++) {
            double weight = (float) ((dr == 1 ? fr : 1-fr) * (dg == 1 ? fg : 1-fg) * (db == 1 ? fb : 1-fb));
            int index = ((ir+dr)*grid+ig+dg)*grid+ib+db;
            t += weight * baseToneGrid[index];
            for (int c = 0; c < 3; c++) output[c] += weight * baseColorGrid[index*3+c];
        }
        t = (float) ((1-gate) * curve(baseTone, level, 0, 1) + gate*t);
        for (int c = 0; c < 3; c++) output[c] = (float) ((1-gate) * curve(baseTint, level, c, 3) + gate*output[c]);
        gamut(t, output);
    }

    private double[] embedding(double[] descriptor) {
        double[] z = new double[8], scene = new double[rank];
        for (int j = 0; j < 8; j++) z[j] = (descriptor[j]-center[j])/scale[j];
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < trainingScenes.length; i += 8) {
            double sum = 0;
            for (int j = 0; j < 8; j++) { double d = z[j] - (trainingScenes[i+j]-center[j])/scale[j]; sum += d*d; }
            distance = Math.min(distance, sum/8);
        }
        double excess = Math.max(Math.sqrt(distance)-1, 0), confidence = Math.exp(-excess*excess);
        for (int k = 0; k < rank; k++) {
            double projected = 0;
            for (int j = 0; j < 8; j++) projected += z[j]*axes[k*8+j];
            scene[k] = (float) (3 * Math.tanh(projected/projectionScale[k]/3) * confidence);
        }
        return scene;
    }

    /** Area resample of the whole encoded source to the PC renderer's 64×64 descriptor. */
    static double[] descriptor(int[] pixels, int width, int height) {
        double[] sums = new double[8];
        double sumSquare = 0;
        for (int sy = 0; sy < 64; sy++) for (int sx = 0; sx < 64; sx++) {
            double x0 = sx * width / 64.0, x1 = (sx+1)*width/64.0, y0 = sy*height/64.0, y1 = (sy+1)*height/64.0;
            double r = 0, g = 0, b = 0, area = (x1-x0)*(y1-y0);
            for (int y = (int) y0; y < Math.ceil(y1); y++) for (int x = (int) x0; x < Math.ceil(x1); x++) {
                double weight = (Math.min(x1, x+1)-Math.max(x0, x)) * (Math.min(y1, y+1)-Math.max(y0, y)) / area;
                int pixel = pixels[Math.min(y, height-1)*width+Math.min(x, width-1)];
                r += weight * (float) (((pixel >>> 16) & 255)/255.0); g += weight * (float) (((pixel >>> 8) & 255)/255.0); b += weight * (float) ((pixel & 255)/255.0);
            }
            r = (float) r; g = (float) g; b = (float) b;
            double y = (float) (r*Y[0]+g*Y[1]+b*Y[2]), spread = (float) (Math.max(r, Math.max(g, b))-Math.min(r, Math.min(g, b)));
            sums[0] += y; sumSquare += y*y;
            sums[2] += 1/(1+Math.exp((y-.2)*12)); sums[3] += 1/(1+Math.exp((.8-y)*12));
            sums[4] += r-y; sums[5] += b-y; sums[6] += spread; sums[7] += Math.exp(-Math.pow(spread/.08, 2));
        }
        for (int i = 0; i < 8; i++) sums[i] = (float) (sums[i]/4096);
        sums[1] = (float) Math.sqrt(Math.max(0, sumSquare/4096-sums[0]*sums[0]));
        return sums;
    }

    private static double[] combine(double[] scene, double[] coefficients, int count) {
        double[] result = new double[count];
        for (int k = 0; k < scene.length; k++) for (int j = 0; j < count; j++) result[j] += scene[k]*coefficients[k*count+j];
        return result;
    }
    private static double dot(double[] basis, double[] coefficients) {
        double result = 0; for (int i = 0; i < basis.length; i++) result += basis[i]*coefficients[i]; return result;
    }
    private static double dotChannel(double[] basis, double[] coefficients, int channel) {
        double result = 0; for (int i = 0; i < basis.length; i++) result += basis[i]*coefficients[i*3+channel]; return result;
    }
    private static double curve(double[] curve, double level, int channel, int channels) {
        int count = curve.length/channels; double position = clamp(level)*(count-1);
        int lo = Math.min((int) position, count-2); double fraction = position-lo;
        return curve[lo*channels+channel]*(1-fraction)+curve[(lo+1)*channels+channel]*fraction;
    }
    private static double[] monotone(double[] input) {
        double[] sums = new double[input.length]; int[] counts = new int[input.length]; int blocks = 0;
        for (double value : input) {
            sums[blocks] = value; counts[blocks++] = 1;
            while (blocks > 1 && sums[blocks-2]/counts[blocks-2] > sums[blocks-1]/counts[blocks-1]) {
                sums[blocks-2] += sums[blocks-1]; counts[blocks-2] += counts[blocks-1]; blocks--;
            }
        }
        double[] output = new double[input.length]; int at = 0;
        for (int i = 0; i < blocks; i++) for (int j = 0; j < counts[i]; j++) output[at++] = (float) (sums[i]/counts[i]);
        return output;
    }
    private static void gamut(double tone, double[] color) {
        double y = brightness(color), scale = 1;
        for (int c = 0; c < 3; c++) {
            color[c] -= y;
            double limit = color[c] > 0 ? (1-tone)/Math.max(color[c], 1e-8) : tone/Math.max(-color[c], 1e-8);
            scale = Math.min(scale, limit);
        }
        for (int c = 0; c < 3; c++) color[c] = (float) clamp(tone+color[c]*Math.max(scale, 0));
    }
    private static double brightness(double[] rgb) { return rgb[0]*Y[0]+rgb[1]*Y[1]+rgb[2]*Y[2]; }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static double linear(double value) { return value <= .04045 ? value/12.92 : Math.pow((value+.055)/1.055, 2.4); }
    private static double srgb(double value) { return value <= .0031308 ? 12.92*value : 1.055*Math.pow(value, 1/2.4)-.055; }
    private static int quantize(double value) { return (int) Math.rint(clamp(value)*255); }
}
