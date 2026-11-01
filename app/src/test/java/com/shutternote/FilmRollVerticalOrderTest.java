package com.shutternote;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class FilmRollVerticalOrderTest {
    @Test public void neighborsOnlyMoveWhenTheDraggedEdgePassesTheirMiddle() {
        FilmRollVerticalOrder order=new FilmRollVerticalOrder(new int[]{100,100,100},1);
        order.move(145); assertEquals(1,order.target()); assertEquals(0,order.siblingOffset(2));
        order.move(160); assertEquals(2,order.target()); assertEquals(-100,order.siblingOffset(2));
        order.move(40); assertEquals(0,order.target()); assertEquals(100,order.siblingOffset(0));
        order.move(100); assertEquals(1,order.target()); assertEquals(0,order.siblingOffset(0));
    }
    @Test public void aTallDatedRollCanMoveAcrossShorterRollsInEitherDirection() {
        FilmRollVerticalOrder down=new FilmRollVerticalOrder(new int[]{300,80,90},0);
        down.move(170); assertEquals(2,down.target()); assertEquals(170,down.settledOffset());
        assertEquals(-300,down.siblingOffset(1)); assertEquals(-300,down.siblingOffset(2));
        FilmRollVerticalOrder up=new FilmRollVerticalOrder(new int[]{80,90,300},2);
        up.move(0); assertEquals(0,up.target()); assertEquals(-170,up.settledOffset());
        assertEquals(300,up.siblingOffset(0)); assertEquals(300,up.siblingOffset(1));
    }
    @Test public void firstAndLastEdgesClampWithoutLeavingTheList() {
        FilmRollVerticalOrder order=new FilmRollVerticalOrder(new int[]{80,120,60},1);
        order.move(-10000); assertEquals(-80,order.offset(),.001f); assertEquals(0,order.target());
        order.move(10000); assertEquals(60,order.offset(),.001f); assertEquals(2,order.target());
        FilmRollVerticalOrder single=new FilmRollVerticalOrder(new int[]{64},0);
        single.move(10000); assertEquals(0,single.offset(),.001f); assertEquals(0,single.target());
    }
    @Test public void settledOffsetsMatchActualVariableHeightLayoutForEveryMove() {
        int[] heights={64,140,90,210,72};
        for (int source=0;source<heights.length;source++) for(int target=0;target<heights.length;target++) {
            FilmRollVerticalOrder order=new FilmRollVerticalOrder(heights,source); order.target(target);
            List<Integer> indices=new ArrayList<>(); for(int i=0;i<heights.length;i++) indices.add(i);
            indices.remove(source); indices.add(target,source);
            int top=0;
            for(int index:indices) {
                int oldTop=0; for(int i=0;i<index;i++) oldTop+=heights[i];
                int offset=index==source?order.settledOffset():order.siblingOffset(index);
                assertEquals(top,oldTop+offset); top+=heights[index];
            }
        }
    }
    @Test public void geometryIsIndependentOfTheCallersArrayAndCanReturnToOriginalOrder() {
        int[] heights={80,120,90}; FilmRollVerticalOrder order=new FilmRollVerticalOrder(heights,0);
        heights[0]=1000; order.move(210); assertEquals(2,order.target());
        assertEquals(-80,order.siblingOffset(1)); order.target(0);
        assertEquals(0,order.offset(),.001f); assertEquals(0,order.siblingOffset(1));
    }
}
