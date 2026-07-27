package com.example.smartkid.common.ui.form;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.widget.LinearLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.User;
import com.example.smartkid.feature.teacher.exercise.TeacherExerciseEditorActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class ContentFormAiStressTest {
    private Context context;

    @Before
    public void setUpTeacherSession() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new SessionManager(context).saveSession(
                "test-access",
                "test-refresh",
                new User("teacher-id", "teacher", "Giáo viên", "", "instructor", "")
        );
    }

    @After
    public void clearSession() {
        new SessionManager(context).clear();
    }

    @Test
    public void generatedFiftyQuestionsRenderAsFiftyEditableCards() throws Exception {
        Intent intent = new Intent(context, TeacherExerciseEditorActivity.class)
                .putExtra(TeacherExerciseEditorActivity.EXTRA_SCOPE,
                        ExerciseScope.STANDALONE_EXAM.name());
        JSONArray generated = generatedQuestions(50);

        try (ActivityScenario<TeacherExerciseEditorActivity> scenario =
                     ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                try {
                    Method apply = ContentFormActivity.class.getDeclaredMethod(
                            "applyGeneratedQuestions", JSONArray.class);
                    apply.setAccessible(true);
                    int added = (int) apply.invoke(activity, generated);

                    Field containerField = ContentFormActivity.class.getDeclaredField(
                            "questionsContainer");
                    containerField.setAccessible(true);
                    LinearLayout container = (LinearLayout) containerField.get(activity);

                    assertEquals(50, added);
                    assertEquals(50, container.getChildCount());
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            });
        }
    }

    private static JSONArray generatedQuestions(int count) throws Exception {
        JSONArray questions = new JSONArray();
        for (int index = 1; index <= count; index++) {
            questions.put(new JSONObject()
                    .put("type", "single")
                    .put("text", "Câu hỏi số " + index + "?")
                    .put("score", 1)
                    .put("choices", new JSONArray()
                            .put("Đáp án đúng " + index)
                            .put("Đáp án sai A " + index)
                            .put("Đáp án sai B " + index)
                            .put("Đáp án sai C " + index))
                    .put("correct_indices", new JSONArray().put(0)));
        }
        return questions;
    }
}
