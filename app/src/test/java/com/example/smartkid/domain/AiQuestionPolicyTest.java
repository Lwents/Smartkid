package com.example.smartkid.domain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AiQuestionPolicyTest {
    @Test
    public void supportedCountsIncludeFiftyButRejectArbitraryValues() {
        assertArrayEquals(new int[]{5, 10, 20, 30, 50}, AiQuestionPolicy.allowedCounts());
        assertTrue(AiQuestionPolicy.isAllowedCount(50));
        assertFalse(AiQuestionPolicy.isAllowedCount(49));
    }

    @Test
    public void generatedQuestionCountNeverExceedsTheEditorLimit() {
        assertEquals(0, AiQuestionPolicy.clampGeneratedCount(-1));
        assertEquals(30, AiQuestionPolicy.clampGeneratedCount(30));
        assertEquals(50, AiQuestionPolicy.clampGeneratedCount(80));
        assertEquals(50, AiQuestionPolicy.remainingCapacity(0));
        assertEquals(8, AiQuestionPolicy.remainingCapacity(42));
        assertEquals(0, AiQuestionPolicy.remainingCapacity(70));
    }

    @Test
    public void documentSizeAllowsUnknownOrAtMostTwentyMegabytes() {
        assertTrue(AiQuestionPolicy.acceptsDocumentSize(-1));
        assertTrue(AiQuestionPolicy.acceptsDocumentSize(AiQuestionPolicy.MAX_DOCUMENT_BYTES));
        assertFalse(AiQuestionPolicy.acceptsDocumentSize(
                AiQuestionPolicy.MAX_DOCUMENT_BYTES + 1));
    }
}
