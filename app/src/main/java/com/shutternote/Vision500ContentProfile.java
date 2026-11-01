package com.shutternote;
import org.json.*;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;

/** Changes only scene routing; approved colour, tungsten and grain remain pinned. */
final class Vision500ContentProfile {
    private final Vision500ApprovedProfile approved;
    private final Vision500ContentClassifier classifier;
    Vision500ContentProfile(JSONObject p,LearnedFilmGrain.Noise noise) throws JSONException {
        if(p.getInt("schema_version")!=33||!p.getString("method").equals("vision500_content_scene_v1")||
           !p.getString("color_space").equals("sRGB_D65_encoded")||!p.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))throw new JSONException("Unsupported 500T content contract");
        JSONObject base=p.getJSONObject("base_profile");
        if(base.getInt("schema_version")!=31||!base.getString("model_version").equals("9c628838-9615-5590-9c4f-51ce8721ae20"))throw new JSONException("Unsupported 500T approved baseline");
        approved=new Vision500ApprovedProfile(base,noise);classifier=new Vision500ContentClassifier(p.getJSONObject("classifier"));
    }
    void render(int[] pixels,int w,int h,BooleanSupplier cancelled,int[] analysis,int aw,int ah,double captureHour) throws IOException {
        if(w<=0||h<=0||(long)w*h!=pixels.length)throw new IllegalArgumentException("Invalid image");
        if(analysis!=null&&(aw<=0||ah<=0||(long)aw*ah!=analysis.length))throw new IllegalArgumentException("Invalid analysis image");
        check(cancelled);
        Vision500ContentClassifier.Decision decision=classifier.evaluate(analysis==null?pixels:analysis,analysis==null?w:aw,analysis==null?h:ah,cancelled);
        double day=Vision500SceneTime.dayWeight(decision.dayWeight,captureHour);
        approved.renderWithDayWeight(pixels,w,h,cancelled,analysis,aw,ah,day);
    }
}
