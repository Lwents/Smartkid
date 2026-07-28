package com.example.smartkid.feature.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdminCourseVideoActionsTest {
    @Test
    public void buildsCourseAndVideoEndpoints() {
        assertEquals("admin/courses/course-1/",
                AdminCourseVideoActions.courseDetailEndpoint(" course-1 "));
        assertEquals("admin/courses/course-1/lessons/lesson-2/video/",
                AdminCourseVideoActions.deleteVideoEndpoint("course-1", "lesson-2"));
    }

    @Test
    public void onlyVideosWithAValidLessonIdCanBeManaged() {
        assertTrue(AdminCourseVideoActions.canManageVideo(true, "lesson-2"));
        assertFalse(AdminCourseVideoActions.canManageVideo(false, "lesson-2"));
        assertFalse(AdminCourseVideoActions.canManageVideo(true, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> AdminCourseVideoActions.deleteVideoEndpoint("", "lesson-2"));
        assertThrows(IllegalArgumentException.class,
                () -> AdminCourseVideoActions.deleteVideoEndpoint("course-1", ""));
    }
}
