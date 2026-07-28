package com.example.smartkid.common.ui.form;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class QuestionRenderPolicyTest {
    @Test
    public void fiftyQuestionExamOnlyExpandsTenEditors() {
        assertEquals(10, QuestionRenderPolicy.expandedCount(50));
        assertEquals(40, QuestionRenderPolicy.compactCount(50));
    }

    @Test
    public void smallExamKeepsEveryQuestionEditable() {
        assertEquals(6, QuestionRenderPolicy.expandedCount(6));
        assertEquals(0, QuestionRenderPolicy.compactCount(6));
    }
}
