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
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.util.*
import javax.annotation.PostConstruct
import javax.servlet.http.HttpServletResponse

@EnableConfigurationProperties(AppProperties::class, BuildProperties::class)
@SpringBootApplication
@PropertySource("classpath:config/git.properties")
class MongodumperApplication

data class DbConnectionDto(@Id val id: String?, val name: String, val connectionUrl: String)

@Repository
interface DbConnectionRepository : MongoRepository<DbConnectionDto, String>

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

	@GetMapping("/dump/{id}")
	fun dump(@PathVariable("id") id: String, resp: HttpServletResponse) {
		val findById = repository.findById(id)
		if (findById.isEmpty) {
			resp.sendError(404, """connection with id '${id}' not found""")
		}
		val dbDto :DbConnectionDto = findById.get()

		val filename = URLEncoder.encode(dbDto.name, "UTF-8")
		resp.status = 200
		resp.setHeader("Content-Type", "application/octet-stream")
		resp.setHeader("Content-Disposition", """attachment; filename*=utf-8''${filename}.gz""")
		val pb :ProcessBuilder = ProcessBuilder(appProperties.mongodump, """--uri=${dbDto.connectionUrl}""", "--gzip", "--archive")
		val process :Process = pb.start()
		val inputStream = process.inputStream
		inputStream.use {
			try {
				inputStream.copyTo(resp.outputStream)
			} catch (e: RuntimeException){
				process.destroyForcibly()
				throw e;
			}
		}
	}

	data class CheckResponse (val ok: Boolean, val message: String)

	@GetMapping("/check/{id}")
	fun check(@PathVariable("id") id: String) : CheckResponse {
		val findById = repository.findById(id)
		if (findById.isEmpty) {
			return CheckResponse(false, "Db record not found")
		}
		val dbConnectionDto = findById.get()

		try {
			val u: MongoClientURI = MongoClientURI(dbConnectionDto.connectionUrl)
			val c: MongoClient = MongoClient(u)
			c.use {
				val session = c.startSession()
				session.use {
					return CheckResponse(true, "Ok")
				}
			}
		} catch (e: Throwable) {
			logger.info("Error during check {}", dbConnectionDto.id, e)
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

	override fun run(vararg args: String?) {
		logger.info("Git commit hash: {}, version {}", buildProperties.commitHash, buildProperties.version)
	}
}

fun main(args: Array<String>) {
	runApplication<MongodumperApplication>(*args)
}
