package com.example.pencilsstencils

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainFlowTest {
    @Test fun userCanRegisterAndBrowseSeededCollection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("shop", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteDatabase("pencils_stencils.db")
        val activity = ActivityScenario.launch(MainActivity::class.java)
        val email = "test-${System.currentTimeMillis()}@example.com"
        onView(withText("Create account")).perform(click())
        onView(withHint("Full name")).perform(typeText("Test Creator"), closeSoftKeyboard())
        onView(withHint("Email address")).perform(typeText(email), closeSoftKeyboard())
        onView(withHint("Password (8+ characters)")).perform(typeText("Creative123!"), closeSoftKeyboard())
        onView(withHint("Confirm password")).perform(typeText("Creative123!"), closeSoftKeyboard())
        onView(withText("Create account")).perform(click())
        onView(withText("Shop the collection")).perform(click())
        onView(withText("Our Collection")).check(matches(isDisplayed()))
        onView(withText("Mixed Media Pad")).check(matches(isDisplayed()))
        onView(withText("Add to bag")).perform(click())
        onView(withText("Home")).perform(click())
        onView(withText("View bag (1)")).perform(click())
        onView(withText("Royal Ink Technical Pen")).check(matches(isDisplayed()))
        onView(withText("Home")).perform(click())
        onView(withText("Log out")).perform(click())
        onView(withHint("Email address")).perform(typeText(email), closeSoftKeyboard())
        onView(withHint("Password")).perform(typeText("Creative123!"), closeSoftKeyboard())
        onView(withText("Login")).perform(click())
        onView(withText("Shop the collection")).check(matches(isDisplayed()))
        activity.close()
    }
}
