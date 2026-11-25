package com.expensetracker.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PetTest {
    @Test
    void levelsNeedMoreXpEachStep() {
        assertEquals(1, Pet.levelFor(0));
        assertEquals(1, Pet.levelFor(49));
        assertEquals(2, Pet.levelFor(50));
        assertEquals(2, Pet.levelFor(149));
        assertEquals(3, Pet.levelFor(150));
    }

    @Test
    void streakCountsBackFromTodayOrYesterday() {
        LocalDate today = LocalDate.of(2026, 11, 25);
        assertEquals(3, Pet.streak(List.of(today, today.minusDays(1), today.minusDays(2), today.minusDays(5)), today));
        assertEquals(2, Pet.streak(List.of(today.minusDays(1), today.minusDays(2)), today));
        assertEquals(0, Pet.streak(List.of(today.minusDays(2)), today));
        assertEquals(0, Pet.streak(List.of(), today));
    }

    @Test
    void moodLabels() {
        assertEquals("HAPPY", Pet.moodLabel(100));
        assertEquals("OKAY", Pet.moodLabel(60));
        assertEquals("HUNGRY", Pet.moodLabel(30));
        assertEquals("STARVING", Pet.moodLabel(0));
    }
}
