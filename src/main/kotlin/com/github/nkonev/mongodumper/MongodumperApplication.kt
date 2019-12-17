package com.github.nkonev.mongodumper

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.*
import java.util.*
import kotlin.collections.ArrayList

@SpringBootApplication
class MongodumperApplication

data class DbConnectionDto(@Id val id: String?, val name: String, val connectionUrl: String)

@Repository
interface DbConnectionRepository : MongoRepository<DbConnectionDto, String>


// https://docs.mongodb.com/manual/reference/program/mongodump/
// mongodump --archive

@RestController
class DatabasesController {

	@Autowired
	private lateinit var repository: DbConnectionRepository

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
}

fun main(args: Array<String>) {
	runApplication<MongodumperApplication>(*args)
}

