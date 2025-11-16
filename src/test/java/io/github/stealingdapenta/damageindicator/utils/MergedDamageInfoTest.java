package io.github.stealingdapenta.damageindicator.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MergedDamageInfoTest {

    private ArmorStand mockArmorStand;
    private TextColor testColor;

    @BeforeEach
    void setUp() {
        mockArmorStand = mock(ArmorStand.class);
        testColor = TextColor.color(255, 0, 0);
    }

    @Test
    void testConstructor_InitializesFieldsCorrectly() {
        double initialDamage = 5.5;

        MergedDamageInfo info = new MergedDamageInfo(initialDamage, testColor, mockArmorStand);

        assertEquals(initialDamage, info.getTotalDamage(), 0.001);
        assertEquals(testColor, info.getLatestColor());
        assertEquals(mockArmorStand, info.getArmorStand());
        assertNull(info.getTimeoutTask());
        assertTrue(info.getLastDamageTime() > 0);
    }

    @Test
    void testAddDamage_AccumulatesDamage() {
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, mockArmorStand);

        info.addDamage(3.0);
        assertEquals(8.0, info.getTotalDamage(), 0.001);

        info.addDamage(2.5);
        assertEquals(10.5, info.getTotalDamage(), 0.001);
    }

    @Test
    void testAddDamage_UpdatesLastDamageTime() throws InterruptedException {
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, mockArmorStand);
        long firstTime = info.getLastDamageTime();

        Thread.sleep(10); // Small delay
        info.addDamage(3.0);
        long secondTime = info.getLastDamageTime();

        assertTrue(secondTime > firstTime, "Last damage time should be updated");
    }

    @Test
    void testSetLatestColor_UpdatesColor() {
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, mockArmorStand);
        TextColor newColor = TextColor.color(0, 255, 0);

        info.setLatestColor(newColor);

        assertEquals(newColor, info.getLatestColor());
    }

    @Test
    void testSetTimeoutTask_StoresTask() {
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, mockArmorStand);
        BukkitTask mockTask = mock(BukkitTask.class);

        info.setTimeoutTask(mockTask);

        assertEquals(mockTask, info.getTimeoutTask());
    }

    @Test
    void testSetTimeoutTask_CancelsPreviousTask() {
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, mockArmorStand);
        BukkitTask oldTask = mock(BukkitTask.class);
        BukkitTask newTask = mock(BukkitTask.class);

        when(oldTask.isCancelled()).thenReturn(false);

        info.setTimeoutTask(oldTask);
        info.setTimeoutTask(newTask);

        verify(oldTask).cancel();
        assertEquals(newTask, info.getTimeoutTask());
    }

    @Test
    void testSetTimeoutTask_DoesNotCancelAlreadyCancelledTask() {
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, mockArmorStand);
        BukkitTask oldTask = mock(BukkitTask.class);
        BukkitTask newTask = mock(BukkitTask.class);

        when(oldTask.isCancelled()).thenReturn(true);

        info.setTimeoutTask(oldTask);
        info.setTimeoutTask(newTask);

        verify(oldTask, never()).cancel();
        assertEquals(newTask, info.getTimeoutTask());
    }

    @Test
    void testCleanup_CancelsTaskAndRemovesArmorStand() {
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, mockArmorStand);
        BukkitTask mockTask = mock(BukkitTask.class);

        when(mockTask.isCancelled()).thenReturn(false);
        when(mockArmorStand.isValid()).thenReturn(true);

        info.setTimeoutTask(mockTask);
        info.cleanup();

        verify(mockTask).cancel();
        verify(mockArmorStand).remove();
    }

    @Test
    void testCleanup_DoesNotCancelAlreadyCancelledTask() {
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, mockArmorStand);
        BukkitTask mockTask = mock(BukkitTask.class);

        when(mockTask.isCancelled()).thenReturn(true);
        when(mockArmorStand.isValid()).thenReturn(true);

        info.setTimeoutTask(mockTask);
        info.cleanup();

        verify(mockTask, never()).cancel();
        verify(mockArmorStand).remove();
    }

    @Test
    void testCleanup_DoesNotRemoveInvalidArmorStand() {
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, mockArmorStand);

        when(mockArmorStand.isValid()).thenReturn(false);

        info.cleanup();

        verify(mockArmorStand, never()).remove();
    }

    @Test
    void testCleanup_HandlesNullTask() {
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, mockArmorStand);

        when(mockArmorStand.isValid()).thenReturn(true);

        // Should not throw exception
        assertDoesNotThrow(() -> info.cleanup());
        verify(mockArmorStand).remove();
    }

    @Test
    void testCleanup_HandlesNullArmorStand() {
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, null);
        BukkitTask mockTask = mock(BukkitTask.class);

        when(mockTask.isCancelled()).thenReturn(false);

        info.setTimeoutTask(mockTask);

        // Should not throw exception
        assertDoesNotThrow(() -> info.cleanup());
        verify(mockTask).cancel();
    }

    @Test
    void testGetTotalDamage_ReturnsExactValue() {
        double damage = 12.345;
        MergedDamageInfo info = new MergedDamageInfo(damage, testColor, mockArmorStand);

        assertEquals(damage, info.getTotalDamage(), 0.0001);
    }

    @Test
    void testAddDamage_WithNegativeValue_StillAdds() {
        MergedDamageInfo info = new MergedDamageInfo(10.0, testColor, mockArmorStand);

        info.addDamage(-2.0);

        assertEquals(8.0, info.getTotalDamage(), 0.001);
    }

    @Test
    void testLastDamageTime_IsRecentOnCreation() {
        long beforeCreation = System.currentTimeMillis();
        MergedDamageInfo info = new MergedDamageInfo(5.0, testColor, mockArmorStand);
        long afterCreation = System.currentTimeMillis();

        long lastDamageTime = info.getLastDamageTime();

        assertTrue(lastDamageTime >= beforeCreation);
        assertTrue(lastDamageTime <= afterCreation);
    }
}

