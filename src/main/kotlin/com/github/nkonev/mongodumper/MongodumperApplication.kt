package com.github.nkonev.mongodumper

import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.MongoClientURI
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.http.ContentDisposition
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import javax.servlet.http.HttpServletRequest
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
data class AppProperties(val mongodump: String, val mongorestore: String, val mongoServerSelectionTimeout: Duration)

@ConstructorBinding
@ConfigurationProperties("build")
data class BuildProperties(val commitHash: String, val version :String)

data class CheckRequest (val connectionUrl: String)

data class CheckResponse (val ok: Boolean, val message: String)

@Configuration
class MongoConfig {
	@Bean
	fun mongoVlientOptions(properties: AppProperties) : MongoClientOptions {
		return MongoClientOptions.builder().serverSelectionTimeout(properties.mongoServerSelectionTimeout.toMillis().toInt()).build();
	}
}

@RestController
class DatabasesController {

	@Autowired
	private lateinit var repository: DbConnectionRepository

	@Autowired
	private lateinit var appProperties: AppProperties

	@Autowired
	private lateinit var mongoProperties : MongoProperties

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

		val contentDisposition = ContentDisposition.builder("attachment").filename(dbDto.name+".gz", StandardCharsets.UTF_8).build()

		resp.setHeader("Content-Type", "application/octet-stream")
		resp.setHeader("Content-Disposition", contentDisposition.toString())
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
		process.waitFor()
		throwExceptionIfProcessBadlyExited(process)
	}

	@PostMapping("/restore")
	fun selfRestore(@RequestPart("file") multipartFile: MultipartFile, req: HttpServletRequest, resp: HttpServletResponse) {
		val pb :ProcessBuilder = ProcessBuilder(appProperties.mongorestore, """--uri=${mongoProperties.uri}""", "--drop", "--gzip", "--archive")
		val process :Process = pb.start()

		var multipartInputStream = multipartFile.inputStream
		multipartInputStream.use {
			try {
				multipartInputStream.copyTo(process.outputStream)
			} catch (e: RuntimeException){
				process.destroyForcibly()
				throw e;
			} finally {
				process.outputStream.flush()
				process.outputStream.close()
			}
		}
		process.waitFor()
		throwExceptionIfProcessBadlyExited(process)
		val header = req.getHeader("Referer");
		if (header != null) {
			resp.sendRedirect(URI.create(header).path)
		} else {
			resp.sendRedirect("/index.html")
		}
	}
	
	fun throwExceptionIfProcessBadlyExited(process :Process) {
		if (process.exitValue() != 0) {
			throw RuntimeException("Completed with error: " + inputStreamToString(process.errorStream))
		}
	}

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

fun inputStreamToString(inputStream: InputStream) : String {
	val reader = BufferedReader(inputStream.reader())
	val content: String
	try {
		content = reader.readText()
	} finally {
		reader.close()
	}
	return content
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
	private lateinit var operations: MongoOperations

	override fun run(vararg args: String?) {
		logger.info("Ensuring unique index")
		val idx :Index = Index(DbConnectionDto::name.name, Sort.Direction.ASC).unique()
		operations.indexOps(DbConnectionDto::class.java).ensureIndex(idx)

		logger.info("Git commit hash: {}, version {}", buildProperties.commitHash, buildProperties.version)
	}
}

fun main(args: Array<String>) {
	runApplication<MongodumperApplication>(*args)
}
