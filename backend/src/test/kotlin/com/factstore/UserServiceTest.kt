package com.factstore

import com.factstore.application.UserService
import com.factstore.dto.CreateUserRequest
import com.factstore.dto.UpdateUserRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    lateinit var userService: UserService

    @Test
    fun `create user succeeds`() {
        val req = CreateUserRequest(email = "alice@example.com", name = "Alice")
        val resp = userService.createUser(req)
        assertEquals("alice@example.com", resp.email)
        assertEquals("Alice", resp.name)
        assertNull(resp.githubId)
        assertNotNull(resp.id)
    }

    @Test
    fun `create user with github id succeeds`() {
        val req = CreateUserRequest(email = "bob@example.com", name = "Bob", githubId = "bob-gh-42")
        val resp = userService.createUser(req)
        assertEquals("bob-gh-42", resp.githubId)
    }

    @Test
    fun `create user with duplicate email throws ConflictException`() {
        userService.createUser(CreateUserRequest("dup@example.com", "First"))
        assertThrows<ConflictException> {
            userService.createUser(CreateUserRequest("dup@example.com", "Second"))
        }
    }

    @Test
    fun `get user by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            userService.getUser(UUID.randomUUID())
        }
    }

    @Test
    fun `list users returns all users`() {
        userService.createUser(CreateUserRequest("u1@example.com", "U1"))
        userService.createUser(CreateUserRequest("u2@example.com", "U2"))
        val users = userService.listUsers()
        assertTrue(users.size >= 2)
    }

    @Test
    fun `update user name`() {
        val created = userService.createUser(CreateUserRequest("upd@example.com", "OldName"))
        val updated = userService.updateUser(created.id, UpdateUserRequest(name = "NewName"))
        assertEquals("NewName", updated.name)
        assertEquals("upd@example.com", updated.email)
    }

    @Test
    fun `update user githubId`() {
        val created = userService.createUser(CreateUserRequest("ghupd@example.com", "Dev"))
        val updated = userService.updateUser(created.id, UpdateUserRequest(githubId = "dev-github-99"))
        assertEquals("dev-github-99", updated.githubId)
    }

    @Test
    fun `delete user removes it`() {
        val created = userService.createUser(CreateUserRequest("del@example.com", "ToDelete"))
        userService.deleteUser(created.id)
        assertThrows<NotFoundException> { userService.getUser(created.id) }
    }

    @Test
    fun `delete non-existent user throws NotFoundException`() {
        assertThrows<NotFoundException> { userService.deleteUser(UUID.randomUUID()) }
    }

    @Test
    fun `findOrCreateByGithub creates new user`() {
        val resp = userService.findOrCreateByGithub("gh-123", "new@example.com", "New User")
        assertEquals("gh-123", resp.githubId)
        assertEquals("new@example.com", resp.email)
        assertEquals("New User", resp.name)
    }

    @Test
    fun `findOrCreateByGithub returns existing user by githubId`() {
        userService.createUser(CreateUserRequest("existing@example.com", "Existing", githubId = "gh-existing"))
        val resp = userService.findOrCreateByGithub("gh-existing", "existing@example.com", "Existing")
        assertEquals("gh-existing", resp.githubId)
        // Should not create a duplicate
        val all = userService.listUsers().filter { it.githubId == "gh-existing" }
        assertEquals(1, all.size)
    }

    @Test
    fun `findOrCreateByGithub links github id to existing email user`() {
        userService.createUser(CreateUserRequest("linkme@example.com", "LinkMe"))
        val resp = userService.findOrCreateByGithub("gh-new-link", "linkme@example.com", "LinkMe")
        assertEquals("gh-new-link", resp.githubId)
        assertEquals("linkme@example.com", resp.email)
    }
}
