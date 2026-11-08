package com.factstore.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * #156 FR-1.2 — with a GitHub OAuth app configured, `/login`'s "Continue with GitHub" starts a
 * real authorization request. No call reaches GitHub: only the redirect is checked.
 */
@SpringBootTest(
    properties = [
        "spring.security.oauth2.client.registration.github.client-id=test-client-id",
        "spring.security.oauth2.client.registration.github.client-secret=test-client-secret"
    ]
)
@AutoConfigureMockMvc
class GitHubOAuthWiringTest {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `continue with GitHub redirects to GitHub's authorization endpoint`() {
        mockMvc.get("/oauth2/authorization/github").andExpect {
            status { is3xxRedirection() }
            header { string("Location", org.hamcrest.Matchers.startsWith("https://github.com/login/oauth/authorize?")) }
            header { string("Location", org.hamcrest.Matchers.containsString("client_id=test-client-id")) }
        }
    }

    @Test
    fun `a failed GitHub callback returns to the login page with a reason and no provider detail`() {
        mockMvc.get("/login/oauth2/code/github?error=access_denied&error_description=user+said+no&state=x").andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/login?reason=failed")
        }
    }
}
