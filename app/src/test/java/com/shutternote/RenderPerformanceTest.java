package com.shutternote;
import org.junit.Test;
import java.nio.file.*;
public class RenderPerformanceTest {
    @Test public void masksAreExactAtBoundariesAndAcrossDensities(){RenderPerformanceCases.geometry();}
    @Test public void filterSummationAndReflectedEdgesAreExact(){RenderPerformanceCases.filters();}
    @Test public void realAndSyntheticPixelsAreExactAndTimeIsMeasured() throws Exception {
        org.junit.Assume.assumeTrue("Private performance fixtures are local only",getClass().getResource("/render-speed/8.bin.gz")!=null);
        org.json.JSONObject result=RenderPerformanceCases.run(name->Files.newInputStream(Path.of("src/main/assets",name)),name->getClass().getResourceAsStream("/"+name),3);
        Path out=Path.of("../outputs/analysis/mobile-render-speed-20261027/jvm-results.json");Files.createDirectories(out.getParent());Files.writeString(out,result.toString(2));
    }
}
