package com.example.smartkid.feature.teacher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.smartkid.common.ui.FeatureSpec;

import org.junit.Test;

public class TeacherManagementSpecTest {

    @Test
    public void studentFeedbackUsesSentFeedbackHistory() {
        FeatureSpec spec = TeacherManagementSpec.get("teacher_feedback");

        assertNotNull(spec);
        assertEquals("teacher/students/feedback/", spec.getEndpoint());
        assertEquals("teacher_feedback", spec.getActionKind());
    }
}
