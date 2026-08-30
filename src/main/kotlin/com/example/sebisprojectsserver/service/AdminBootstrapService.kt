package com.example.sebisprojectsserver.service

import com.example.sebisprojectsserver.entities.AppRole
import com.example.sebisprojectsserver.entities.AppUser
import com.example.sebisprojectsserver.repositories.AppUserRepository
import com.example.sebisprojectsserver.security.AppSecurityProperties
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AdminBootstrapService(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val securityProperties: AppSecurityProperties,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val configuredUsername = securityProperties.admin.username.trim()
        val adminUser = appUserRepository.findByUsername(configuredUsername)
            ?: appUserRepository.findFirstByRoleOrderByIdAsc(AppRole.ADMIN)
            ?: AppUser()

        adminUser.username = configuredUsername
        adminUser.passwordHash = passwordEncoder.encode(securityProperties.admin.password).orEmpty()
        adminUser.role = AppRole.ADMIN

        appUserRepository.save(adminUser)
    }
}
