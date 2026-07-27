package com.example.smartkid.data.model;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CourseDetailTest {

    @Test
    public void lessonsUnlockStrictlyInCourseOrderAcrossSections() {
        Lesson first = lesson("1", true);
        Lesson second = lesson("2", false);
        Lesson third = lesson("3", false);
        CourseDetail detail = detail(first, second, third);

        assertTrue(detail.isLessonUnlocked("1"));
        assertTrue(detail.isLessonUnlocked("2"));
        assertFalse(detail.isLessonUnlocked("3"));
        assertEquals("2", detail.getBlockingLesson("3").getId());
    }

    @Test
    public void firstIncompleteLessonBlocksTheNextChapter() {
        Lesson first = lesson("1", false);
        Lesson second = lesson("2", true);
        Lesson third = lesson("3", false);
        CourseDetail detail = detail(first, second, third);

        assertFalse(detail.isLessonUnlocked("3"));
        assertEquals("1", detail.getBlockingLesson("3").getId());
        assertNull(detail.getBlockingLesson("1"));
    }

    private CourseDetail detail(Lesson first, Lesson second, Lesson third) {
        CourseSection chapterOne = new CourseSection(
                "chapter-1", "Chương 1", Arrays.asList(first, second));
        CourseSection chapterTwo = new CourseSection(
                "chapter-2", "Chương 2", Arrays.asList(third));
        return new CourseDetail(null, Arrays.asList(chapterOne, chapterTwo));
    }

    private Lesson lesson(String id, boolean completed) {
        return new Lesson(id, "Bài " + id, "video", completed);
    }
}
