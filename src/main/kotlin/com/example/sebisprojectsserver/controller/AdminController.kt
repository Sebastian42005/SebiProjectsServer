package com.example.sebisprojectsserver.controller

import com.example.sebisprojectsserver.admin.AdminReferenceOption
import com.example.sebisprojectsserver.admin.AdminSaveRequest
import com.example.sebisprojectsserver.admin.AdminTableDefinition
import com.example.sebisprojectsserver.admin.AdminTableService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val adminTableService: AdminTableService,
) {

    @GetMapping("/tables")
    fun getTables(): List<AdminTableDefinition> {
        return adminTableService.getTables()
    }

    @GetMapping("/tables/{table}/rows")
    fun getRows(@PathVariable table: String): List<Map<String, Any?>> {
        return adminTableService.getRows(table)
    }

    @GetMapping("/tables/{table}/options/{field}")
    fun getReferenceOptions(
        @PathVariable table: String,
        @PathVariable field: String,
    ): List<AdminReferenceOption> {
        return adminTableService.getReferenceOptions(table, field)
    }

    @PostMapping("/tables/{table}/rows")
    fun createRow(
        @PathVariable table: String,
        @RequestBody request: AdminSaveRequest,
    ): Map<String, Any?> {
        return adminTableService.createRow(table, request.values)
    }

    @PutMapping("/tables/{table}/rows/{id}")
    fun updateRow(
        @PathVariable table: String,
        @PathVariable id: Long,
        @RequestBody request: AdminSaveRequest,
    ): Map<String, Any?> {
        return adminTableService.updateRow(table, id, request.values)
    }
}
