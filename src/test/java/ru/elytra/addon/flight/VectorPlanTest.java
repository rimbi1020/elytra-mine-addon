package ru.elytra.addon.flight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorPlanTest {
    @Test
    void createsCorrectTwoVectorPlan() {
        TargetSurface target = new TargetSurface(new BlockPosI(0, 5, 0), 6.0, true);
        VectorPlan plan = VectorPlan.create(new Vec3d(0, 0, 0), target, 3, 3, 2);

        assertEquals(0.5, plan.p1().x(), 1.0E-6);
        assertEquals(9.0, plan.p1().y(), 1.0E-6);
        assertEquals(0.5, plan.p1().z(), 1.0E-6);

        assertTrue(plan.v1().y() > 0);
        assertTrue(plan.v1().y() > Math.abs(plan.v1().x()));
        assertEquals(0.0, plan.v2().x(), 1.0E-6);
        assertEquals(-1.0, plan.v2().y(), 1.0E-6);
        assertEquals(0.0, plan.v2().z(), 1.0E-6);

        assertFalse(plan.isDegenerate());
    }

    @Test
    void horizontalOffsetMakesV1Diagonal() {
        TargetSurface target = new TargetSurface(new BlockPosI(4, 5, 0), 6.0, true);
        VectorPlan plan = VectorPlan.create(new Vec3d(0, 0, 0), target, 3, 3, 2);

        assertTrue(plan.v1().x() > 0.1);
        assertTrue(plan.v1().x() > Math.abs(plan.v1().z()));
        assertTrue(Math.abs(plan.v1().z()) < 0.1);
        assertTrue(plan.v1().y() > 0);
        assertFalse(plan.isDegenerate());
    }

    @Test
    void degenerateWhenP1EqualsP0() {
        TargetSurface target = new TargetSurface(new BlockPosI(0, 0, 0), 1.0, true);
        VectorPlan plan = VectorPlan.create(new Vec3d(0.5, 1.0, 0.5), target, 0, 3, 2);
        assertTrue(plan.isDegenerate());
    }

    @Test
    void tickSequenceFinishesBothVectors() {
        TargetSurface target = new TargetSurface(new BlockPosI(0, 5, 0), 6.0, true);
        VectorPlan plan = VectorPlan.create(new Vec3d(0, 0, 0), target, 3, 3, 2);

        plan.begin();
        assertTrue(plan.inV1());

        assertFalse(plan.tickV1());
        assertFalse(plan.tickV1());
        assertTrue(plan.tickV1());
        assertEquals(3, plan.tick());

        plan.startV2();
        assertFalse(plan.inV1());
        assertTrue(plan.inV2());
        assertFalse(plan.tickV2());
        assertTrue(plan.tickV2());
        assertTrue(plan.finished());
    }
}