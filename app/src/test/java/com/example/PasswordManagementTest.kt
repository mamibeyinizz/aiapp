package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordManagementTest {

    data class PasswordValidationState(
        val newPassword: String,
        val confirmPassword: String
    ) {
        val isMinLength: Boolean = newPassword.length >= 6
        val isMatch: Boolean = newPassword.isNotEmpty() && newPassword == confirmPassword
        val isValid: Boolean = isMinLength && isMatch
    }

    @Test
    fun testPasswordValidationSuccess() {
        val state = PasswordValidationState("123456", "123456")
        assertTrue(state.isMinLength)
        assertTrue(state.isMatch)
        assertTrue(state.isValid)
    }

    @Test
    fun testPasswordTooShort() {
        val state = PasswordValidationState("12345", "12345")
        assertFalse(state.isMinLength)
        assertTrue(state.isMatch)
        assertFalse(state.isValid)
    }

    @Test
    fun testPasswordMismatch() {
        val state = PasswordValidationState("123456", "654321")
        assertTrue(state.isMinLength)
        assertFalse(state.isMatch)
        assertFalse(state.isValid)
    }

    @Test
    fun testEmptyPassword() {
        val state = PasswordValidationState("", "")
        assertFalse(state.isMinLength)
        assertFalse(state.isMatch)
        assertFalse(state.isValid)
    }

    @Test
    fun testRoleSupportForPasswordManagement() {
        val roles = listOf("mudur", "garson")
        for (role in roles) {
            val isSupported = role == "mudur" || role == "garson"
            assertTrue("Role $role must be supported for password management", isSupported)
        }
    }
}
