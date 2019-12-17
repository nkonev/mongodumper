package com.github.nkonev.mongodumper

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.servlet.http.HttpServletResponse
import kotlin.collections.ArrayList

@EnableConfigurationProperties(AppProperties::class)
@SpringBootApplication
class MongodumperApplication

data class DbConnectionDto(@Id val id: String?, val name: String, val connectionUrl: String)

@Repository
interface DbConnectionRepository : MongoRepository<DbConnectionDto, String>

@ConstructorBinding
@ConfigurationProperties("mongodumper")
data class AppProperties (var mongodump: String)

@RestController
class DatabasesController {

	@Autowired
	private lateinit var repository: DbConnectionRepository

	@Autowired
	private lateinit var properties: AppProperties

	@PostMapping("/db")
	fun create(@RequestBody userDto: DbConnectionDto): DbConnectionDto {
		return repository.save(userDto)
	}

	@PutMapping("/db")
	fun update(@RequestBody userDto: DbConnectionDto): DbConnectionDto {
		return repository.save(userDto)
	}

	@DeleteMapping("/db/{id}")
	fun delete(@PathVariable("id") id: String) {
		repository.deleteById(id)
	}

	@GetMapping("/db")
	fun read(): List<DbConnectionDto?> {
		return repository.findAll()
	}

	@GetMapping("/db/{id}")
	fun readById(@PathVariable("id") id: String): Optional<DbConnectionDto> {
		return repository.findById(id)
	}

	@GetMapping("/dump/{id}")
	fun dump(@PathVariable("id") id: String, resp: HttpServletResponse) {
		val findById = repository.findById(id)
		if (findById.isEmpty) {
			resp.sendError(404, """connection with id '${id}' not found""")
		}
		val dbDto :DbConnectionDto = findById.get()

		val filename = dbDto.name
		resp.status = 200
		resp.setHeader("Content-Type", "application/octet-stream")
		resp.setHeader("Content-Disposition", """attachment; filename="${filename}"""")
		val pb :ProcessBuilder = ProcessBuilder(properties.mongodump, """--uri=${dbDto.connectionUrl}""", "--archive")
		val process :Process = pb.start()
		val inputStream = process.inputStream
		inputStream.use {
			inputStream.copyTo(resp.outputStream)
		}
	}

}

fun main(args: Array<String>) {
	runApplication<MongodumperApplication>(*args)
}

