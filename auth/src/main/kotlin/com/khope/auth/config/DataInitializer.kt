package com.khope.auth.config

import com.khope.auth.domain.Account
import com.khope.auth.domain.AccountRepository
import com.khope.auth.domain.Role
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class DataInitializer(
    private val accountRepository: AccountRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!accountRepository.existsByEmail("khope@test.com")) {
            accountRepository.save(
                Account(
                    email = "khope@test.com",
                    password = "1234",
                    role = Role.USER,
                )
            )
            log.info("Default user created: khope@test.com / 1234")
        }
    }
}
