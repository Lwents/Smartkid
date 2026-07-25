package com.example.smartkid;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.smartkid.feature.shared.auth.RegisterActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RegisterActivityTest {

    @Test
    public void emptyPhone_showsErrorOnPhoneFieldWithoutCallingApi() {
        Intent intent = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                RegisterActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        onView(withId(R.id.inputRegisterUsername)).perform(replaceText("student_test"));
        onView(withId(R.id.inputRegisterEmail)).perform(replaceText("student@example.com"));
        onView(withId(R.id.inputRegisterPassword)).perform(replaceText("123456"));
        onView(withId(R.id.inputRegisterConfirmation))
                .perform(replaceText("123456"), closeSoftKeyboard());
        onView(withId(R.id.buttonRegister)).perform(scrollTo(), click());

        onView(withText("Vui lòng nhập số điện thoại")).check(matches(isDisplayed()));
    }
}
