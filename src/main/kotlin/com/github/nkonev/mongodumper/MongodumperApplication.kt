package com.github.nkonev.mongodumper

import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.PropertySource
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.util.*
import javax.servlet.http.HttpServletResponse

@EnableConfigurationProperties(AppProperties::class, BuildProperties::class)
@SpringBootApplication
@PropertySource("classpath:config/git.properties")
class MongodumperApplication

data class DbConnectionDto(@Id val id: String?, val name: String, val connectionUrl: String)

@Repository
interface DbConnectionRepository : MongoRepository<DbConnectionDto, String> {
	fun findByName(name: String): Optional<DbConnectionDto>
}

@ConstructorBinding
@ConfigurationProperties("mongodumper")
data class AppProperties(val mongodump: String)

@ConstructorBinding
@ConfigurationProperties("build")
data class BuildProperties(val commitHash: String, val version :String)

@RestController
class DatabasesController {

	@Autowired
	private lateinit var repository: DbConnectionRepository

	@Autowired
	private lateinit var appProperties: AppProperties

	private val logger = LoggerFactory.getLogger(javaClass)

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

	@GetMapping("/dump/{name}.gz")
	fun dump(@PathVariable("name") name: String, resp: HttpServletResponse) {
		val findById = repository.findByName(name)
		if (findById.isEmpty) {
			resp.sendError(404, """connection with name '${name}' not found""")
		}
		val dbDto :DbConnectionDto = findById.get()

		val filename = URLEncoder.encode(dbDto.name, "UTF-8")
		resp.setHeader("Content-Type", "application/octet-stream")
		val pb :ProcessBuilder = ProcessBuilder(appProperties.mongodump, """--uri=${dbDto.connectionUrl}""", "--gzip", "--archive")
		try {
			val process: Process = pb.start()
			val inputStream = process.inputStream
			inputStream.use {
				try {
					inputStream.copyTo(resp.outputStream)
				} catch (e: RuntimeException) {
					process.destroyForcibly()
					throw e;
				}
			}
		} catch (e: Throwable) {
			resp.status = 500
		}
	}

	data class CheckRequest (val connectionUrl: String)

	data class CheckResponse (val ok: Boolean, val message: String)

	@PostMapping("/check")
	fun check(@RequestBody checkRequest: CheckRequest) : CheckResponse {
		try {
			val u: MongoClientURI = MongoClientURI(checkRequest.connectionUrl)
			val c: MongoClient = MongoClient(u)
			c.use {
				val dbs = c.listDatabaseNames()
				dbs.forEach({ s: String? -> logger.info("For {} found db {}", checkRequest.connectionUrl, s) })
				return CheckResponse(true, "Ok")
			}
		} catch (e: Throwable) {
			logger.info("Error during check {}", checkRequest.connectionUrl, e)
			return CheckResponse(false, e.message.toString())
		}
	}
}

@RestController
class BuildController {

	@Autowired
	private lateinit var buildProperties: BuildProperties

	@GetMapping("/version")
	fun version(): BuildProperties {
		return buildProperties;
	}
}

@Component
class Commandline : CommandLineRunner {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Autowired
	private lateinit var buildProperties: BuildProperties

	@Autowired
	private lateinit var mongoTemplate: MongoTemplate

	override fun run(vararg args: String?) {
		logger.info("Git commit hash: {}, version {}", buildProperties.commitHash, buildProperties.version)

		mongoTemplate.indexOps(DbConnectionDto::class.java).ensureIndex(Index().unique().on(DbConnectionDto::name.name, Sort.Direction.ASC))
	}
}

fun main(args: Array<String>) {
	runApplication<MongodumperApplication>(*args)
}
